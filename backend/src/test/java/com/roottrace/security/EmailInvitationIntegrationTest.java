package com.roottrace.security;

import com.fasterxml.jackson.databind.*;
import com.roottrace.infrastructure.mail.*;
import com.roottrace.ingestion.service.PayloadDigest;
import com.roottrace.support.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="roottrace.invites.hourly-limit=3")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class EmailInvitationIntegrationTest extends IntegrationTestSupport {
    private static final LocalSmtpServer SMTP=new LocalSmtpServer();
    @DynamicPropertySource static void smtp(DynamicPropertyRegistry properties) {
        properties.add("roottrace.mail.enabled",() -> "true");
        properties.add("roottrace.mail.scheduling.enabled",() -> "false");
        properties.add("roottrace.mail.public-base-url",() -> "http://localhost:3000");
        properties.add("roottrace.mail.encryption-key",() -> Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)));
        properties.add("spring.mail.host",() -> "localhost"); properties.add("spring.mail.port",SMTP::port);
        properties.add("spring.mail.properties.mail.smtp.auth",() -> "false");
        properties.add("spring.mail.properties.mail.smtp.starttls.enable",() -> "false");
        properties.add("spring.mail.properties.mail.smtp.starttls.required",() -> "false");
    }
    @AfterAll static void stopSmtp() throws Exception { SMTP.close(); }
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MailWorker worker;
    @SpyBean private JavaMailSender sender;
    @SpyBean private MailOutbox outbox;

    @Test
    void verificationUsesSmtpHashAndEncryptedOutboxWithoutGrantingSessions() throws Exception {
        Account account=signup();
        String hash=jdbc.queryForObject("select token_hash from email_verifications where user_id=?",String.class,account.id());
        byte[] encrypted=jdbc.queryForObject("select encrypted_body from outbound_emails where recipient=?",byte[].class,account.email());
        assertThat(new String(encrypted,StandardCharsets.UTF_8)).doesNotContain("rt_ver_","Verify your RootTrace");
        String token=verificationToken(account);
        mvc.perform(post("/api/v1/auth/email/verify").contentType("application/json").content(mapper.writeValueAsString(Map.of("token",token))))
                .andExpect(status().isUnauthorized());
        assertThat(hash).isEqualTo(PayloadDigest.hash(token));
        mvc.perform(post("/api/v1/auth/email/verify").header("Authorization",account.token()).contentType("application/json").content(mapper.writeValueAsString(Map.of("token",token))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VERIFIED")).andExpect(jsonPath("$.token").doesNotExist());
        mvc.perform(get("/api/v1/auth/me").header("Authorization",account.token())).andExpect(status().isOk()).andExpect(jsonPath("$.emailVerified").value(true));
        assertThat(jdbc.queryForObject("select encrypted_body from outbound_emails where recipient=?",byte[].class,account.email())).isNull();
        mvc.perform(post("/api/v1/auth/email/verify").header("Authorization",account.token()).contentType("application/json").content(mapper.writeValueAsString(Map.of("token",token))))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from workspace_members where user_id=?",Integer.class,account.id())).isEqualTo(1);
    }

    @Test
    void invitationDoesNotGrantAccessUntilVerifiedRecipientExplicitlyAccepts() throws Exception {
        Account admin=signup(),recipient=signup(); verify(admin);
        UUID invitation=invite(admin,recipient.email(),"ROLE_SRE");
        assertThat(memberships(recipient,admin.workspace())).isZero();
        mvc.perform(get("/api/v1/auth/invitations").header("Authorization",recipient.token())).andExpect(status().isForbidden());
        assertThat(accept(recipient,invitation)).isEqualTo(403);
        verify(recipient);
        mvc.perform(get("/api/v1/auth/invitations").header("Authorization",recipient.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(invitation.toString()));
        assertThat(accept(recipient,invitation)).isEqualTo(200);
        assertThat(accept(recipient,invitation)).isEqualTo(200);
        assertThat(memberships(recipient,admin.workspace())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select role from workspace_members where user_id=? and workspace_id=?",String.class,recipient.id(),admin.workspace())).isEqualTo("ROLE_SRE");
        mvc.perform(post("/api/v1/workspace/invites").header("Authorization",admin.token()).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("email",recipient.email(),"role","ROLE_ADMIN"))))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/auth/me").header("Authorization",recipient.token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceId").value(recipient.workspace().toString()));
        // Accepting never silently switches the selected workspace or restores removed membership.
        jdbc.update("delete from workspace_members where user_id=? and workspace_id=?",recipient.id(),admin.workspace());
        assertThat(accept(recipient,invitation)).isEqualTo(409);
    }

    @Test
    void expiredRevokedWrongRecipientAndConcurrentAcceptanceAreSafe() throws Exception {
        Account admin=signup(),recipient=signup(),foreign=signup(); verify(admin); verify(recipient); verify(foreign);
        UUID invitation=invite(admin,recipient.email(),"VIEWER");
        assertThat(accept(foreign,invitation)).isEqualTo(404);
        mvc.perform(delete("/api/v1/workspace/invites/"+invitation).header("Authorization",foreign.token())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/workspace/invites/"+invitation).header("Authorization",admin.token())).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select encrypted_body from outbound_emails where invitation_id=?",byte[].class,invitation)).isNull();
        assertThat(accept(recipient,invitation)).isEqualTo(409);
        UUID expired=invite(admin,recipient.email(),"VIEWER");
        jdbc.update("update workspace_invites set expires_at=now()-interval '1 second' where id=?",expired);
        assertThat(accept(recipient,expired)).isEqualTo(409);
        UUID current=invite(admin,recipient.email(),"VIEWER");
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(() -> accept(recipient,current)); var second=pool.submit(() -> accept(recipient,current));
            assertThat(List.of(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS))).containsOnly(200);
        }
        assertThat(memberships(recipient,admin.workspace())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from workspace_invites where id=? and status='ACCEPTED'",Integer.class,current)).isEqualTo(1);
    }

    @Test
    void verificationResendRevokesOldLinksAndCooldownPreventsRepeatedMessages() throws Exception {
        Account user=signup(); String old=verificationToken(user);
        requestVerification(user);
        assertThat(jdbc.queryForObject("select count(*) from email_verifications where user_id=?",Integer.class,user.id())).isEqualTo(1);
        jdbc.update("update email_verifications set created_at=now()-interval '61 seconds' where user_id=?",user.id());
        requestVerification(user);
        String fresh=verificationToken(user);
        assertThat(fresh).isNotEqualTo(old);
        mvc.perform(post("/api/v1/auth/email/verify").header("Authorization",user.token()).contentType("application/json").content(mapper.writeValueAsString(Map.of("token",old))))
                .andExpect(status().isBadRequest());
        jdbc.update("update email_verifications set expires_at=now()-interval '1 second' where user_id=? and revoked_at is null",user.id());
        mvc.perform(post("/api/v1/auth/email/verify").header("Authorization",user.token()).contentType("application/json").content(mapper.writeValueAsString(Map.of("token",fresh))))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select email_verified_at from users where id=?",java.sql.Timestamp.class,user.id())).isNull();
    }

    @Test
    void smtpFailureIsRetriedWithSafeMetadataAndNeverGrantsMembership() throws Exception {
        Account user=signup();
        doThrow(new MailSendException("provider-secret-must-not-be-stored")).when(sender).send(any(SimpleMailMessage.class));
        // Only this account's message is due; earlier accounts may have pending invitation notifications.
        jdbc.update("update outbound_emails set available_at='1970-01-01T00:00:00Z' where recipient=? and purpose='VERIFY_EMAIL'",user.email());
        worker.deliverOne();
        var row=jdbc.queryForMap("select status,attempts,last_error_code,encrypted_body from outbound_emails where recipient=?",user.email());
        assertThat(row.get("status")).isEqualTo("PENDING"); assertThat(row.get("attempts")).isEqualTo(1);
        assertThat(row.get("last_error_code")).isEqualTo("MailSendException"); assertThat(row.toString()).doesNotContain("provider-secret");
        assertThat(jdbc.queryForObject("select email_verified_at from users where id=?",java.sql.Timestamp.class,user.id())).isNull();
        doCallRealMethod().when(sender).send(any(SimpleMailMessage.class));
        jdbc.update("update outbound_emails set available_at='1970-01-01T00:00:00Z' where recipient=?",user.email());
        worker.deliverOne();
        assertThat(jdbc.queryForObject("select status from outbound_emails where recipient=?",String.class,user.email())).isEqualTo("SENT");
        assertThat(jdbc.queryForObject("select attempts from outbound_emails where recipient=?",Integer.class,user.email())).isEqualTo(2);
    }

    @Test
    void invitationCreationRequiresVerifiedAdminAndNeverReturnsBearerSecrets() throws Exception {
        Account admin=signup(),target=signup();
        mvc.perform(post("/api/v1/workspace/invites").header("Authorization",admin.token()).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("email",target.email(),"role","ROLE_ADMIN"))))
                .andExpect(status().isForbidden());
        verify(admin);
        UUID id=invite(admin,target.email(),"ADMIN");
        mvc.perform(get("/api/v1/workspace/invites").header("Authorization",admin.token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(id.toString())).andExpect(jsonPath("$.items[0].token").doesNotExist());
        mvc.perform(post("/api/v1/workspace/invites").header("Authorization",admin.token()).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("email",target.email(),"role","SUPERUSER"))))
                .andExpect(status().isBadRequest());
        assertThat(memberships(target,admin.workspace())).isZero();
    }

    @Test
    void invitationAndAccountCreationRollBackIfMailCannotBeQueued() throws Exception {
        Account admin=signup(); verify(admin);
        MailOutbox target=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(outbox);
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("queue unavailable"))
                .when(target).enqueue(eq("WORKSPACE_INVITE"),anyString(),isNull(),any(),anyString());
        mvc.perform(post("/api/v1/workspace/invites").header("Authorization",admin.token()).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("email","rollback@example.com","role","ROLE_SRE"))))
                .andExpect(status().isServiceUnavailable());
        assertThat(jdbc.queryForObject("select count(*) from workspace_invites where workspace_id=?",Integer.class,admin.workspace())).isZero();
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("queue unavailable"))
                .when(target).enqueue(eq("VERIFY_EMAIL"),anyString(),any(),isNull(),anyString());
        String email=UUID.randomUUID()+"@example.com";
        mvc.perform(post("/api/v1/auth/signup").contentType("application/json").content(mapper.writeValueAsString(Map.of(
                "fullName","Rollback","email",email,"password","test-password","workspaceName","Rollback workspace"))))
                .andExpect(status().isServiceUnavailable());
        assertThat(jdbc.queryForObject("select count(*) from users where email=?",Integer.class,email)).isZero();
    }

    @Test
    void concurrentRevocationAndAcceptanceHaveOneConsistentMembershipOutcome() throws Exception {
        Account admin=signup(),recipient=signup(); verify(admin); verify(recipient);
        UUID invitation=invite(admin,recipient.email(),"ROLE_VIEWER");
        try(var pool=Executors.newFixedThreadPool(2)) {
            var accepting=pool.submit(() -> accept(recipient,invitation));
            var revoking=pool.submit(() -> mvc.perform(delete("/api/v1/workspace/invites/"+invitation).header("Authorization",admin.token()))
                    .andReturn().getResponse().getStatus());
            int accepted=accepting.get(15,TimeUnit.SECONDS),revoked=revoking.get(15,TimeUnit.SECONDS);
            assertThat(List.of(accepted,revoked)).isIn(List.of(200,409),List.of(409,204));
            assertThat(memberships(recipient,admin.workspace())).isEqualTo(accepted==200?1:0);
        }
    }

    @Test
    void oldEmailProofCannotVerifyAChangedAddressAndExpiredQueuedSecretsAreScrubbed() throws Exception {
        Account user=signup(); String token=verificationToken(user);
        jdbc.update("update users set email=? where id=?",UUID.randomUUID()+"@example.com",user.id());
        mvc.perform(post("/api/v1/auth/email/verify").header("Authorization",user.token()).contentType("application/json").content(mapper.writeValueAsString(Map.of("token",token))))
                .andExpect(status().isBadRequest());
        Account expired=signup();
        jdbc.update("update email_verifications set expires_at=now()-interval '1 second' where user_id=?",expired.id());
        worker.deliverOne();
        assertThat(jdbc.queryForObject("select status from outbound_emails where recipient=?",String.class,expired.email())).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select encrypted_body from outbound_emails where recipient=?",byte[].class,expired.email())).isNull();
        assertThat(SMTP.forRecipient(expired.email())).isEmpty();
    }

    @Test
    void possessionOfAnotherAccountsLinkCannotVerifyIt() throws Exception {
        Account requested=signup(),other=signup();
        String token=verificationToken(requested);
        mvc.perform(post("/api/v1/auth/email/verify").header("Authorization",other.token()).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("token",token))))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select email_verified_at from users where id=?",java.sql.Timestamp.class,requested.id())).isNull();
    }

    @Test
    void invitationSendQuotaIsCheckedWithinTheWorkspaceTransaction() throws Exception {
        Account admin=signup(); verify(admin);
        for(int i=0;i<3;i++) invite(admin,UUID.randomUUID()+"@example.com","ROLE_VIEWER");
        mvc.perform(post("/api/v1/workspace/invites").header("Authorization",admin.token()).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("email",UUID.randomUUID()+"@example.com","role","ROLE_VIEWER"))))
                .andExpect(status().isTooManyRequests());
        assertThat(jdbc.queryForObject("select count(*) from workspace_invites where workspace_id=?",Integer.class,admin.workspace())).isEqualTo(3);
    }

    private Account signup() throws Exception {
        String email=UUID.randomUUID()+"@example.com";
        String json=mvc.perform(post("/api/v1/auth/signup").contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("fullName","Mail test","email",email,"password","test-password","workspaceName",UUID.randomUUID().toString()))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.emailVerified").value(false)).andReturn().getResponse().getContentAsString();
        JsonNode body=mapper.readTree(json);
        return new Account(UUID.fromString(body.path("userId").asText()),email,UUID.fromString(body.path("workspaceId").asText()),"Bearer "+body.path("token").asText());
    }
    private String verificationToken(Account account) {
        for(int i=0;i<100;i++) { if(!worker.deliverOne()) break; }
        var messages=SMTP.forRecipient(account.email()).stream().filter(message -> message.subject().contains("Verify")).toList();
        assertThat(messages).isNotEmpty();
        var match=Pattern.compile("rt_ver_[A-Za-z0-9_-]+").matcher(messages.getLast().body()); assertThat(match.find()).isTrue(); return match.group();
    }
    private void verify(Account account) throws Exception {
        String token=verificationToken(account);
        mvc.perform(post("/api/v1/auth/email/verify").header("Authorization",account.token()).contentType("application/json").content(mapper.writeValueAsString(Map.of("token",token))))
                .andExpect(status().isOk());
    }
    private void requestVerification(Account account) throws Exception {
        mvc.perform(post("/api/v1/auth/email/verification").header("Authorization",account.token())).andExpect(status().isAccepted());
    }
    private UUID invite(Account admin,String email,String role) throws Exception {
        String json=mvc.perform(post("/api/v1/workspace/invites").header("Authorization",admin.token()).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("email",email,"role",role))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.token").doesNotExist()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(mapper.readTree(json).path("id").asText());
    }
    private int accept(Account account,UUID invite) throws Exception {
        return mvc.perform(post("/api/v1/auth/invitations/"+invite+"/accept").header("Authorization",account.token()))
                .andReturn().getResponse().getStatus();
    }
    private int memberships(Account account,UUID workspace) { return jdbc.queryForObject("select count(*) from workspace_members where user_id=? and workspace_id=?",Integer.class,account.id(),workspace); }
    private record Account(UUID id,String email,UUID workspace,String token) {}
}
