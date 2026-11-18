package com.roottrace.security.service;

import com.roottrace.security.WorkspaceIdentity;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.sql.Timestamp;
import java.util.UUID;

@Service
public class SessionRevocationService {
    private final JdbcTemplate jdbc;
    public SessionRevocationService(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public boolean isRevoked(UUID session) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from revoked_sessions where session_id=?)",Boolean.class,session));
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public void lockActive(WorkspaceIdentity identity) {
        var rows=jdbc.queryForList("select auth_epoch from users where id=? for update",identity.userId());
        if(identity.sessionId()==null || identity.sessionExpiresAt()==null || rows.isEmpty()
                || ((Number)rows.getFirst().get("auth_epoch")).longValue()!=identity.authEpoch()
                || isRevoked(identity.sessionId()) || !identity.sessionExpiresAt().isAfter(java.time.Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Session no longer active");
        }
    }

    @Transactional
    public void logout(WorkspaceIdentity identity) {
        lockActive(identity);
        jdbc.update("insert into revoked_sessions(session_id,user_id,expires_at) values (?,?,?) on conflict do nothing",
                identity.sessionId(),identity.userId(),Timestamp.from(identity.sessionExpiresAt()));
    }

    @Transactional
    public void logoutAll(WorkspaceIdentity identity) {
        lockActive(identity);
        jdbc.update("update users set auth_epoch=auth_epoch+1,updated_at=current_timestamp where id=?",identity.userId());
    }
}
