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
class TaskRecoveryMigrationIntegrationTest extends IntegrationTestSupport {
    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void upgradePreservesFailedWorkAndEnforcesScopedAuditHistory() {
        String schema="recovery_"+UUID.randomUUID().toString().replace("-","");
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("6").load().migrate();
        UUID incident=UUID.randomUUID(),task=UUID.randomUUID(),actor=UUID.randomUUID(),foreign=UUID.randomUUID();
        jdbc.update("insert into "+schema+".incidents(id,title,workspace_id) values (?,'Preserved incident',?)",incident,Workspace.LEGACY_WORKSPACE_ID);
        jdbc.update("insert into "+schema+".durable_tasks(id,workspace_id,kind,incident_id,status,attempts,last_error_code) values (?,?,'INVESTIGATE',?,'FAILED',5,'ProviderUnavailable')",task,Workspace.LEGACY_WORKSPACE_ID,incident);
        jdbc.update("insert into "+schema+".users(id,email,password_hash,full_name) values (?,'actor@example.com','unused','Actor')",actor);
        jdbc.update("insert into "+schema+".workspaces(id,name,slug,api_key) values (?,'Other',?,?)",foreign,foreign.toString(),"retired_"+foreign);
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("7").load().migrate();
        assertThat(jdbc.queryForObject("select total_attempts from "+schema+".durable_tasks where id=?",Integer.class,task)).isEqualTo(5);
        assertThat(jdbc.queryForObject("select status from "+schema+".durable_tasks where id=?",String.class,task)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select redrive_count from "+schema+".durable_tasks where id=?",Integer.class,task)).isZero();
        String insert="insert into "+schema+".durable_task_redrives(id,task_id,workspace_id,actor_user_id,actor_email,reason,cycle_number,previous_attempts,total_attempts_before) values (?,?,?,?,'actor@example.com',?,1,5,5)";
        assertThatThrownBy(() -> jdbc.update(insert,UUID.randomUUID(),task,foreign,actor,"fixed"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert,UUID.randomUUID(),task,Workspace.LEGACY_WORKSPACE_ID,actor," "))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(insert,UUID.randomUUID(),task,Workspace.LEGACY_WORKSPACE_ID,actor,"Restored provider access");
        assertThatThrownBy(() -> jdbc.update(insert,UUID.randomUUID(),task,Workspace.LEGACY_WORKSPACE_ID,actor,"Duplicate cycle"))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("delete from "+schema+".users where id=?",actor);
        assertThat(jdbc.queryForObject("select actor_user_id from "+schema+".durable_task_redrives",UUID.class)).isNull();
        assertThat(jdbc.queryForObject("select actor_email from "+schema+".durable_task_redrives",String.class)).isEqualTo("actor@example.com");
        assertThat(jdbc.queryForObject("select reason from "+schema+".durable_task_redrives",String.class)).isEqualTo("Restored provider access");
    }
}
