package com.foodie.api.settings;

import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SettingsRepository {
    private final JdbcTemplate jdbc;

    public SettingsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, String> storedValues() {
        Map<String, String> values = new HashMap<>();
        jdbc.query("SELECT setting_key, setting_value FROM settings", rs -> {
            values.put(rs.getString("setting_key"), rs.getString("setting_value"));
        });
        return values;
    }

    public void upsert(String key, String value) {
        jdbc.update("INSERT INTO settings (setting_key, setting_value) VALUES (?, ?) "
            + "ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value)", key, value);
    }

    public boolean hasActiveWindow() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM maintenance_windows WHERE starts_at <= NOW() AND ends_at >= NOW()",
            Integer.class
        );
        return count != null && count > 0;
    }
}
