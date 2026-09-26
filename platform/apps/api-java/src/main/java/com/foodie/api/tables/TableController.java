package com.foodie.api.tables;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.permissions.PermissionService;
import com.foodie.api.permissions.Permissions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
public class TableController {
    private final AuthService auth;
    private final TableService tables;
    private final PermissionService permissions;

    public TableController(AuthService auth, TableService tables, PermissionService permissions) {
        this.auth = auth;
        this.tables = tables;
        this.permissions = permissions;
    }

    @GetMapping("/restaurant/tables")
    public List<Map<String, Object>> list(@CookieValue(value = "foodie_session", required = false) String token) {
        return tables.list(manager(token).restaurantId());
    }

    @GetMapping("/restaurants/{id}/tables")
    public List<Map<String, Object>> publicTables(@PathVariable @Min(1) long id) {
        return tables.listActive(id);
    }

    @PostMapping("/restaurant/tables")
    public ResponseEntity<Map<String, Object>> create(@CookieValue(value = "foodie_session", required = false) String token,
                                                       @Valid @RequestBody TableRequest body) {
        return ResponseEntity.status(201).body(tables.create(manager(token).restaurantId(), body.number(), body.capacity()));
    }

    @PatchMapping("/restaurant/tables/{id}")
    public Map<String, Object> update(@CookieValue(value = "foodie_session", required = false) String token,
                                      @PathVariable @Min(1) long id,
                                      @Valid @RequestBody TableUpdateRequest body) {
        return tables.update(manager(token).restaurantId(), id, body.number(), body.capacity(), body.active());
    }

    @DeleteMapping("/restaurant/tables/{id}")
    public Map<String, Boolean> delete(@CookieValue(value = "foodie_session", required = false) String token,
                                       @PathVariable @Min(1) long id) {
        tables.delete(manager(token).restaurantId(), id);
        return Map.of("ok", true);
    }

    @GetMapping("/restaurant/tables/{id}/session")
    public Map<String, Object> session(@CookieValue(value = "foodie_session", required = false) String token,
                                       @PathVariable @Min(1) long id) {
        return tables.session(manager(token).restaurantId(), id);
    }

    @PostMapping("/restaurant/tables/{id}/session/close")
    public Map<String, Object> closeSession(@CookieValue(value = "foodie_session", required = false) String token,
                                            @PathVariable @Min(1) long id) {
        return tables.closeSession(manager(token).restaurantId(), id);
    }

    private User manager(String token) {
        User user = auth.requireUser(token, "restaurant");
        if (user.restaurantId() == null) throw new ApiException(403, "Acesso não autorizado");
        permissions.require(user, Permissions.TABLES_MANAGE);
        return user;
    }

    public record TableRequest(@NotBlank @Size(max = 20) String number, @Min(1) @Max(200) int capacity) {}

    public record TableUpdateRequest(@Size(max = 20) String number, @Min(1) @Max(200) Integer capacity, Boolean active) {}
}
