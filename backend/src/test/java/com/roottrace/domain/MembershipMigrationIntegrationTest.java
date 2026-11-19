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
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MembershipMigrationIntegrationTest extends IntegrationTestSupport {
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Test
    void versioningPreservesHistoricalMembershipsAndDoesNotInventAuditHistory() {
        String schema="membership_"+UUID.randomUUID().toString().replace("-","");
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("9").load().migrate();
        UUID user=UUID.randomUUID(),member=UUID.randomUUID();
        jdbc.update("insert into "+schema+".users(id,email,password_hash,full_name) values (?,'existing@example.com','preserved','Existing')",user);
        jdbc.update("insert into "+schema+".workspace_members(id,workspace_id,user_id,role) values (?,?,?,'ROLE_ADMIN')",member,Workspace.LEGACY_WORKSPACE_ID,user);
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("10").load().migrate();
        var row=jdbc.queryForMap("select id,role,version from "+schema+".workspace_members where id=?",member);
        assertThat(row.get("role")).isEqualTo("ROLE_ADMIN"); assertThat(row.get("version")).isEqualTo(0L);
        assertThat(jdbc.queryForObject("select count(*) from "+schema+".workspace_membership_audit",Integer.class)).isZero();
    }
}
