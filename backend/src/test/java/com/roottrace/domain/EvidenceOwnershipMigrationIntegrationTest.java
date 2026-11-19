package com.roottrace.domain;

import com.roottrace.domain.model.Workspace;
import com.roottrace.support.IntegrationTestSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;

import javax.sql.DataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class EvidenceOwnershipMigrationIntegrationTest extends IntegrationTestSupport {
    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void upgradePreservesEvidenceInLegacyAndEnforcesOwnership() {
        String schema = "evidence_" + UUID.randomUUID().toString().replace("-", "");
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("4").load().migrate();
        UUID logId = UUID.randomUUID();
        UUID deploymentId = UUID.randomUUID();
        jdbc.update("insert into " + schema + ".log_events(id,service_name,timestamp,log_level,message) values (?, 'shared', now(), 'ERROR', 'Preserved log')", logId);
        jdbc.update("insert into " + schema + ".deployments(id,service_name,deployment_time,version_tag,github_commit_hash) values (?, 'shared', now(), 'v1', 'commit')", deploymentId);
        jdbc.update("insert into " + schema + ".github_prs(deployment_id,pr_number,title,author) values (?, 42, 'Preserved PR', 'author')", deploymentId);

        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("5").load().migrate();
        for (String table : new String[]{"log_events", "deployments"}) {
            assertThat(jdbc.queryForObject("select workspace_id from " + schema + "." + table, UUID.class))
                    .isEqualTo(Workspace.LEGACY_WORKSPACE_ID);
            assertThatThrownBy(() -> jdbc.update("update " + schema + "." + table + " set workspace_id=null"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("update " + schema + "." + table + " set workspace_id=?", UUID.randomUUID()))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThat(jdbc.queryForObject("select message from " + schema + ".log_events where id=?", String.class, logId)).isEqualTo("Preserved log");
        assertThat(jdbc.queryForObject("select version_tag from " + schema + ".deployments where id=?", String.class, deploymentId)).isEqualTo("v1");
        assertThat(jdbc.queryForObject("select title from " + schema + ".github_prs where deployment_id=?", String.class, deploymentId)).isEqualTo("Preserved PR");
        assertThatThrownBy(() -> jdbc.update("insert into " + schema + ".log_events(service_name,timestamp,log_level,message) values ('shared', now(), 'ERROR', 'No owner')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
