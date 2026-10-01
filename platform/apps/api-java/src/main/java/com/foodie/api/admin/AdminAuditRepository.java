package com.foodie.api.admin;

import java.sql.Types;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminAuditRepository {
    private final JdbcTemplate jdbc;

    public AdminAuditRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Entry(long id, Long actorUserId, String actorName, String action, String entity,
                        Long entityId, String summary, String createdAt) {}

    public record SupportEntry(long id, String actorName, String action, String entity, Long entityId,
                               String summary, String reason, String createdAt) {}

    public void insert(Long actorUserId, String actorName, String action, String entity, Long entityId, String summary) {
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                "INSERT INTO admin_audit_log (actor_user_id, actor_name, action, entity, entity_id, summary) VALUES (?, ?, ?, ?, ?, ?)");
            if (actorUserId == null) statement.setNull(1, Types.BIGINT); else statement.setLong(1, actorUserId);
            statement.setString(2, actorName);
            statement.setString(3, action);
            statement.setString(4, entity);
            if (entityId == null) statement.setNull(5, Types.BIGINT); else statement.setLong(5, entityId);
            statement.setString(6, summary == null ? "" : summary);
            return statement;
        });
    }

    /** Registro de intervenção de suporte. Ao contrário de {@link #insert}, falhas propagam (rollback). */
    public void insertSupport(long actorUserId, String actorName, long restaurantId, String action, String entity,
                              Long entityId, String summary, String reason) {
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                "INSERT INTO admin_audit_log (actor_user_id, actor_name, action, entity, entity_id, summary, reason, restaurant_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
            statement.setLong(1, actorUserId);
            statement.setString(2, actorName);
            statement.setString(3, action);
            statement.setString(4, entity);
            if (entityId == null) statement.setNull(5, Types.BIGINT); else statement.setLong(5, entityId);
            statement.setString(6, summary == null ? "" : summary);
            if (reason == null) statement.setNull(7, Types.VARCHAR); else statement.setString(7, reason);
            statement.setLong(8, restaurantId);
            return statement;
        });
    }

    public List<SupportEntry> listForRestaurant(long restaurantId, Long beforeId, int limit) {
        return jdbc.query(
            """
            SELECT id, actor_name, action, entity, entity_id, summary, reason, created_at
            FROM admin_audit_log
            WHERE restaurant_id = ? AND (? IS NULL OR id < ?)
            ORDER BY id DESC
            LIMIT ?
            """,
            (rs, row) -> {
                long entityId = rs.getLong("entity_id");
                boolean entityNull = rs.wasNull();
                return new SupportEntry(
                    rs.getLong("id"),
                    rs.getString("actor_name"),
                    rs.getString("action"),
                    rs.getString("entity"),
                    entityNull ? null : entityId,
                    rs.getString("summary"),
                    rs.getString("reason"),
                    rs.getTimestamp("created_at").toInstant().toString()
                );
            },
            restaurantId, beforeId, beforeId, limit
        );
    }

    public List<Entry> list(String entity, Long actorUserId, String from, String to, Long beforeId, int limit) {
        return jdbc.query(
            """
            SELECT id, actor_user_id, actor_name, action, entity, entity_id, summary, created_at
            FROM admin_audit_log
            WHERE (? IS NULL OR entity = ?)
              AND (? IS NULL OR actor_user_id = ?)
              AND (? IS NULL OR created_at >= ?)
              AND (? IS NULL OR created_at < DATE_ADD(?, INTERVAL 1 DAY))
              AND (? IS NULL OR id < ?)
            ORDER BY id DESC
            LIMIT ?
            """,
            (rs, row) -> {
                long actor = rs.getLong("actor_user_id");
                boolean actorNull = rs.wasNull();
                long entityId = rs.getLong("entity_id");
                boolean entityNull = rs.wasNull();
                return new Entry(
                    rs.getLong("id"),
                    actorNull ? null : actor,
                    rs.getString("actor_name"),
                    rs.getString("action"),
                    rs.getString("entity"),
                    entityNull ? null : entityId,
                    rs.getString("summary"),
                    rs.getTimestamp("created_at").toInstant().toString()
                );
            },
            entity, entity, actorUserId, actorUserId, from, from, to, to, beforeId, beforeId, limit
        );
    }
}
