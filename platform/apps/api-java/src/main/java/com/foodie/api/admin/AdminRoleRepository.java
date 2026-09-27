package com.foodie.api.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class AdminRoleRepository {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final JdbcTemplate jdbc;

    public AdminRoleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Role(long id, String name, String description, List<String> permissions, boolean active) {}

    public record Employee(long id, String name, String email, Long adminRoleId, boolean suspended) {}

    public List<Role> listRoles() {
        return jdbc.query(
            "SELECT id, name, description, permissions, active FROM admin_roles ORDER BY name",
            (rs, row) -> mapRole(rs.getLong("id"), rs.getString("name"), rs.getString("description"), rs.getString("permissions"), rs.getBoolean("active"))
        );
    }

    public Optional<Role> findRole(long id) {
        return jdbc.query(
            "SELECT id, name, description, permissions, active FROM admin_roles WHERE id = ?",
            (rs, row) -> mapRole(rs.getLong("id"), rs.getString("name"), rs.getString("description"), rs.getString("permissions"), rs.getBoolean("active")),
            id
        ).stream().findFirst();
    }

    public Optional<Role> findRoleForUser(long userId) {
        return jdbc.query(
            "SELECT r.id, r.name, r.description, r.permissions, r.active FROM users u JOIN admin_roles r ON r.id = u.admin_role_id WHERE u.id = ?",
            (rs, row) -> mapRole(rs.getLong("id"), rs.getString("name"), rs.getString("description"), rs.getString("permissions"), rs.getBoolean("active")),
            userId
        ).stream().findFirst();
    }

    public boolean roleNameExists(String name) {
        Integer found = jdbc.query("SELECT 1 FROM admin_roles WHERE name = ? LIMIT 1", rs -> rs.next() ? 1 : null, name);
        return found != null;
    }

    public long createRole(String name, String description, List<String> permissions, boolean active) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        String json = write(permissions);
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO admin_roles (name, description, permissions, active) VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, name);
            statement.setString(2, description);
            statement.setString(3, json);
            statement.setBoolean(4, active);
            return statement;
        }, key);
        return key.getKey().longValue();
    }

    public int updateRole(long id, String name, String description, List<String> permissions, boolean active) {
        return jdbc.update(
            "UPDATE admin_roles SET name = ?, description = ?, permissions = ?, active = ? WHERE id = ?",
            name, description, write(permissions), active, id
        );
    }

    public boolean roleAssigned(long id) {
        Integer found = jdbc.query("SELECT 1 FROM users WHERE admin_role_id = ? LIMIT 1", rs -> rs.next() ? 1 : null, id);
        return found != null;
    }

    public int deleteRole(long id) {
        return jdbc.update("DELETE FROM admin_roles WHERE id = ?", id);
    }

    public List<Employee> listEmployees() {
        return jdbc.query(
            "SELECT id, name, email, admin_role_id, suspended_at IS NOT NULL AS suspended FROM users WHERE role = 'admin' ORDER BY name",
            (rs, row) -> {
                long roleId = rs.getLong("admin_role_id");
                return new Employee(rs.getLong("id"), rs.getString("name"), rs.getString("email"), rs.wasNull() ? null : roleId, rs.getBoolean("suspended"));
            }
        );
    }

    public Optional<Employee> findEmployee(long id) {
        return jdbc.query(
            "SELECT id, name, email, admin_role_id, suspended_at IS NOT NULL AS suspended FROM users WHERE id = ? AND role = 'admin'",
            (rs, row) -> {
                long roleId = rs.getLong("admin_role_id");
                return new Employee(rs.getLong("id"), rs.getString("name"), rs.getString("email"), rs.wasNull() ? null : roleId, rs.getBoolean("suspended"));
            },
            id
        ).stream().findFirst();
    }

    public boolean emailExists(String email) {
        Integer found = jdbc.query("SELECT 1 FROM users WHERE email = ? LIMIT 1", rs -> rs.next() ? 1 : null, email);
        return found != null;
    }

    public long createEmployee(String name, String email, String passwordHash, Long adminRoleId) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO users (name, email, password_hash, role, restaurant_id, admin_role_id) VALUES (?, ?, ?, 'admin', NULL, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, name);
            statement.setString(2, email);
            statement.setString(3, passwordHash);
            if (adminRoleId == null) statement.setNull(4, Types.BIGINT); else statement.setLong(4, adminRoleId);
            return statement;
        }, key);
        return key.getKey().longValue();
    }

    public int updateEmployee(long id, String name, Long adminRoleId) {
        if (adminRoleId == null) {
            return jdbc.update("UPDATE users SET name = ?, admin_role_id = NULL WHERE id = ? AND role = 'admin'", name, id);
        }
        return jdbc.update("UPDATE users SET name = ?, admin_role_id = ? WHERE id = ? AND role = 'admin'", name, adminRoleId, id);
    }

    /** Administradores ativos e sem papel restrito (acesso total). */
    public int countFullAdmins() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM users WHERE role = 'admin' AND suspended_at IS NULL AND admin_role_id IS NULL",
            Integer.class
        );
        return count == null ? 0 : count;
    }

    public int countActiveAdmins() {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM users WHERE role = 'admin' AND suspended_at IS NULL",
            Integer.class
        );
        return count == null ? 0 : count;
    }

    private static Role mapRole(long id, String name, String description, String permissions, boolean active) {
        return new Role(id, name, description, parse(permissions), active);
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
