package com.foodie.api;

import java.util.List;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        jdbc.queryForObject("SELECT 1", Integer.class);
        return Map.of("status", "ok", "schemaVersion", schemaVersion());
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, String>> ready() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            String version = schemaVersion();
            if ("none".equals(version) || "unknown".equals(version)) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "not-ready", "schemaVersion", version));
            }
            return ResponseEntity.ok(Map.of("status", "ready", "schemaVersion", version));
        } catch (DataAccessException error) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "not-ready", "schemaVersion", "unknown"));
        }
    }

    private String schemaVersion() {
        try {
            List<String> versions = jdbc.query(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1",
                (rs, row) -> rs.getString(1)
            );
            return versions.isEmpty() ? "none" : versions.getFirst();
        } catch (DataAccessException error) {
            return "unknown";
        }
    }
}
