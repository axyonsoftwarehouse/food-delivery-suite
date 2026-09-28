package com.foodie.api.admin;

import com.foodie.api.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Enforces the restaurant's contracted modules at the API boundary. */
@Service
public class ModuleAccessService {
    private final JdbcTemplate jdbc;

    public ModuleAccessService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void require(long restaurantId, String moduleKey) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM restaurant_modules WHERE restaurant_id = ? AND module_key = ? AND enabled = TRUE",
            Integer.class, restaurantId, moduleKey);
        if (count == null || count == 0) throw new ApiException(403, "Módulo não habilitado para esta loja");
    }
}
