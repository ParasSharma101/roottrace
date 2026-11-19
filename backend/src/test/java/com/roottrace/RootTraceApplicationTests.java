package com.roottrace;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RootTraceApplicationTests extends com.roottrace.support.IntegrationTestSupport {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private Environment environment;

    @Test
    void contextLoads() {
        assertThat(jdbcTemplate.queryForObject("select current_database()", String.class))
                .isEqualTo("roottrace_test");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success and version in ('1', '2')",
                Integer.class)).isEqualTo(2);
        assertThat(environment.getProperty("github.token")).isEmpty();
        assertThat(environment.getProperty("gemini.api-key")).isEmpty();
    }
}
