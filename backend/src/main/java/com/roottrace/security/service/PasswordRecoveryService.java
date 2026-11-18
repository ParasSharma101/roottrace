package com.roottrace.security.service;

import com.roottrace.infrastructure.mail.*;
import com.roottrace.ingestion.service.PayloadDigest;
import com.roottrace.security.PasswordPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.security.SecureRandom;
import java.util.*;

@Service
public class PasswordRecoveryService {
    private final JdbcTemplate jdbc;
    private final MailSettings mail;
    private final MailOutbox outbox;
    private final PasswordEncoder passwords;
    private final int hourlyLimit;
    private final SecureRandom random=new SecureRandom();
    public PasswordRecoveryService(JdbcTemplate jdbc,MailSettings mail,MailOutbox outbox,PasswordEncoder passwords,
            @Value("${roottrace.recovery.requests-per-account-hour:5}") int hourlyLimit) {
        if(hourlyLimit<1) throw new IllegalArgumentException("Recovery account limit must be positive");
        this.jdbc=jdbc; this.mail=mail; this.outbox=outbox; this.passwords=passwords; this.hourlyLimit=hourlyLimit;
    }

    @Transactional
    public void request(String suppliedEmail) {
        if(!mail.enabled) return;
        String email=suppliedEmail.strip().toLowerCase(Locale.ROOT);
        var users=jdbc.queryForList("select id from users where email=? for update",email);
        // This runs only in the private worker, after the public acknowledgement.
        byte[] bytes=new byte[32]; random.nextBytes(bytes);
        String token="rt_rst_"+Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash=PayloadDigest.hash(token);
        if(users.isEmpty()) return;
        UUID user=(UUID)users.getFirst().get("id");
        if(Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from password_resets where user_id=? and created_at>current_timestamp-interval '60 seconds')",Boolean.class,user))
                || jdbc.queryForObject("select count(*) from password_resets where user_id=? and created_at>current_timestamp-interval '1 hour'",Long.class,user)>=hourlyLimit) return;
        UUID reset=UUID.randomUUID();
        // New requests never invalidate another still-valid reset link. A successful reset retires all of them.
        jdbc.update("insert into password_resets(id,user_id,email,token_hash,expires_at) values (?,?,?,?,current_timestamp+interval '30 minutes')",reset,user,email,hash);
        outbox.enqueue("PASSWORD_RESET",email,null,null,reset,"Reset your RootTrace password:\n\n"+mail.link("/reset-password#token="+token)
                +"\n\nThis link expires in 30 minutes. Completing a reset signs out all sessions. If you did not request it, ignore this email.");
    }

    @Transactional
    public void reset(String token,String password) {
        mail.requireEnabled();
        PasswordPolicy.validate(password);
        if(token==null || token.length()>256 || !token.startsWith("rt_rst_")) throw invalid();
        var found=jdbc.queryForList("select user_id from password_resets where token_hash=?",PayloadDigest.hash(token));
        if(found.isEmpty()) throw invalid();
        UUID user=(UUID)found.getFirst().get("user_id");
        var users=jdbc.queryForList("select email from users where id=? for update",user);
        var rows=jdbc.queryForList("select *,expires_at>clock_timestamp() as valid_time from password_resets where token_hash=? for update",PayloadDigest.hash(token));
        var reset=rows.getFirst();
        if(users.isEmpty() || reset.get("consumed_at")!=null || reset.get("revoked_at")!=null || !Boolean.TRUE.equals(reset.get("valid_time"))
                || !users.getFirst().get("email").toString().strip().toLowerCase(Locale.ROOT).equals(reset.get("email"))) throw invalid();
        String email=reset.get("email").toString();
        jdbc.update("update users set password_hash=?,auth_epoch=auth_epoch+1,verified_email=?,email_verified_at=current_timestamp,updated_at=current_timestamp where id=?",
                passwords.encode(password),email,user);
        jdbc.update("update password_resets set consumed_at=current_timestamp where id=?",reset.get("id"));
        jdbc.update("update password_resets set revoked_at=current_timestamp where user_id=? and consumed_at is null and revoked_at is null",user);
        jdbc.update("update email_verifications set revoked_at=current_timestamp where user_id=? and consumed_at is null and revoked_at is null",user);
        jdbc.update("""
            update outbound_emails set status='CANCELLED',encrypted_body=null,nonce=null,claim_token=null,lease_until=null,completed_at=current_timestamp
            where status in ('PENDING','RUNNING','FAILED') and (
              (purpose='PASSWORD_RESET' and reset_id in(select id from password_resets where user_id=?)) or
              (purpose='VERIFY_EMAIL' and verification_id in(select id from email_verifications where user_id=?)))
            """,user,user);
        outbox.enqueue("PASSWORD_CHANGED",email,null,null,(UUID)reset.get("id"),"Your RootTrace password was changed and all sessions were signed out.\n\n"
                +"If you did not make this change, request another password reset immediately and contact your service operator.\n"+mail.link("/forgot-password"));
    }
    private ResponseStatusException invalid() { return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Reset link is invalid or expired"); }
}
