package com.roottrace.domain;

import com.roottrace.support.IntegrationTestSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RecoveryMigrationIntegrationTest extends IntegrationTestSupport {
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Test
    void upgradePreservesAccountsAndQueuedVerificationMailWithoutCreatingRecoveryCredentials() {
        String schema="recovery_"+UUID.randomUUID().toString().replace("-","");
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("8").load().migrate();
        UUID user=UUID.randomUUID(),proof=UUID.randomUUID(),message=UUID.randomUUID();
        jdbc.update("insert into "+schema+".users(id,email,password_hash,full_name,verified_email,email_verified_at) values (?,'existing@example.com','preserved-hash','Existing','existing@example.com',now())",user);
        jdbc.update("insert into "+schema+".email_verifications(id,user_id,email,token_hash,expires_at) values (?,?,'existing@example.com',?,now()+interval '1 day')",proof,user,"a".repeat(64));
        byte[] ciphertext={1,2,3},nonce=new byte[12];
        jdbc.update("insert into "+schema+".outbound_emails(id,purpose,recipient,verification_id,encrypted_body,nonce) values (?,'VERIFY_EMAIL','existing@example.com',?,?,?)",message,proof,ciphertext,nonce);
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("9").load().migrate();
        assertThat(jdbc.queryForObject("select password_hash from "+schema+".users where id=?",String.class,user)).isEqualTo("preserved-hash");
        assertThat(jdbc.queryForObject("select auth_epoch from "+schema+".users where id=?",Long.class,user)).isZero();
        assertThat(jdbc.queryForObject("select verified_email from "+schema+".users where id=?",String.class,user)).isEqualTo("existing@example.com");
        assertThat(jdbc.queryForObject("select encrypted_body from "+schema+".outbound_emails where id=?",byte[].class,message)).isEqualTo(ciphertext);
        assertThat(jdbc.queryForObject("select count(*) from "+schema+".password_resets",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from "+schema+".revoked_sessions",Integer.class)).isZero();
    }
}
