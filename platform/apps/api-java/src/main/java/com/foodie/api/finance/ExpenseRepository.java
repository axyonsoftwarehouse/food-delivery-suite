package com.foodie.api.finance;

import java.sql.Types;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ExpenseRepository {
    private final JdbcTemplate jdbc;

    public ExpenseRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> list(String from, String to) {
        return jdbc.queryForList(
            "SELECT e.id, e.category, e.description, e.amount_cents, e.incurred_at, e.created_at, u.name AS created_by_name "
                + "FROM expenses e LEFT JOIN users u ON u.id = e.created_by "
                + "WHERE (? IS NULL OR e.incurred_at >= ?) AND (? IS NULL OR e.incurred_at <= ?) ORDER BY e.incurred_at DESC, e.id DESC LIMIT 300",
            from, from, to, to);
    }

    public long sum(String from, String to) {
        Long total = jdbc.queryForObject(
            "SELECT COALESCE(SUM(amount_cents),0) FROM expenses WHERE (? IS NULL OR incurred_at >= ?) AND (? IS NULL OR incurred_at <= ?)",
            Long.class, from, from, to, to);
        return total == null ? 0L : total;
    }

    public long insert(String category, String description, long amountCents, String incurredAt, Long createdBy) {
        var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                "INSERT INTO expenses (category, description, amount_cents, incurred_at, created_by) VALUES (?, ?, ?, ?, ?)",
                java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, category);
            statement.setString(2, description);
            statement.setLong(3, amountCents);
            statement.setString(4, incurredAt);
            if (createdBy == null) statement.setNull(5, Types.BIGINT); else statement.setLong(5, createdBy);
            return statement;
        }, key);
        return key.getKey().longValue();
    }

    public int delete(long id) {
        return jdbc.update("DELETE FROM expenses WHERE id = ?", id);
    }
}
