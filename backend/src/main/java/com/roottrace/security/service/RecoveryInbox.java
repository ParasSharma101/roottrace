package com.roottrace.security.service;

import com.roottrace.infrastructure.mail.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

/** Encrypted intake keeps public request timing independent of account existence. */
@Service
public class RecoveryInbox {
    private final JdbcTemplate jdbc;
    private final MailCipher cipher;
    private final MailSettings mail;
    private final PasswordRecoveryService recovery;
    public RecoveryInbox(JdbcTemplate jdbc,MailCipher cipher,MailSettings mail,PasswordRecoveryService recovery) {
        this.jdbc=jdbc; this.cipher=cipher; this.mail=mail; this.recovery=recovery;
    }
    @Transactional
    public void enqueue(String email) {
        if(!mail.enabled) return;
        UUID id=UUID.randomUUID(); var encrypted=cipher.encrypt(id,"RECOVERY_REQUEST","request",email.strip().toLowerCase(Locale.ROOT));
        jdbc.update("insert into password_recovery_requests(id,encrypted_body,nonce) values (?,?,?)",id,encrypted.body(),encrypted.nonce());
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public Optional<Claim> claim() {
        // Retain only one day of anonymous peer counters and seven days of intake metadata.
        jdbc.update("delete from recovery_request_limits where (peer_hash,window_start) in (select peer_hash,window_start from recovery_request_limits where window_start<floor(extract(epoch from current_timestamp)/900)::bigint-96 limit 100)");
        jdbc.update("delete from password_recovery_requests where id in (select id from password_recovery_requests where status in ('DONE','FAILED') and created_at<current_timestamp-interval '7 days' limit 100)");
        jdbc.update("update password_recovery_requests set status='FAILED',encrypted_body=null,nonce=null,claim_token=null,lease_until=null where status in ('PENDING','RUNNING') and (created_at<current_timestamp-interval '30 minutes' or (attempts=5 and lease_until<=current_timestamp))");
        jdbc.update("delete from revoked_sessions where session_id in(select session_id from revoked_sessions where expires_at<=current_timestamp limit 100)");
        UUID token=UUID.randomUUID();
        return jdbc.query("""
            with candidate as(select id from password_recovery_requests where attempts<5 and
              ((status='PENDING' and available_at<=current_timestamp) or (status='RUNNING' and lease_until<=current_timestamp))
              order by available_at,id for update skip locked limit 1)
            update password_recovery_requests r set status='RUNNING',attempts=attempts+1,claim_token=?,lease_until=current_timestamp+interval '5 minutes'
            from candidate c where r.id=c.id returning r.id
            """,(rs,n) -> new Claim(rs.getObject("id",UUID.class),token),token).stream().findFirst();
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void process(Claim claim) {
        var rows=jdbc.queryForList("select encrypted_body,nonce from password_recovery_requests where id=? and status='RUNNING' and claim_token=? and lease_until>clock_timestamp() for update",claim.id(),claim.token());
        if(rows.isEmpty()) return;
        var row=rows.getFirst();
        recovery.request(cipher.decrypt(claim.id(),"RECOVERY_REQUEST","request",(byte[])row.get("encrypted_body"),(byte[])row.get("nonce")));
        jdbc.update("update password_recovery_requests set status='DONE',encrypted_body=null,nonce=null,claim_token=null,lease_until=null where id=?",claim.id());
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void failed(Claim claim) {
        jdbc.update("""
            update password_recovery_requests set status=case when attempts=5 then 'FAILED' else 'PENDING' end,
              encrypted_body=case when attempts=5 then null else encrypted_body end,nonce=case when attempts=5 then null else nonce end,
              available_at=current_timestamp+(power(2,attempts)::integer*5*interval '1 second'),claim_token=null,lease_until=null
            where id=? and status='RUNNING' and claim_token=? and lease_until>current_timestamp
            """,claim.id(),claim.token());
    }
    public record Claim(UUID id,UUID token) { @Override public String toString() { return "RecoveryRequest[redacted]"; } }
}
