package com.roottrace.domain;

import com.roottrace.support.IntegrationTestSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class IngestionKeyMigrationIntegrationTest extends IntegrationTestSupport {
    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void existingTenantKeysArePreservedAsHashesAndPlaintextIsRetired() throws Exception {
        String schema = "key_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("3").load().migrate();
        UUID workspace = UUID.randomUUID();
        String secret = "rt_live_existing_fixture_тест_123456789";
        jdbc.update("insert into " + schema + ".workspaces(id,name,slug,api_key) values (?, 'Tenant', ?, ?)",
                workspace, workspace.toString(), secret);
        UUID shortKeyWorkspace = UUID.randomUUID();
        jdbc.update("insert into " + schema + ".workspaces(id,name,slug,api_key) values (?, 'Short key', ?, 'too-short')",
                shortKeyWorkspace, shortKeyWorkspace.toString());
        jdbc.update("update " + schema + ".workspaces set api_key='rt_live_legacy_fixture_12345678901234567890' where is_legacy"); // gitleaks:allow -- public legacy migration fixture
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("4").load().migrate();
        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)));
        assertThat(jdbc.queryForObject("select key_hash from " + schema + ".workspace_ingestion_keys where workspace_id=?",
                String.class, workspace)).isEqualTo(expected);
        assertThat(jdbc.queryForObject("select count(*) from " + schema + ".workspace_ingestion_keys", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select api_key from " + schema + ".workspaces where id=?", String.class, workspace))
                .startsWith("retired_").isNotEqualTo(secret);
    }
}
