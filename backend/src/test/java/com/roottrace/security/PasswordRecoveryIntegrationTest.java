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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"roottrace.recovery.requests-per-peer-window=8","roottrace.recovery.requests-per-account-hour=3"})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PasswordRecoveryIntegrationTest extends IntegrationTestSupport {
    private static final String ORIGINAL="original-password-123",NEW="replacement-password-456";
    private static final LocalSmtpServer SMTP=new LocalSmtpServer();
    private static final AtomicInteger PEERS=new AtomicInteger();
    private final String peer="192.0.2."+PEERS.incrementAndGet();
    @DynamicPropertySource static void smtp(DynamicPropertyRegistry p) {
        p.add("roottrace.mail.enabled",() -> "true"); p.add("roottrace.mail.scheduling.enabled",() -> "false");
        p.add("roottrace.mail.public-base-url",() -> "http://localhost:3000");
        p.add("roottrace.mail.encryption-key",() -> Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)));
        p.add("spring.mail.host",() -> "localhost"); p.add("spring.mail.port",SMTP::port);
        p.add("spring.mail.properties.mail.smtp.auth",() -> "false");
        p.add("spring.mail.properties.mail.smtp.starttls.enable",() -> "false"); p.add("spring.mail.properties.mail.smtp.starttls.required",() -> "false");
    }
    @AfterAll static void closeMail() throws Exception { SMTP.close(); }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired MailWorker worker;
    @Autowired com.roottrace.security.service.RecoveryRequestWorker recoveryWorker;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtTokenProvider jwt;
    @Autowired javax.sql.DataSource dataSource;
    @org.springframework.boot.test.web.server.LocalServerPort int port;
    @SpyBean MailOutbox outbox;

    @Test
    void resetProvesEmailChangesPasswordAndRevokesAllSessionsAtomically() throws Exception {
        Account account=signup(); String other=login(account,ORIGINAL);
        request(account.email()); String token=resetToken(account);
        assertThat(jdbc.queryForObject("select token_hash from password_resets where user_id=?",String.class,account.id())).isEqualTo(PayloadDigest.hash(token));
        assertThat(reset(token,NEW)).isEqualTo(200);
        assertThat(reset(token,NEW)).isEqualTo(400);
        me(account.token(),401); me(other,401);
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(json(Map.of("email",account.email(),"password",ORIGINAL)))).andExpect(status().isUnauthorized());
        String fresh=login(account,NEW); me(fresh,200);
        mvc.perform(get("/api/v1/auth/me").header("Authorization",fresh)).andExpect(jsonPath("$.emailVerified").value(true));
        assertThat(jdbc.queryForObject("select count(*) from workspace_members where user_id=?",Integer.class,account.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from email_verifications where user_id=? and consumed_at is null and revoked_at is null",Integer.class,account.id())).isZero();
        drain();
        assertThat(SMTP.forRecipient(account.email()).stream().filter(m -> m.subject().equals("Your RootTrace password was changed"))).hasSize(1);
        assertThat(jdbc.queryForObject("select encrypted_body from outbound_emails where purpose='PASSWORD_CHANGED' and recipient=?",byte[].class,account.email())).isNull();
    }

    @Test
    void logoutRevokesWholeSessionFamilyAcrossWorkspaceSwitchesOnly() throws Exception {
        Account account=signup(); String other=login(account,ORIGINAL);
        String switched=mvc.perform(post("/api/v1/auth/select-workspace").header("Authorization",other).contentType("application/json")
                .content(json(Map.of("workspaceId",account.workspace())))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String selected="Bearer "+mapper.readTree(switched).path("token").asText();
        var before=jwt.readIdentity(other.substring(7)); var after=jwt.readIdentity(selected.substring(7));
        assertThat(after.sessionId()).isEqualTo(before.sessionId()); assertThat(after.expiresAt()).isEqualTo(before.expiresAt());
        mvc.perform(post("/api/v1/auth/logout").header("Authorization",selected)).andExpect(status().isNoContent());
        me(other,401); me(selected,401); me(account.token(),200);
        mvc.perform(post("/api/v1/auth/select-workspace").header("Authorization",other).contentType("application/json")
                .content(json(Map.of("workspaceId",account.workspace())))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutAllInvalidatesOtherSessionsButFreshLoginWorks() throws Exception {
        Account account=signup(); String other=login(account,ORIGINAL);
        mvc.perform(post("/api/v1/auth/logout-all").header("Authorization",other)).andExpect(status().isNoContent());
        me(account.token(),401); me(other,401); me(login(account,ORIGINAL),200);
        assertThat(jdbc.queryForObject("select auth_epoch from users where id=?",Long.class,account.id())).isEqualTo(1);
    }

    @Test
    void missingExpiredStaleAndVerificationTokensCannotReset() throws Exception {
        Account account=signup(); request(account.email()); String token=resetToken(account);
        assertThat(reset("rt_rst_missing",NEW)).isEqualTo(400);
        assertThat(reset(token,"too-short")).isEqualTo(400);
        assertThat(reset(token,"é".repeat(37))).isEqualTo(400);
        jdbc.update("update password_resets set expires_at=now()-interval '1 second' where user_id=?",account.id());
        assertThat(reset(token,NEW)).isEqualTo(400);
        String proof=SMTP.forRecipient(account.email()).stream().filter(m -> m.subject().contains("Verify")).findFirst().orElseThrow().body();
        var match=Pattern.compile("rt_ver_[A-Za-z0-9_-]+").matcher(proof); assertThat(match.find()).isTrue();
        assertThat(reset(match.group(),NEW)).isEqualTo(400);
        assertThat(passwords.matches(ORIGINAL,jdbc.queryForObject("select password_hash from users where id=?",String.class,account.id()))).isTrue();
        // A token is tied to its email snapshot even if an administrative change bypasses the entity setter.
        jdbc.update("update password_resets set expires_at=now()+interval '1 day' where user_id=?",account.id());
        jdbc.update("update users set email=? where id=?",UUID.randomUUID()+"@example.com",account.id());
        assertThat(reset(token,NEW)).isEqualTo(400); me(account.token(),200);
    }

    @Test
    void concurrentResetConsumesProofExactlyOnce() throws Exception {
        Account account=signup(); request(account.email()); String token=resetToken(account);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(() -> reset(token,NEW)); var b=pool.submit(() -> reset(token,"other-replacement-789"));
            assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,400);
        }
        assertThat(jdbc.queryForObject("select auth_epoch from users where id=?",Long.class,account.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from outbound_emails where purpose='PASSWORD_CHANGED' and recipient=?",Integer.class,account.email())).isEqualTo(1);
    }

    @Test
    void genericAcknowledgementCooldownAndQuotaDoNotInvalidateExistingLinks() throws Exception {
        Account account=signup(); String unknown=UUID.randomUUID()+"@example.com";
        assertThat(request(account.email())).isEqualTo(request(unknown)); request(account.email());
        assertThat(resetCount(account)).isEqualTo(1);
        String first=resetToken(account);
        for(int i=0;i<2;i++) {
            jdbc.update("update password_resets set created_at=now()-interval '61 seconds' where user_id=?",account.id()); request(account.email());
        }
        assertThat(resetCount(account)).isEqualTo(3);
        jdbc.update("update password_resets set created_at=now()-interval '61 seconds' where user_id=?",account.id()); request(account.email());
        assertThat(resetCount(account)).isEqualTo(3);
        assertThat(reset(first,NEW)).isEqualTo(200);
        assertThat(jdbc.queryForObject("select count(*) from password_resets where user_id=? and revoked_at is not null",Integer.class,account.id())).isEqualTo(2);
    }

    @Test
    void directPeerLimitCannotBeBypassedByForwardedHeaders() throws Exception {
        Account account=signup();
        for(int i=0;i<8;i++) request(UUID.randomUUID()+"@example.com");
        mvc.perform(fromPeer(post("/api/v1/auth/password/forgot")).header("X-Forwarded-For","different-client")
                .contentType("application/json").content(json(Map.of("email",account.email())))).andExpect(status().isAccepted());
        assertThat(resetCount(account)).isZero();
        assertThat(reset("rt_rst_missing",NEW)).isEqualTo(429);
    }

    @Test
    void acknowledgementDoesNotWaitForOrLookUpTheAccount() throws Exception {
        Account account=signup();
        try(var pool=Executors.newSingleThreadExecutor(); var connection=dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try(var statement=connection.prepareStatement("select id from users where id=? for update")) {
                statement.setObject(1,account.id()); statement.executeQuery().close();
            }
            var response=pool.submit(() -> mvc.perform(fromPeer(post("/api/v1/auth/password/forgot")).contentType("application/json")
                    .content(json(Map.of("email",account.email())))).andReturn().getResponse());
            // A synchronous account lookup with locking would block behind the connection above.
            assertThat(response.get(3,TimeUnit.SECONDS).getStatus()).isEqualTo(202);
            assertThat(resetCount(account)).isZero();
            connection.rollback();
        }
        while(recoveryWorker.processOne()) {}
        assertThat(resetCount(account)).isEqualTo(1);
    }

    @Test
    void realHttpRecoveryLoginAndRevocationSmokeCheck() throws Exception {
        try(var client=java.net.http.HttpClient.newHttpClient()) {
            String email=UUID.randomUUID()+"@example.com";
            var signup=http(client,"POST","signup",null,Map.of("email",email,"password",ORIGINAL,"fullName","HTTP smoke","workspaceName",UUID.randomUUID().toString()));
            assertThat(signup.statusCode()).isEqualTo(201);
            var body=mapper.readTree(signup.body());
            Account account=new Account(UUID.fromString(body.path("userId").asText()),email,UUID.fromString(body.path("workspaceId").asText()),"Bearer "+body.path("token").asText());
            var requested=http(client,"POST","password/forgot",null,Map.of("email",email));
            assertThat(requested.statusCode()).isEqualTo(202);
            assertThat(requested.headers().firstValue("Cache-Control")).hasValue("no-store");
            while(recoveryWorker.processOne()) {}
            String proof=resetToken(account);
            var reset=http(client,"POST","password/reset",null,Map.of("token",proof,"password",NEW));
            assertThat(reset.statusCode()).isEqualTo(200);
            assertThat(mapper.readTree(reset.body()).has("token")).isFalse();
            assertThat(http(client,"GET","me",account.token(),null).statusCode()).isEqualTo(401);
            var login=http(client,"POST","login",null,Map.of("email",email,"password",NEW));
            assertThat(login.statusCode()).isEqualTo(200);
            String fresh="Bearer "+mapper.readTree(login.body()).path("token").asText();
            assertThat(http(client,"POST","logout-all",fresh,Map.of()).statusCode()).isEqualTo(204);
            assertThat(http(client,"GET","me",fresh,null).statusCode()).isEqualTo(401);
        }
    }

    @Test
    void notificationQueueFailureRollsBackPasswordProofAndRevocation() throws Exception {
        Account account=signup(); request(account.email()); String token=resetToken(account);
        MailOutbox target=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(outbox);
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("queue unavailable"))
                .when(target).enqueue(eq("PASSWORD_CHANGED"),anyString(),isNull(),isNull(),any(),anyString());
        assertThat(reset(token,NEW)).isEqualTo(503);
        me(account.token(),200);
        assertThat(jdbc.queryForObject("select consumed_at from password_resets where user_id=?",java.sql.Timestamp.class,account.id())).isNull();
        assertThat(jdbc.queryForObject("select auth_epoch from users where id=?",Long.class,account.id())).isZero();
        assertThat(passwords.matches(ORIGINAL,jdbc.queryForObject("select password_hash from users where id=?",String.class,account.id()))).isTrue();
    }

    private String json(Object data) throws Exception { return mapper.writeValueAsString(data); }
    private java.net.http.HttpResponse<String> http(java.net.http.HttpClient client,String method,String path,String token,Object body) throws Exception {
        var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+"/api/v1/auth/"+path)).timeout(java.time.Duration.ofSeconds(10));
        if(token!=null) request.header("Authorization",token);
        if(body!=null) request.header("Content-Type","application/json");
        return client.send(request.method(method,body==null?java.net.http.HttpRequest.BodyPublishers.noBody():java.net.http.HttpRequest.BodyPublishers.ofString(json(body))).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
    }
    private MockHttpServletRequestBuilder fromPeer(MockHttpServletRequestBuilder request) { return request.with(http -> { http.setRemoteAddr(peer); return http; }); }
    private String request(String email) throws Exception {
        String response=mvc.perform(fromPeer(post("/api/v1/auth/password/forgot")).contentType("application/json").content(json(Map.of("email",email))))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.token").doesNotExist()).andReturn().getResponse().getContentAsString();
        for(int i=0;i<100 && recoveryWorker.processOne();i++) {}
        return response;
    }
    private int reset(String token,String password) throws Exception {
        return mvc.perform(fromPeer(post("/api/v1/auth/password/reset")).contentType("application/json").content(json(Map.of("token",token,"password",password))))
                .andReturn().getResponse().getStatus();
    }
    private Account signup() throws Exception {
        String email=UUID.randomUUID()+"@example.com";
        var body=mapper.readTree(mvc.perform(post("/api/v1/auth/signup").contentType("application/json")
                .content(json(Map.of("email",email,"password",ORIGINAL,"fullName","Recovery test","workspaceName",UUID.randomUUID().toString()))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        return new Account(UUID.fromString(body.path("userId").asText()),email,UUID.fromString(body.path("workspaceId").asText()),"Bearer "+body.path("token").asText());
    }
    private String login(Account account,String password) throws Exception {
        var body=mapper.readTree(mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(json(Map.of("email",account.email(),"password",password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return "Bearer "+body.path("token").asText();
    }
    private void drain() { for(int i=0;i<100 && worker.deliverOne();i++) {} }
    private String resetToken(Account account) {
        byte[] encrypted=jdbc.queryForObject("select encrypted_body from outbound_emails where purpose='PASSWORD_RESET' and recipient=? order by created_at desc limit 1",byte[].class,account.email());
        assertThat(new String(encrypted,StandardCharsets.UTF_8)).doesNotContain("rt_rst_");
        drain(); var messages=SMTP.forRecipient(account.email()).stream().filter(m -> m.subject().equals("Reset your RootTrace password")).toList();
        var match=Pattern.compile("rt_rst_[A-Za-z0-9_-]+").matcher(messages.getLast().body()); assertThat(match.find()).isTrue(); return match.group();
    }
    private void me(String token,int expected) throws Exception { mvc.perform(get("/api/v1/auth/me").header("Authorization",token)).andExpect(status().is(expected)); }
    private int resetCount(Account account) { return jdbc.queryForObject("select count(*) from password_resets where user_id=?",Integer.class,account.id()); }
    private record Account(UUID id,String email,UUID workspace,String token) {}
}
