package com.foodie.api.finance;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CommissionRepository {
    private final JdbcTemplate jdbc;

    public CommissionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<BigDecimal> global() {
        return jdbc.query("SELECT percent FROM commission_rules WHERE scope = 'global' AND active = TRUE LIMIT 1",
            (rs, row) -> rs.getBigDecimal(1)).stream().findFirst();
    }

    public Optional<BigDecimal> forRestaurant(long restaurantId) {
        return jdbc.query("SELECT percent FROM commission_rules WHERE scope = 'restaurant' AND restaurant_id = ? AND active = TRUE LIMIT 1",
            (rs, row) -> rs.getBigDecimal(1), restaurantId).stream().findFirst();
    }

    public List<Map<String, Object>> rules() {
        return jdbc.queryForList(
            "SELECT c.scope, c.restaurant_id, c.percent, r.name AS restaurant_name "
                + "FROM commission_rules c LEFT JOIN restaurants r ON r.id = c.restaurant_id "
                + "WHERE c.active = TRUE ORDER BY c.scope, r.name");
    }

    public void upsertGlobal(BigDecimal percent) {
        int changed = jdbc.update("UPDATE commission_rules SET percent = ?, active = TRUE WHERE scope = 'global'", percent);
        if (changed == 0) jdbc.update("INSERT INTO commission_rules (scope, restaurant_id, percent, active) VALUES ('global', NULL, ?, TRUE)", percent);
    }

    public void upsertRestaurant(long restaurantId, BigDecimal percent) {
        int changed = jdbc.update("UPDATE commission_rules SET percent = ?, active = TRUE WHERE scope = 'restaurant' AND restaurant_id = ?", percent, restaurantId);
        if (changed == 0) jdbc.update("INSERT INTO commission_rules (scope, restaurant_id, percent, active) VALUES ('restaurant', ?, ?, TRUE)", restaurantId, percent);
    }
}
