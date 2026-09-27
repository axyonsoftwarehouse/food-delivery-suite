package com.foodie.api.admin;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerRepository {
    private static final String COMPLETED = "('delivered','completed','served')";

    private final JdbcTemplate jdbc;

    public CustomerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> list(String query, Long before, int limit) {
        String like = query == null || query.isBlank() ? null : "%" + query.strip() + "%";
        return jdbc.queryForList(
            "SELECT u.id, u.name, u.email, u.phone, u.created_at, u.suspended_at IS NOT NULL AS suspended, "
                + "(SELECT COUNT(*) FROM orders o WHERE o.customer_id = u.id) AS orders, "
                + "(SELECT COALESCE(SUM(CASE WHEN o.status IN " + COMPLETED + " THEN o.total_cents ELSE 0 END),0) FROM orders o WHERE o.customer_id = u.id) AS spend_cents "
                + "FROM users u WHERE u.role = 'customer' "
                + "AND (? IS NULL OR u.name LIKE ? OR u.email LIKE ? OR u.phone LIKE ?) "
                + "AND (? IS NULL OR u.id < ?) "
                + "ORDER BY u.id DESC LIMIT ?",
            like, like, like, like, before, before, limit + 1);
    }

    public Optional<Map<String, Object>> find(long id) {
        return jdbc.queryForList(
            "SELECT u.id, u.name, u.email, u.phone, u.created_at, u.suspended_at IS NOT NULL AS suspended, u.suspended_reason, "
                + "(SELECT COUNT(*) FROM orders o WHERE o.customer_id = u.id) AS orders, "
                + "(SELECT COALESCE(SUM(CASE WHEN o.status IN " + COMPLETED + " THEN o.total_cents ELSE 0 END),0) FROM orders o WHERE o.customer_id = u.id) AS spend_cents, "
                + "(SELECT MAX(o.created_at) FROM orders o WHERE o.customer_id = u.id) AS last_order_at "
                + "FROM users u WHERE u.id = ? AND u.role = 'customer'", id).stream().findFirst();
    }

    public List<Map<String, Object>> orders(long id) {
        return jdbc.queryForList(
            "SELECT id, status, order_type, total_cents, created_at FROM orders WHERE customer_id = ? ORDER BY id DESC LIMIT 50", id);
    }

    public List<Map<String, Object>> addresses(long id) {
        return jdbc.queryForList(
            "SELECT a.id, a.label, a.street, a.number, a.neighborhood, a.complement, a.postal_code, z.name AS zone_name, z.city, z.state "
                + "FROM addresses a JOIN zones z ON z.id = a.zone_id WHERE a.user_id = ? ORDER BY a.id DESC", id);
    }
}
