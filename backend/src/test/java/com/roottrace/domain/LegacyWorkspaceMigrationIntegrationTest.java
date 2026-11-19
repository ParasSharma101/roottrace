package com.roottrace.domain;

import com.roottrace.security.AuthDataSeeder;
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
class LegacyWorkspaceMigrationIntegrationTest extends IntegrationTestSupport {
    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void upgradePreservesDataAndOnlyBackfillsUnassignedIncidents() {
        // Independent schema inside the disposable database; never clean public data.
        String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("2").load().migrate();
        UUID assignedWorkspace = UUID.randomUUID();
        UUID oldIncident = UUID.randomUUID();
        UUID assignedIncident = UUID.randomUUID();
        jdbc.update("insert into " + schema + ".workspaces(id,name,slug,api_key) values (?, 'Owned', ?, ?)",
                assignedWorkspace, assignedWorkspace.toString(), "fixture-" + assignedWorkspace);
        jdbc.update("insert into " + schema + ".incidents(id,title,summary) values (?, 'Keep title', 'Keep summary')", oldIncident);
        jdbc.update("insert into " + schema + ".incidents(id,title,workspace_id) values (?, 'Assigned', ?)",
                assignedIncident, assignedWorkspace);

        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("3").load().migrate();
        assertThat(jdbc.queryForObject("select workspace_id from " + schema + ".incidents where id=?", UUID.class, oldIncident))
                .isEqualTo(AuthDataSeeder.DEFAULT_WORKSPACE_ID);
        assertThat(jdbc.queryForObject("select workspace_id from " + schema + ".incidents where id=?", UUID.class, assignedIncident))
                .isEqualTo(assignedWorkspace);
        assertThat(jdbc.queryForObject("select summary from " + schema + ".incidents where id=?", String.class, oldIncident))
                .isEqualTo("Keep summary");
        assertThat(jdbc.queryForObject("select count(*) from " + schema + ".incidents", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select is_legacy from " + schema + ".workspaces where id=?", Boolean.class,
                AuthDataSeeder.DEFAULT_WORKSPACE_ID)).isTrue();
        assertThat(jdbc.queryForObject("select is_legacy from " + schema + ".workspaces where id=?", Boolean.class,
                assignedWorkspace)).isFalse();
    }
}
