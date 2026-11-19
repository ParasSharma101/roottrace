package com.roottrace.security.service;

import com.roottrace.infrastructure.mail.*;
import com.roottrace.ingestion.service.PayloadDigest;
import com.roottrace.security.WorkspaceIdentity;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Service
public class EmailVerificationService {
    private final JdbcTemplate jdbc;
    private final MailSettings settings;
    private final MailOutbox outbox;
    private final SecureRandom random=new SecureRandom();
    public EmailVerificationService(JdbcTemplate jdbc,MailSettings settings,MailOutbox outbox) { this.jdbc=jdbc; this.settings=settings; this.outbox=outbox; }

    @Transactional(propagation=Propagation.MANDATORY)
    public void afterSignup(UUID user) { if(settings.enabled) issue(user); }

    @Transactional
    public void request(WorkspaceIdentity identity) { settings.requireEnabled(); issue(identity.userId()); }

    private void issue(UUID userId) {
        var users=jdbc.queryForList("select id,email,verified_email,email_verified_at from users where id=? for no key update",userId);
        if(users.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Account unavailable");
        var user=users.getFirst(); String email=user.get("email").toString().toLowerCase(Locale.ROOT).strip();
        if(user.get("email_verified_at")!=null && email.equals(user.get("verified_email"))) return;
        if(Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from email_verifications where user_id=? and created_at>current_timestamp-interval '60 seconds')",Boolean.class,userId))) return;
        jdbc.update("update email_verifications set revoked_at=current_timestamp where user_id=? and consumed_at is null and revoked_at is null",userId);
        jdbc.update("""
            update outbound_emails set status='CANCELLED',encrypted_body=null,nonce=null,claim_token=null,lease_until=null,completed_at=current_timestamp
            where verification_id in (select id from email_verifications where user_id=?) and status in ('PENDING','RUNNING','FAILED')
            """,userId);
        byte[] bytes=new byte[32]; random.nextBytes(bytes);
        String token="rt_ver_"+Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        UUID id=UUID.randomUUID();
        jdbc.update("insert into email_verifications(id,user_id,email,token_hash,expires_at) values (?,?,?,?,current_timestamp+interval '24 hours')",id,userId,email,PayloadDigest.hash(token));
        outbox.enqueue("VERIFY_EMAIL",email,id,null,"Verify your RootTrace email address:\n\n"+settings.link("/verify-email#token="+token)+"\n\nThis link expires in 24 hours. If you did not request it, ignore this email.");
    }

    @Transactional
    public void verify(WorkspaceIdentity identity,String token) {
        if(token==null || token.length()>256 || !token.startsWith("rt_ver_")) throw invalid();
        // Read identity first, then lock the user before the challenge, matching issue's lock order.
        var found=jdbc.queryForList("select user_id from email_verifications where token_hash=? and user_id=?",PayloadDigest.hash(token),identity.userId());
        if(found.isEmpty()) throw invalid();
        UUID userId=(UUID)found.getFirst().get("user_id");
        var users=jdbc.queryForList("select email,verified_email,email_verified_at from users where id=? for no key update",userId);
        var rows=jdbc.queryForList("select * from email_verifications where token_hash=? for update",PayloadDigest.hash(token));
        var challenge=rows.getFirst();
        if(users.isEmpty() || !users.getFirst().get("email").toString().toLowerCase(Locale.ROOT).strip().equals(challenge.get("email"))
                || challenge.get("revoked_at")!=null || !Boolean.TRUE.equals(jdbc.queryForObject("select expires_at>clock_timestamp() from email_verifications where id=?",Boolean.class,challenge.get("id")))) throw invalid();
        if(challenge.get("consumed_at")!=null) {
            if(users.getFirst().get("email_verified_at")==null || !challenge.get("email").equals(users.getFirst().get("verified_email"))) throw invalid();
            return; // Repeated confirmation never issues a session or membership.
        }
        jdbc.update("update users set verified_email=?,email_verified_at=current_timestamp,updated_at=current_timestamp where id=?",challenge.get("email"),userId);
        jdbc.update("update email_verifications set consumed_at=current_timestamp where id=?",challenge.get("id"));
        jdbc.update("update outbound_emails set status='CANCELLED',encrypted_body=null,nonce=null,claim_token=null,lease_until=null,completed_at=current_timestamp where verification_id=? and status in ('PENDING','RUNNING','FAILED')",challenge.get("id"));
    }

    @Transactional(readOnly=true)
    public Status status(WorkspaceIdentity identity) {
        var rows=jdbc.queryForList("select email,verified_email,email_verified_at from users where id=?",identity.userId());
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Account unavailable");
        var user=rows.getFirst(); String email=user.get("email").toString().toLowerCase(Locale.ROOT).strip();
        boolean verified=user.get("email_verified_at")!=null && email.equals(user.get("verified_email"));
        var messages=jdbc.queryForList("select m.status,v.created_at+interval '60 seconds' as resend_at from email_verifications v left join outbound_emails m on m.verification_id=v.id where v.user_id=? order by v.created_at desc,v.id desc limit 1",identity.userId());
        String delivery=messages.isEmpty()?"NOT_REQUESTED":Objects.toString(messages.getFirst().get("status"),"NOT_REQUESTED");
        Instant next=messages.isEmpty()?null:((java.sql.Timestamp)messages.getFirst().get("resend_at")).toInstant();
        return new Status(email,verified,settings.enabled,delivery,next);
    }
    private ResponseStatusException invalid() { return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Verification link is invalid or expired"); }
    public record Status(String email,boolean emailVerified,boolean deliveryConfigured,String deliveryStatus,Instant nextResendAt) {}
}
