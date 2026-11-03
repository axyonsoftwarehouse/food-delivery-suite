package com.foodie.api.admin;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Gestão de restaurantes: aprovação e exportação (E08). A operação da loja é do modo suporte (E48). */
@RestController
@RequestMapping("/admin/restaurants")
public class RestaurantAdminController {
    private final AuthService auth;
    private final AdminPermissionService permissions;
    private final AdminAuditService audit;
    private final JdbcTemplate jdbc;

    public RestaurantAdminController(AuthService auth, AdminPermissionService permissions, AdminAuditService audit, JdbcTemplate jdbc) {
        this.auth = auth;
        this.permissions = permissions;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @PatchMapping("/{id}/approval")
    public Map<String, Object> approval(@CookieValue(value = "foodie_session", required = false) String token,
                                        @PathVariable @Positive long id,
                                        @Valid @RequestBody ApprovalRequest body) {
        User actor = admin(token);
        if (jdbc.update("UPDATE restaurants SET approval = ? WHERE id = ?", body.approval(), id) == 0) {
            throw new ApiException(404, "Restaurante não encontrado");
        }
        audit.record(actor, "update", "restaurant", id, "Aprovação: " + body.approval());
        return Map.of("id", id, "approval", body.approval());
    }

    @GetMapping("/export")
    public ResponseEntity<String> export(@CookieValue(value = "foodie_session", required = false) String token) {
        admin(token);
        StringBuilder csv = new StringBuilder("id,nome,slug,aprovacao,ativo\n");
        for (Map<String, Object> row : jdbc.queryForList("SELECT id, name, slug, approval, active FROM restaurants ORDER BY name")) {
            csv.append(row.get("id")).append(',')
                .append(csvText(row.get("name"))).append(',')
                .append(csvText(row.get("slug"))).append(',')
                .append(csvText(row.get("approval"))).append(',')
                .append(Boolean.TRUE.equals(row.get("active")) ? "sim" : "nao").append('\n');
        }
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"restaurantes.csv\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(csv.toString());
    }

    private User admin(String token) {
        User user = auth.requireUser(token, "admin");
        permissions.require(user, AdminPermissions.RESTAURANTS_MANAGE);
        return user;
    }

    private static String csvText(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) text = "'" + text;
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) return '"' + text.replace("\"", "\"\"") + '"';
        return text;
    }

    public record ApprovalRequest(@NotNull @Pattern(regexp = "approved|pending|denied") String approval) {}
}
