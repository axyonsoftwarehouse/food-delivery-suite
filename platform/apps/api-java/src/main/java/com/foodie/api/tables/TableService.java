package com.foodie.api.tables;

import com.foodie.api.ApiException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Mesas do salão, por restaurante, para pedidos de consumo no local (dine-in). */
@Service
public class TableService {
    private final JdbcTemplate jdbc;

    public TableService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> list(long restaurantId) {
        return jdbc.queryForList(
            "SELECT id, restaurant_id, number, capacity, active FROM restaurant_tables WHERE restaurant_id = ? ORDER BY number",
            restaurantId
        );
    }

    @Transactional
    public Map<String, Object> create(long restaurantId, String number, int capacity) {
        String clean = cleanNumber(number);
        validateCapacity(capacity);
        Integer duplicate = jdbc.query("SELECT 1 FROM restaurant_tables WHERE restaurant_id = ? AND number = ?",
            rs -> rs.next() ? 1 : null, restaurantId, clean);
        if (duplicate != null) throw new ApiException(409, "Já existe uma mesa com este número");
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO restaurant_tables (restaurant_id, number, capacity) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, restaurantId);
            statement.setString(2, clean);
            statement.setInt(3, capacity);
            return statement;
        }, key);
        return find(restaurantId, key.getKey().longValue());
    }

    @Transactional
    public Map<String, Object> update(long restaurantId, long id, String number, Integer capacity, Boolean active) {
        Map<String, Object> current = find(restaurantId, id);
        String clean = number == null ? String.valueOf(current.get("number")) : cleanNumber(number);
        int seats = capacity == null ? ((Number) current.get("capacity")).intValue() : capacity;
        validateCapacity(seats);
        boolean isActive = active == null ? Boolean.TRUE.equals(current.get("active")) : active;
        if (!clean.equals(String.valueOf(current.get("number")))) {
            Integer duplicate = jdbc.query("SELECT 1 FROM restaurant_tables WHERE restaurant_id = ? AND number = ? AND id <> ?",
                rs -> rs.next() ? 1 : null, restaurantId, clean, id);
            if (duplicate != null) throw new ApiException(409, "Já existe uma mesa com este número");
        }
        jdbc.update("UPDATE restaurant_tables SET number = ?, capacity = ?, active = ? WHERE id = ? AND restaurant_id = ?",
            clean, seats, isActive, id, restaurantId);
        return find(restaurantId, id);
    }

    @Transactional
    public void delete(long restaurantId, long id) {
        find(restaurantId, id);
        Integer used = jdbc.query("SELECT 1 FROM orders WHERE table_id = ? LIMIT 1", rs -> rs.next() ? 1 : null, id);
        if (used != null) throw new ApiException(409, "Esta mesa já foi usada em pedidos");
        jdbc.update("DELETE FROM restaurant_tables WHERE id = ? AND restaurant_id = ?", id, restaurantId);
    }

    public Map<String, Object> find(long restaurantId, long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, restaurant_id, number, capacity, active FROM restaurant_tables WHERE id = ? AND restaurant_id = ?",
            id, restaurantId
        );
        if (rows.isEmpty()) throw new ApiException(404, "Mesa não encontrada");
        return rows.getFirst();
    }

    public Map<String, Object> session(long restaurantId, long tableId) {
        find(restaurantId, tableId);
        List<Map<String, Object>> sessions = jdbc.queryForList(
            "SELECT id, table_id, status, opened_at FROM table_sessions WHERE table_id = ? AND restaurant_id = ? AND status = 'open' ORDER BY id DESC LIMIT 1",
            tableId, restaurantId
        );
        if (sessions.isEmpty()) return Map.of("open", false);
        Map<String, Object> session = new java.util.LinkedHashMap<>(sessions.getFirst());
        session.put("open", true);
        session.put("orders", jdbc.queryForList(
            "SELECT id, status, total_cents, order_type, party_size, created_at FROM orders WHERE table_session_id = ? AND status NOT IN ('cancelled','rejected','expired','failed') ORDER BY id",
            session.get("id")));
        Long total = jdbc.queryForObject(
            "SELECT COALESCE(SUM(total_cents),0) FROM orders WHERE table_session_id = ? AND status NOT IN ('cancelled','rejected','expired','failed')",
            Long.class, session.get("id"));
        session.put("totalCents", total == null ? 0L : total);
        return session;
    }

    @Transactional
    public Map<String, Object> closeSession(long restaurantId, long tableId) {
        find(restaurantId, tableId);
        int changed = jdbc.update(
            "UPDATE table_sessions SET status = 'closed', closed_at = NOW() WHERE table_id = ? AND restaurant_id = ? AND status = 'open'",
            tableId, restaurantId);
        if (changed == 0) throw new ApiException(409, "Não há comanda aberta nesta mesa");
        return Map.of("ok", true);
    }

    private static String cleanNumber(String number) {
        String clean = number == null ? "" : number.strip();
        if (clean.isEmpty() || clean.length() > 20) throw new ApiException(400, "Número da mesa inválido");
        return clean;
    }

    private static void validateCapacity(int capacity) {
        if (capacity < 1 || capacity > 200) throw new ApiException(400, "Capacidade inválida");
    }
}
