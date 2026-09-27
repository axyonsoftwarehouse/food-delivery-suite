package com.foodie.api.admin;

import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.constraints.Positive;

/** Papéis, funcionários e trilha administrativa (E01). */
@RestController
@RequestMapping("/admin")
public class AdminAccessController {
    private final AuthService auth;
    private final AdminPermissionService permissions;
    private final AdminAccessService access;
    private final AdminAuditService audit;

    public AdminAccessController(AuthService auth, AdminPermissionService permissions, AdminAccessService access, AdminAuditService audit) {
        this.auth = auth;
        this.permissions = permissions;
        this.access = access;
        this.audit = audit;
    }

    @GetMapping("/permissions")
    public List<AdminPermissions.Descriptor> catalog(@CookieValue(value = "foodie_session", required = false) String token) {
        require(token, AdminPermissions.ADMIN_MANAGE);
        return AdminPermissions.CATALOG;
    }

    @GetMapping("/roles")
    public List<AdminRoleRepository.Role> roles(@CookieValue(value = "foodie_session", required = false) String token) {
        require(token, AdminPermissions.ADMIN_MANAGE);
        return access.listRoles();
    }

    @PostMapping("/roles")
    public ResponseEntity<AdminRoleRepository.Role> createRole(@CookieValue(value = "foodie_session", required = false) String token,
                                                                @Valid @RequestBody RoleCreateRequest body) {
        User actor = require(token, AdminPermissions.ADMIN_MANAGE);
        AdminRoleRepository.Role role = access.createRole(body.name(), body.description(), body.permissions(), body.active());
        audit.record(actor, "create", "admin_role", role.id(), "Papel " + role.name());
        return ResponseEntity.status(201).body(role);
    }

    @PatchMapping("/roles/{id}")
    public AdminRoleRepository.Role updateRole(@CookieValue(value = "foodie_session", required = false) String token,
                                               @PathVariable @Positive long id,
                                               @Valid @RequestBody RoleUpdateRequest body) {
        User actor = require(token, AdminPermissions.ADMIN_MANAGE);
        AdminRoleRepository.Role role = access.updateRole(id, body.name(), body.description(), body.permissions(), body.active());
        audit.record(actor, "update", "admin_role", id, "Papel " + role.name());
        return role;
    }

    @DeleteMapping("/roles/{id}")
    public Map<String, Boolean> deleteRole(@CookieValue(value = "foodie_session", required = false) String token,
                                           @PathVariable @Positive long id) {
        User actor = require(token, AdminPermissions.ADMIN_MANAGE);
        access.deleteRole(id);
        audit.record(actor, "delete", "admin_role", id, "Papel removido");
        return Map.of("ok", true);
    }

    @GetMapping("/employees")
    public List<AdminRoleRepository.Employee> employees(@CookieValue(value = "foodie_session", required = false) String token) {
        require(token, AdminPermissions.ADMIN_MANAGE);
        return access.listEmployees();
    }

    @PostMapping("/employees")
    public ResponseEntity<AdminRoleRepository.Employee> createEmployee(@CookieValue(value = "foodie_session", required = false) String token,
                                                                        @Valid @RequestBody EmployeeCreateRequest body) {
        User actor = require(token, AdminPermissions.ADMIN_MANAGE);
        AdminRoleRepository.Employee employee = access.createEmployee(body.name(), body.email(), body.password(), body.adminRoleId());
        audit.record(actor, "create", "admin_employee", employee.id(), "Funcionário " + employee.name());
        return ResponseEntity.status(201).body(employee);
    }

    @PatchMapping("/employees/{id}")
    public AdminRoleRepository.Employee updateEmployee(@CookieValue(value = "foodie_session", required = false) String token,
                                                       @PathVariable @Positive long id,
                                                       @Valid @RequestBody EmployeeUpdateRequest body) {
        User actor = require(token, AdminPermissions.ADMIN_MANAGE);
        AdminRoleRepository.Employee employee = access.updateEmployee(id, body.name(), body.adminRoleId());
        audit.record(actor, "update", "admin_employee", id, "Funcionário " + employee.name());
        return employee;
    }

    @GetMapping("/audit")
    public Map<String, Object> auditLog(@CookieValue(value = "foodie_session", required = false) String token,
                                        @RequestParam(required = false) String entity,
                                        @RequestParam(required = false) Long actor,
                                        @RequestParam(required = false) String from,
                                        @RequestParam(required = false) String to,
                                        @RequestParam(required = false) Long before,
                                        @RequestParam(required = false) @Min(1) @Max(200) Integer limit) {
        require(token, AdminPermissions.AUDIT_VIEW);
        int size = limit == null ? 50 : limit;
        List<AdminAuditRepository.Entry> entries = audit.list(entity, actor, from, to, before, size);
        Long nextCursor = !entries.isEmpty() && entries.size() == size ? entries.get(entries.size() - 1).id() : null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", entries);
        result.put("nextCursor", nextCursor);
        return result;
    }

    private User require(String token, String permission) {
        User user = auth.requireUser(token, "admin");
        permissions.require(user, permission);
        return user;
    }

    public record RoleCreateRequest(@NotBlank @Size(min = 2, max = 80) String name,
                                    @Size(max = 255) String description,
                                    @Size(max = 40) List<String> permissions,
                                    Boolean active) {}

    public record RoleUpdateRequest(@Size(min = 2, max = 80) String name,
                                    @Size(max = 255) String description,
                                    @Size(max = 40) List<String> permissions,
                                    Boolean active) {}

    public record EmployeeCreateRequest(@NotBlank @Size(min = 2, max = 120) String name,
                                        @NotBlank @Email @Size(max = 190) String email,
                                        @NotBlank @Size(min = 12, max = 128) String password,
                                        @Positive Long adminRoleId) {}

    public record EmployeeUpdateRequest(@Size(min = 2, max = 120) String name,
                                        @Positive Long adminRoleId) {}
}
