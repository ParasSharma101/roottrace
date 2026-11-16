package com.roottrace.domain;

import com.roottrace.domain.model.Workspace;
import com.roottrace.support.IntegrationTestSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class DurablePipelineMigrationIntegrationTest extends IntegrationTestSupport {
    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void upgradePreservesExistingEvidenceAndConstrainsQueueOwnership() {
        String schema="durable_"+UUID.randomUUID().toString().replace("-", "");
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("5").load().migrate();
        UUID incident=UUID.randomUUID(), workspace=UUID.randomUUID(), submission=UUID.randomUUID();
        jdbc.update("insert into "+schema+".incidents(id,title,workspace_id) values (?,'Preserved incident',?)",incident,Workspace.LEGACY_WORKSPACE_ID);
        jdbc.update("insert into "+schema+".workspaces(id,name,slug,api_key) values (?,'Other',?,?)",workspace,workspace.toString(),"retired_"+workspace);
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("6").load().migrate();
        assertThat(jdbc.queryForObject("select title from "+schema+".incidents where id=?",String.class,incident)).isEqualTo("Preserved incident");
        assertThat(jdbc.queryForObject("select count(*) from "+schema+".durable_tasks",Integer.class)).isZero();
        jdbc.update("insert into "+schema+".ingestion_submissions(id,workspace_id,request_kind,payload_hash,item_count) values (?,?,'LOG','hash',1)",submission,workspace);
        assertThatThrownBy(() -> jdbc.update("insert into "+schema+".durable_tasks(id,workspace_id,kind,incident_id) values (?,?,'INVESTIGATE',?)",UUID.randomUUID(),workspace,incident))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into "+schema+".durable_tasks(id,workspace_id,kind,submission_id,topic,partition_key,event_payload) values (?,?,'PUBLISH',?,'raw-logs','key','payload')",
                UUID.randomUUID(),Workspace.LEGACY_WORKSPACE_ID,submission)).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("insert into "+schema+".durable_tasks(id,workspace_id,kind,incident_id) values (?,?,'INVESTIGATE',?)",UUID.randomUUID(),Workspace.LEGACY_WORKSPACE_ID,incident);
        assertThatThrownBy(() -> jdbc.update("insert into "+schema+".durable_tasks(id,workspace_id,kind,incident_id) values (?,?,'INVESTIGATE',?)",UUID.randomUUID(),Workspace.LEGACY_WORKSPACE_ID,incident))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
