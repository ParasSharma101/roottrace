package com.roottrace.infrastructure.jobs;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class DurableTaskStore {
    private final JdbcTemplate jdbc;
    public DurableTaskStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueuePublish(UUID id, UUID workspace, UUID submission, String topic, String key, String payload) {
        jdbc.update("insert into durable_tasks(id,workspace_id,kind,submission_id,topic,partition_key,event_payload) values (?,?,'PUBLISH',?,?,?,?)",
                id, workspace, submission, topic, key, payload);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueInvestigation(UUID workspace, UUID incident) {
        jdbc.update("insert into durable_tasks(id,workspace_id,kind,incident_id) values (?,?,'INVESTIGATE',?) on conflict do nothing",
                UUID.randomUUID(), workspace, incident);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Task> claim(String kind) {
        jdbc.update("""
            update durable_tasks set status='FAILED', completed_at=current_timestamp, claim_token=null,
                lease_until=null, last_error_code='LeaseExpired'
            where kind=? and status='RUNNING' and lease_until <= current_timestamp and attempts=5
            """, kind);
        UUID token = UUID.randomUUID();
        var tasks = jdbc.query("""
            with candidate as (
                select id from durable_tasks where kind=? and attempts<5
                  and ((status='PENDING' and available_at<=current_timestamp)
                    or (status='RUNNING' and lease_until<=current_timestamp))
                order by available_at, created_at, id for update skip locked limit 1
            )
            update durable_tasks t set status='RUNNING', attempts=attempts+1, total_attempts=total_attempts+1, claim_token=?,
                lease_until=current_timestamp + interval '5 minutes'
            from candidate c where t.id=c.id
            returning t.id,t.workspace_id,t.incident_id,t.topic,t.partition_key,t.event_payload,t.attempts
            """, (rs, row) -> new Task(rs.getObject("id", UUID.class), rs.getObject("workspace_id", UUID.class),
                rs.getObject("incident_id", UUID.class), rs.getString("topic"), rs.getString("partition_key"),
                rs.getString("event_payload"), rs.getInt("attempts"), token), kind, token);
        return tasks.stream().findFirst();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completePublish(Task task) { complete(task); }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean lockCurrentClaim(Task task) {
        return !jdbc.queryForList("""
            select id from durable_tasks where id=? and workspace_id=? and status='RUNNING'
                and claim_token=? and lease_until>current_timestamp for update
            """, task.id(), task.workspaceId(), task.token()).isEmpty();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(Task task) {
        jdbc.update("""
            update durable_tasks set status='SUCCEEDED', completed_at=current_timestamp,
                claim_token=null, lease_until=null, last_error_code=null
            where id=? and workspace_id=? and status='RUNNING' and claim_token=? and lease_until>current_timestamp
            """, task.id(), task.workspaceId(), task.token());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Task task, Exception failure) {
        // Persist a class/code, never provider messages, evidence, credentials, or stack traces.
        jdbc.update("""
            update durable_tasks set status=case when attempts=5 then 'FAILED' else 'PENDING' end,
                available_at=current_timestamp + (? * interval '1 second'), claim_token=null, lease_until=null,
                completed_at=case when attempts=5 then current_timestamp else null end, last_error_code=?
            where id=? and workspace_id=? and status='RUNNING' and claim_token=? and lease_until>current_timestamp
            """, Math.min(300, 5 * (1 << task.attempts())), failure.getClass().getSimpleName(), task.id(), task.workspaceId(), task.token());
    }

    @Transactional(readOnly = true)
    public Map<String,Object> investigationStatus(UUID workspace, UUID incident) {
        if (jdbc.queryForList("select id from incidents where id=? and workspace_id=?", incident, workspace).isEmpty()) {
            throw new com.roottrace.domain.exception.ResourceNotFoundException("Incident not found");
        }
        var rows = jdbc.queryForList("select id,status,attempts,available_at,lease_until,last_error_code,completed_at from durable_tasks where workspace_id=? and incident_id=? and kind='INVESTIGATE'", workspace, incident);
        return rows.isEmpty() ? Map.of("status", "NOT_SCHEDULED") : rows.getFirst();
    }

    public record Task(UUID id, UUID workspaceId, UUID incidentId, String topic, String key, String payload, int attempts, UUID token) {}
}
