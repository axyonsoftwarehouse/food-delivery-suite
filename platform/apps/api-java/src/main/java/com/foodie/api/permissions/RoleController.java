package com.foodie.api.permissions;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RoleController {
    private final AuthService auth;
    private final PermissionService permissions;
    private final RoleService roles;

    public RoleController(AuthService auth, PermissionService permissions, RoleService roles) {
        this.auth = auth;
        this.permissions = permissions;
        this.roles = roles;
    }

    @GetMapping("/permissions")
    public List<Permissions.Descriptor> catalog(@CookieValue(value = "foodie_session", required = false) String token) {
        auth.requireUser(token);
        return Permissions.CATALOG;
    }

    @GetMapping("/me/permissions")
    public Map<String, Object> myPermissions(@CookieValue(value = "foodie_session", required = false) String token) {
        User user = auth.requireUser(token);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("role", user.role());
        result.put("permissions", permissions.effective(user).stream().sorted().toList());
        return result;
    }

    @GetMapping("/restaurant/roles")
    public List<RoleRepository.Role> list(@CookieValue(value = "foodie_session", required = false) String token) {
        return roles.list(manager(token).restaurantId());
    }

    @PostMapping("/restaurant/roles")
    public ResponseEntity<RoleRepository.Role> create(@CookieValue(value = "foodie_session", required = false) String token,
                                                       @Valid @RequestBody RoleRequest body) {
        RoleRepository.Role role = roles.create(manager(token).restaurantId(), body.name(), body.permissions());
        return ResponseEntity.status(201).body(role);
    }

    @PatchMapping("/restaurant/roles/{id}")
    public RoleRepository.Role update(@CookieValue(value = "foodie_session", required = false) String token,
                                      @PathVariable @Positive long id,
                                      @Valid @RequestBody RoleUpdateRequest body) {
        return roles.update(manager(token).restaurantId(), id, body.name(), body.permissions());
    }

    @DeleteMapping("/restaurant/roles/{id}")
    public Map<String, Boolean> delete(@CookieValue(value = "foodie_session", required = false) String token,
                                       @PathVariable @Positive long id) {
        roles.delete(manager(token).restaurantId(), id);
        return Map.of("ok", true);
    }

    @GetMapping("/restaurant/staff")
    public List<RoleRepository.Staff> staff(@CookieValue(value = "foodie_session", required = false) String token) {
        return roles.staff(manager(token).restaurantId());
    }

    @PostMapping("/restaurant/staff")
    public ResponseEntity<RoleRepository.Staff> createStaff(@CookieValue(value = "foodie_session", required = false) String token,
                                                            @Valid @RequestBody StaffRequest body) {
        RoleRepository.Staff staff = roles.createStaff(manager(token).restaurantId(), body.name(), body.email(), body.password(), body.role(), body.staffRoleId());
        return ResponseEntity.status(201).body(staff);
    }

    @PatchMapping("/restaurant/staff/{id}/role")
    public Map<String, Boolean> assignRole(@CookieValue(value = "foodie_session", required = false) String token,
                                           @PathVariable @Positive long id,
                                           @Valid @RequestBody StaffRoleRequest body) {
        roles.assignStaffRole(manager(token).restaurantId(), id, body.staffRoleId());
        return Map.of("ok", true);
    }

    private User manager(String token) {
        User user = auth.requireUser(token, "restaurant", "kitchen");
        if (user.restaurantId() == null) throw new ApiException(403, "Acesso não autorizado");
        permissions.require(user, Permissions.STAFF_MANAGE);
        return user;
    }

    public record RoleRequest(@NotBlank @Size(min = 2, max = 80) String name,
                              @NotNull @Size(max = 40) List<@NotBlank @Size(max = 60) String> permissions) {}

    public record RoleUpdateRequest(@Size(min = 2, max = 80) String name,
                                    @Size(max = 40) List<@NotBlank @Size(max = 60) String> permissions) {}

    public record StaffRequest(@NotBlank @Size(min = 2, max = 120) String name,
                               @NotBlank @Email @Size(max = 190) String email,
                               @NotBlank @Size(min = 12, max = 128) String password,
                               @NotBlank @Pattern(regexp = "restaurant|kitchen") String role,
                               @Positive Long staffRoleId) {}

    public record StaffRoleRequest(Long staffRoleId) {}
}
