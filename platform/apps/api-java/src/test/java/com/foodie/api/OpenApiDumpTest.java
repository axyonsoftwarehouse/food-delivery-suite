package com.foodie.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Boots the full web context without a database to expose the springdoc spec at /v3/api-docs.
 * Run with {@code -Dopenapi.dump=true} to write the spec to packages/api-client/openapi.json,
 * which feeds the TypeScript client generation.
 */
@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
    "management.health.defaults.enabled=false",
    "app.migrate-only=false"
})
@AutoConfigureMockMvc
class OpenApiDumpTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JdbcTemplate jdbc;

    @MockitoBean
    private NamedParameterJdbcTemplate namedJdbc;

    @Test
    void exposesOpenApiSpecAndDumpsItWhenRequested() throws Exception {
        String spec = mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(spec).contains("\"openapi\"");
        assertThat(spec).contains("/orders/{id}/status");
        assertThat(spec).contains("/auth/login");

        if (Boolean.getBoolean("openapi.dump")) {
            Path target = Path.of("..", "..", "packages", "api-client", "openapi.json");
            Files.createDirectories(target.getParent());
            Files.writeString(target, spec);
        }
    }
}
