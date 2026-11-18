package com.roottrace.workspace.service;

import com.roottrace.security.WorkspaceIdentity;
import com.roottrace.security.service.WorkspaceOperatorAccess;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
public class QueueMonitoringService {
    private final JdbcTemplate jdbc;
    private final WorkspaceOperatorAccess access;
    private final int overdueSeconds;
    public QueueMonitoringService(JdbcTemplate jdbc,WorkspaceOperatorAccess access,
            @Value("${roottrace.monitoring.queue.pending-overdue-seconds:300}") int overdueSeconds) {
        if (overdueSeconds<=0) throw new IllegalArgumentException("roottrace.monitoring.queue.pending-overdue-seconds must be positive");
        this.jdbc=jdbc; this.access=access; this.overdueSeconds=overdueSeconds;
    }
    @Transactional(readOnly=true)
    public Summary summary(WorkspaceIdentity identity) {
        UUID workspace=access.require(identity).getWorkspace().getId();
        var queues=jdbc.query("""
            select k.kind,current_timestamp as observed_at,
              count(t.id) filter (where t.status='PENDING') as pending,
              count(t.id) filter (where t.status='RUNNING') as running,
              count(t.id) filter (where t.status='SUCCEEDED') as succeeded,
              count(t.id) filter (where t.status='FAILED') as failed,
              count(t.id) filter (where t.status='PENDING' and t.available_at<=current_timestamp) as ready,
              count(t.id) filter (where t.status='PENDING' and t.available_at<=current_timestamp-(? * interval '1 second')) as overdue,
              count(t.id) filter (where t.status='RUNNING' and t.lease_until<=current_timestamp) as expired,
              floor(coalesce(max(extract(epoch from current_timestamp-t.available_at)) filter
                (where t.status='PENDING' and t.available_at<=current_timestamp),0))::bigint as oldest_ready_seconds
            from (values ('PUBLISH'),('INVESTIGATE')) as k(kind)
              left join durable_tasks t on t.kind=k.kind and t.workspace_id=?
            group by k.kind order by k.kind
            """,(rs,row) -> new Queue(rs.getString("kind"),rs.getTimestamp("observed_at").toInstant(),
                rs.getLong("pending"),rs.getLong("running"),rs.getLong("succeeded"),rs.getLong("failed"),
                rs.getLong("ready"),rs.getLong("overdue"),rs.getLong("expired"),rs.getLong("oldest_ready_seconds")),overdueSeconds,workspace);
        List<Signal> signals=new ArrayList<>();
        for (Queue queue:queues) {
            if (queue.failed()>0) signals.add(new Signal(queue.kind(),"FAILED_TASKS",queue.failed()));
            if (queue.overduePending()>0) signals.add(new Signal(queue.kind(),"OVERDUE_PENDING",queue.overduePending()));
            if (queue.expiredLeases()>0) signals.add(new Signal(queue.kind(),"EXPIRED_LEASES",queue.expiredLeases()));
        }
        return new Summary(queues.getFirst().observedAt(),overdueSeconds,List.copyOf(queues),List.copyOf(signals));
    }
    public record Queue(String kind,Instant observedAt,long pending,long running,long succeeded,long failed,long ready,
                        long overduePending,long expiredLeases,long oldestReadyAgeSeconds) {}
    public record Signal(String kind,String code,long count) {}
    public record Summary(Instant observedAt,int pendingOverdueSeconds,List<Queue> queues,List<Signal> signals) {}
}
