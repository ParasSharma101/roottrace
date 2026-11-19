package com.roottrace.workspace.service;

import com.roottrace.security.WorkspaceIdentity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import java.time.Instant;

/** Read-only onboarding evidence; a key or HTTP acknowledgement alone is not proof of processing. */
@Service
public class WorkspaceSetupService {
    private final JdbcTemplate jdbc;
    private final MembershipAccess access;
    public WorkspaceSetupService(JdbcTemplate jdbc,MembershipAccess access) { this.jdbc=jdbc; this.access=access; }
    @Transactional(readOnly=true)
    public SetupStatus status(WorkspaceIdentity identity) {
        var workspace=identity.requireWorkspace();
        access.memberForUser(workspace,identity.userId());
        var result=jdbc.queryForObject("""
            select current_timestamp as observed_at,
              (select count(*) from workspace_ingestion_keys where workspace_id=? and revoked_at is null) as active_keys,
              (select count(*) from ingestion_submissions where workspace_id=?) as accepted_requests,
              (select max(received_at) from ingestion_submissions where workspace_id=?) as last_request,
              (select count(*) from ingestion_receipts where workspace_id=?) as processed_events,
              (select max(processed_at) from ingestion_receipts where workspace_id=?) as last_processed
            """,(rs,n) -> new SetupStatus(rs.getTimestamp("observed_at").toInstant(),rs.getLong("active_keys"),rs.getLong("accepted_requests"),
                rs.getTimestamp("last_request")==null?null:rs.getTimestamp("last_request").toInstant(),rs.getLong("processed_events"),
                rs.getTimestamp("last_processed")==null?null:rs.getTimestamp("last_processed").toInstant()),workspace,workspace,workspace,workspace,workspace);
        return result;
    }
    public record SetupStatus(Instant observedAt,long activeKeyCount,long acceptedRequestCount,Instant lastRequestAt,long processedEventCount,Instant lastProcessedAt) {}
}
