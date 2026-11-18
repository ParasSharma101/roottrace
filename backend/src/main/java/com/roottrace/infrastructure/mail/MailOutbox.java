package com.roottrace.infrastructure.mail;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

@Service
public class MailOutbox {
    private final JdbcTemplate jdbc;
    private final MailCipher cipher;
    public MailOutbox(JdbcTemplate jdbc,MailCipher cipher) { this.jdbc=jdbc; this.cipher=cipher; }
    @Transactional(propagation=Propagation.MANDATORY)
    public void enqueue(String purpose,String recipient,UUID verification,UUID invitation,String body) {
        UUID id=UUID.randomUUID(); var encrypted=cipher.encrypt(id,purpose,recipient,body);
        jdbc.update("insert into outbound_emails(id,purpose,recipient,verification_id,invitation_id,encrypted_body,nonce) values (?,?,?,?,?,?,?)",
                id,purpose,recipient,verification,invitation,encrypted.body(),encrypted.nonce());
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public Optional<Message> claim() {
        jdbc.update("""
            update outbound_emails m set status='CANCELLED',encrypted_body=null,nonce=null,claim_token=null,lease_until=null,completed_at=current_timestamp
            where m.status in ('PENDING','RUNNING','FAILED') and (
              (m.verification_id is not null and exists (select 1 from email_verifications v where v.id=m.verification_id and
                (v.expires_at<=current_timestamp or v.revoked_at is not null or v.consumed_at is not null))) or
              (m.invitation_id is not null and exists (select 1 from workspace_invites i where i.id=m.invitation_id and
                (i.status<>'PENDING' or i.expires_at<=current_timestamp))))
            """);
        jdbc.update("update outbound_emails set status='FAILED',claim_token=null,lease_until=null,last_error_code='LeaseExpired' where status='RUNNING' and lease_until<=current_timestamp and attempts=5");
        UUID token=UUID.randomUUID();
        var rows=jdbc.query("""
            with candidate as (select id from outbound_emails where attempts<5 and
              ((status='PENDING' and available_at<=current_timestamp) or (status='RUNNING' and lease_until<=current_timestamp))
              order by available_at,created_at,id for update skip locked limit 1)
            update outbound_emails m set status='RUNNING',attempts=attempts+1,claim_token=?,lease_until=current_timestamp+interval '5 minutes'
            from candidate c where m.id=c.id returning m.id,m.purpose,m.recipient,m.encrypted_body,m.nonce
            """,(rs,row) -> new Message(rs.getObject("id",UUID.class),rs.getString("purpose"),rs.getString("recipient"),rs.getBytes("encrypted_body"),rs.getBytes("nonce"),token),token);
        return rows.stream().findFirst();
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void sent(Message message) {
        jdbc.update("update outbound_emails set status='SENT',encrypted_body=null,nonce=null,claim_token=null,lease_until=null,last_error_code=null,completed_at=current_timestamp where id=? and status='RUNNING' and claim_token=? and lease_until>current_timestamp",message.id(),message.token());
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void failed(Message message,Exception error) {
        jdbc.update("""
            update outbound_emails set status=case when attempts=5 then 'FAILED' else 'PENDING' end,
              available_at=current_timestamp+(power(2,attempts)::integer*5 * interval '1 second'),
              claim_token=null,lease_until=null,last_error_code=? where id=? and status='RUNNING' and claim_token=? and lease_until>current_timestamp
            """,error.getClass().getSimpleName(),message.id(),message.token());
    }
    public record Message(UUID id,String purpose,String recipient,byte[] body,byte[] nonce,UUID token) {
        @Override public String toString() { return "OutboundEmail["+id+",redacted]"; }
    }
}
