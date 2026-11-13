package com.foodie.api.permissions;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodie.api.ShortCache;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class RoleRepository {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    /**
     * Papel de funcionário por usuário, consultado em toda chamada da loja que confere permissão. Guardado
     * por alguns segundos (teste de carga de 12/11); qualquer escrita de papel ou de atribuição limpa tudo.
     */
    private final ShortCache<Long, Optional<Role>> rolesByUser;

    public RoleRepository(JdbcTemplate jdbc) {
        this(jdbc, 0);
    }

    @Autowired
    public RoleRepository(JdbcTemplate jdbc, @Value("${app.auth.cache-seconds:10}") int cacheSeconds) {
        this.jdbc = jdbc;
        this.rolesByUser = new ShortCache<>(Duration.ofSeconds(Math.max(0, cacheSeconds)), 50_000);
    }

    public record Role(long id, long restaurantId, String name, List<String> permissions) {}

    public record Staff(long id, String name, String email, String role, Long staffRoleId) {}

    public List<Role> list(long restaurantId) {
        return jdbc.query(
            "SELECT id, restaurant_id, name, permissions FROM restaurant_roles WHERE restaurant_id = ? ORDER BY name",
            (rs, row) -> new Role(rs.getLong("id"), rs.getLong("restaurant_id"), rs.getString("name"), parse(rs.getString("permissions"))),
            restaurantId
        );
    }

    public Optional<Role> find(long restaurantId, long id) {
        List<Role> rows = jdbc.query(
            "SELECT id, restaurant_id, name, permissions FROM restaurant_roles WHERE id = ? AND restaurant_id = ?",
            (rs, row) -> new Role(rs.getLong("id"), rs.getLong("restaurant_id"), rs.getString("name"), parse(rs.getString("permissions"))),
            id, restaurantId
        );
        return rows.stream().findFirst();
    }

    public Optional<Role> findForUser(long userId) {
        return rolesByUser.get(userId, this::loadForUser);
    }

    private Optional<Role> loadForUser(long userId) {
        List<Role> rows = jdbc.query(
            "SELECT r.id, r.restaurant_id, r.name, r.permissions FROM users u JOIN restaurant_roles r ON r.id = u.staff_role_id WHERE u.id = ?",
            (rs, row) -> new Role(rs.getLong("id"), rs.getLong("restaurant_id"), rs.getString("name"), parse(rs.getString("permissions"))),
            userId
        );
        return rows.stream().findFirst();
    }

    public long create(long restaurantId, String name, List<String> permissions) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        String json = write(permissions);
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO restaurant_roles (restaurant_id, name, permissions) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, restaurantId);
            statement.setString(2, name);
            statement.setString(3, json);
            return statement;
        }, key);
        rolesByUser.invalidateAll();
        return key.getKey().longValue();
    }

    public int update(long restaurantId, long id, String name, List<String> permissions) {
        int changed = jdbc.update(
            "UPDATE restaurant_roles SET name = ?, permissions = ? WHERE id = ? AND restaurant_id = ?",
            name, write(permissions), id, restaurantId
        );
        rolesByUser.invalidateAll();
        return changed;
    }

    public boolean assigned(long id) {
        Integer found = jdbc.query("SELECT 1 FROM users WHERE staff_role_id = ? LIMIT 1", rs -> rs.next() ? 1 : null, id);
        return found != null;
    }

    public int delete(long restaurantId, long id) {
        int changed = jdbc.update("DELETE FROM restaurant_roles WHERE id = ? AND restaurant_id = ?", id, restaurantId);
        rolesByUser.invalidateAll();
        return changed;
    }

    public List<Staff> staff(long restaurantId) {
        return jdbc.query(
            "SELECT id, name, email, role, staff_role_id FROM users WHERE restaurant_id = ? AND role IN ('restaurant','kitchen') ORDER BY name",
            (rs, row) -> {
                long staffRole = rs.getLong("staff_role_id");
                Long assignedRole = rs.wasNull() ? null : staffRole;
                return new Staff(rs.getLong("id"), rs.getString("name"), rs.getString("email"), rs.getString("role"), assignedRole);
            },
            restaurantId
        );
    }

    public boolean emailExists(String email) {
        Integer found = jdbc.query("SELECT 1 FROM users WHERE email = ? LIMIT 1", rs -> rs.next() ? 1 : null, email);
        return found != null;
    }

    public long createStaff(long restaurantId, String name, String email, String passwordHash, String role, Long staffRoleId) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO users (name, email, password_hash, role, restaurant_id, staff_role_id) VALUES (?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, name);
            statement.setString(2, email);
            statement.setString(3, passwordHash);
            statement.setString(4, role);
            statement.setLong(5, restaurantId);
            if (staffRoleId == null) statement.setNull(6, java.sql.Types.BIGINT); else statement.setLong(6, staffRoleId);
            return statement;
        }, key);
        rolesByUser.invalidateAll();
        return key.getKey().longValue();
    }

    public int assignStaffRole(long restaurantId, long userId, Long staffRoleId) {
        int changed = staffRoleId == null
            // NULL identifica o dono no acesso restaurant. Só a cozinha pode usar o papel padrão.
            ? jdbc.update("UPDATE users SET staff_role_id = NULL WHERE id = ? AND restaurant_id = ? AND role = 'kitchen'", userId, restaurantId)
            : jdbc.update("UPDATE users SET staff_role_id = ? WHERE id = ? AND restaurant_id = ? AND (role = 'kitchen' OR (role = 'restaurant' AND staff_role_id IS NOT NULL))", staffRoleId, userId, restaurantId);
        rolesByUser.invalidateAll();
        return changed;
    }


    private static List<String> parse(String json) {
        try {
            return JSON.readValue(json, STRING_LIST);
        } catch (Exception error) {
            return List.of();
        }
    }

    private static String write(List<String> permissions) {
        try {
            return JSON.writeValueAsString(permissions);
        } catch (Exception error) {
            return "[]";
        }
    }
}
