package com.roottrace.domain;

import com.roottrace.domain.model.Workspace;
import com.roottrace.support.IntegrationTestSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class EmailInvitationMigrationIntegrationTest extends IntegrationTestSupport {
    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;
    @Test
    void upgradeRetiresOldInviteCredentialsWithoutEvictingExistingMembersOrAssumingVerification() {
        String schema="email_"+UUID.randomUUID().toString().replace("-","");
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("7").load().migrate();
        UUID user=UUID.randomUUID(),invite=UUID.randomUUID();
        jdbc.update("insert into "+schema+".users(id,email,password_hash,full_name) values (?,'existing@example.com','preserved-hash','Existing user')",user);
        jdbc.update("insert into "+schema+".workspace_members(workspace_id,user_id,role) values (?,?,'ROLE_ADMIN')",Workspace.LEGACY_WORKSPACE_ID,user);
        jdbc.update("insert into "+schema+".workspace_invites(id,workspace_id,email,role,token,invited_by,expires_at) values (?,?,'existing@example.com','ROLE_SRE','old-plaintext-invite',?,now()+interval '1 day')",invite,Workspace.LEGACY_WORKSPACE_ID,user);
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("8").load().migrate();
        assertThat(jdbc.queryForObject("select count(*) from "+schema+".workspace_members where user_id=?",Integer.class,user)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select password_hash from "+schema+".users where id=?",String.class,user)).isEqualTo("preserved-hash");
        assertThat(jdbc.queryForObject("select email_verified_at from "+schema+".users where id=?",java.sql.Timestamp.class,user)).isNull();
        assertThat(jdbc.queryForObject("select status from "+schema+".workspace_invites where id=?",String.class,invite)).isEqualTo("REVOKED");
        assertThat(jdbc.queryForObject("select invited_by_email from "+schema+".workspace_invites where id=?",String.class,invite)).isEqualTo("existing@example.com");
        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_schema=? and table_name='workspace_invites' and column_name='token'",Integer.class,schema)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from "+schema+".outbound_emails",Integer.class)).isZero();
    }
}
