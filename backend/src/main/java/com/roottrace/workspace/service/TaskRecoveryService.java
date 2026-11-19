package com.roottrace.workspace.service;

import com.roottrace.domain.exception.ResourceNotFoundException;
import com.roottrace.domain.model.WorkspaceMember;
import com.roottrace.security.service.WorkspaceOperatorAccess;
import com.roottrace.security.WorkspaceIdentity;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

@Service
public class TaskRecoveryService {
    private static final String TASK_COLUMNS = "id,kind,status,submission_id,incident_id,attempts,total_attempts,redrive_count,available_at,lease_until,last_error_code,created_at,completed_at";
    private static final RowMapper<TaskView> TASK_MAPPER = (rs, row) -> new TaskView(rs.getObject("id",UUID.class),
            rs.getString("kind"),rs.getString("status"),rs.getObject("submission_id",UUID.class),rs.getObject("incident_id",UUID.class),
            rs.getInt("attempts"),rs.getInt("total_attempts"),rs.getInt("redrive_count"),time(rs,"available_at"),time(rs,"lease_until"),
            rs.getString("last_error_code"),time(rs,"created_at"),time(rs,"completed_at"));
    private static final RowMapper<RedriveView> AUDIT_MAPPER = (rs, row) -> new RedriveView(rs.getObject("id",UUID.class),
            rs.getObject("task_id",UUID.class),rs.getObject("actor_user_id",UUID.class),rs.getString("actor_email"),rs.getString("reason"),
            rs.getInt("cycle_number"),rs.getInt("previous_attempts"),rs.getInt("total_attempts_before"),rs.getString("previous_error_code"),
            time(rs,"previous_completed_at"),time(rs,"created_at"));
    private final JdbcTemplate jdbc;
    private final WorkspaceOperatorAccess access;
    public TaskRecoveryService(JdbcTemplate jdbc, WorkspaceOperatorAccess access) { this.jdbc=jdbc; this.access=access; }

    @Transactional(readOnly = true)
    public Page<TaskView> list(WorkspaceIdentity identity, Status status, Kind kind, UUID before, int limit) {
        UUID workspace = operator(identity).getWorkspace().getId();
        validateLimit(limit);
        List<Object> args = new ArrayList<>(List.of(workspace,status.name()));
        String where = "workspace_id=? and status=?";
        if (kind != null) { where += " and kind=?"; args.add(kind.name()); }
        if (before != null) {
            TaskView cursor = owned(workspace,before,false);
            where += " and (created_at,id) < (?,?)";
            args.add(java.sql.Timestamp.from(cursor.createdAt())); args.add(cursor.id());
        }
        args.add(limit+1);
        var rows=jdbc.query("select "+TASK_COLUMNS+" from durable_tasks where "+where+" order by created_at desc,id desc limit ?",TASK_MAPPER,args.toArray());
        return page(rows,limit,TaskView::id);
    }

    @Transactional(readOnly = true)
    public TaskView get(WorkspaceIdentity identity, UUID id) { return owned(operator(identity).getWorkspace().getId(),id,false); }

    @Transactional(readOnly = true)
    public Page<RedriveView> history(WorkspaceIdentity identity, UUID id, UUID before, int limit) {
        UUID workspace=operator(identity).getWorkspace().getId();
        owned(workspace,id,false);
        validateLimit(limit);
        List<Object> args=new ArrayList<>(List.of(workspace,id));
        String where="workspace_id=? and task_id=?";
        if (before!=null) {
            var cursors=jdbc.query("select * from durable_task_redrives where id=? and workspace_id=? and task_id=?",AUDIT_MAPPER,before,workspace,id);
            if (cursors.isEmpty()) throw new ResourceNotFoundException("Recovery record not found");
            where+=" and (created_at,id) < (?,?)";
            args.add(java.sql.Timestamp.from(cursors.getFirst().createdAt())); args.add(before);
        }
        args.add(limit+1);
        var rows=jdbc.query("select * from durable_task_redrives where "+where+" order by created_at desc,id desc limit ?",AUDIT_MAPPER,args.toArray());
        return page(rows,limit,RedriveView::id);
    }

    @Transactional
    public TaskView redrive(WorkspaceIdentity identity, UUID id, RedriveRequest request) {
        WorkspaceMember member=operator(identity);
        UUID workspace=member.getWorkspace().getId();
        if (request.reason()==null || request.reason().isBlank() || request.reason().length()>500 || request.expectedRedriveCount()==null || request.expectedRedriveCount()<0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A reason and current recovery count are required");
        }
        TaskView previous=owned(workspace,id,true);
        if (!"FAILED".equals(previous.status()) || previous.redriveCount()!=request.expectedRedriveCount()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Task state changed; refresh before retrying");
        }
        jdbc.update("""
            insert into durable_task_redrives(id,task_id,workspace_id,actor_user_id,actor_email,reason,cycle_number,
                previous_attempts,total_attempts_before,previous_error_code,previous_completed_at)
            values (?,?,?,?,?,?,?,?,?,?,?)
            """,UUID.randomUUID(),id,workspace,member.getUser().getId(),member.getUser().getEmail(),request.reason().strip(),previous.redriveCount()+1,
                previous.attempts(),previous.totalAttempts(),previous.lastErrorCode(),previous.completedAt()==null?null:java.sql.Timestamp.from(previous.completedAt()));
        int updated=jdbc.update("""
            update durable_tasks set status='PENDING',attempts=0,redrive_count=redrive_count+1,available_at=current_timestamp,
                completed_at=null,last_error_code=null,claim_token=null,lease_until=null
            where id=? and workspace_id=? and status='FAILED' and redrive_count=?
            """,id,workspace,previous.redriveCount());
        if (updated!=1) throw new IllegalStateException("Task recovery state changed");
        return owned(workspace,id,false);
    }

    private WorkspaceMember operator(WorkspaceIdentity identity) {
        return access.require(identity);
    }
    private TaskView owned(UUID workspace,UUID id,boolean lock) {
        var rows=jdbc.query("select "+TASK_COLUMNS+" from durable_tasks where id=? and workspace_id=?"+(lock?" for update":""),TASK_MAPPER,id,workspace);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Task not found");
        return rows.getFirst();
    }
    private void validateLimit(int limit) { if (limit<1 || limit>100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Limit must be between 1 and 100"); }
    private static Instant time(ResultSet rs,String name) throws SQLException { var value=rs.getTimestamp(name); return value==null?null:value.toInstant(); }
    private <T> Page<T> page(List<T> rows,int limit,java.util.function.Function<T,UUID> id) {
        boolean more=rows.size()>limit;
        List<T> items=List.copyOf(rows.subList(0,Math.min(rows.size(),limit)));
        return new Page<>(items,more?id.apply(items.getLast()):null);
    }
    public enum Status { PENDING,RUNNING,SUCCEEDED,FAILED }
    public enum Kind { PUBLISH,INVESTIGATE }
    public record RedriveRequest(@NotBlank @Size(max=500) String reason,@NotNull @Min(0) Integer expectedRedriveCount) {}
    public record Page<T>(List<T> items,UUID nextCursor) {}
    public record TaskView(UUID id,String kind,String status,UUID submissionId,UUID incidentId,int attempts,int totalAttempts,int redriveCount,
                           Instant availableAt,Instant leaseUntil,String lastErrorCode,Instant createdAt,Instant completedAt) {}
    public record RedriveView(UUID id,UUID taskId,UUID actorUserId,String actorEmail,String reason,int cycleNumber,int previousAttempts,
                              int totalAttemptsBefore,String previousErrorCode,Instant previousCompletedAt,Instant createdAt) {}
}
