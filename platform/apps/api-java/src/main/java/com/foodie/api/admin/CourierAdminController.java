package com.foodie.api.admin;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.courier.CourierShiftService;
import com.foodie.api.support.SupportActionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Gestão de entregadores: veículo, turnos, incentivos e avaliações (E09). */
@RestController
@RequestMapping("/admin/couriers")
public class CourierAdminController {
    private final AuthService auth;
    private final AdminPermissionService permissions;
    private final AdminAuditService audit;
    private final SupportActionService support;
    private final JdbcTemplate jdbc;
    private final CourierShiftService shifts;

    public CourierAdminController(AuthService auth, AdminPermissionService permissions, AdminAuditService audit, SupportActionService support, JdbcTemplate jdbc,
                                  CourierShiftService shifts) {
        this.shifts = shifts;
        this.auth = auth;
        this.permissions = permissions;
        this.audit = audit;
        this.support = support;
        this.jdbc = jdbc;
    }

    /**
     * Liga a uma loja o entregador que ficou sem loja na migração V061 (decisão de 08/10/2026: o entregador
     * é exclusivo de uma loja). Só liga quem está sem loja: mover de loja com entregas em andamento quebraria
     * o vínculo. É intervenção de suporte: exige motivo e vai para a trilha da loja.
     */
    @PatchMapping("/{id}/restaurant")
    public Map<String, Boolean> linkToRestaurant(@CookieValue(value = "foodie_session", required = false) String token,
                                                 @PathVariable @Positive long id,
                                                 @Valid @RequestBody LinkRequest body) {
        User actor = admin(token);
        permissions.require(actor, AdminPermissions.SUPPORT_ACT);
        requireCourier(id);
        return support.act(actor, body.restaurantId(), "courier.link", "courier", id, "Entregador #" + id + " ligado à loja", body.reason(), () -> {
            int changed = jdbc.update("UPDATE users SET restaurant_id = ? WHERE id = ? AND role = 'courier' AND restaurant_id IS NULL", body.restaurantId(), id);
            if (changed == 0) throw new ApiException(409, "Este entregador já pertence a uma loja");
            return Map.of("ok", true);
        });
    }

    @GetMapping("/{id}/profile")
    public Map<String, Object> profile(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        admin(token);
        requireCourier(id);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT vehicle_type, vehicle_plate, extra_fee_cents FROM courier_profiles WHERE user_id = ?", id);
        if (rows.isEmpty()) return Map.of("userId", id, "vehicleType", "moto", "vehiclePlate", "", "extraFeeCents", 0);
        return rows.getFirst();
    }

    @PatchMapping("/{id}/profile")
    public Map<String, Object> updateProfile(@CookieValue(value = "foodie_session", required = false) String token,
                                             @PathVariable @Positive long id,
                                             @Valid @RequestBody ProfileRequest body) {
        User actor = admin(token);
        requireCourier(id);
        jdbc.update("INSERT INTO courier_profiles (user_id, vehicle_type, vehicle_plate, extra_fee_cents) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE vehicle_type = VALUES(vehicle_type), vehicle_plate = VALUES(vehicle_plate), extra_fee_cents = VALUES(extra_fee_cents)",
            id, body.vehicleType(), body.vehiclePlate() == null ? "" : body.vehiclePlate().strip(), body.extraFeeCents() == null ? 0 : body.extraFeeCents());
        audit.record(actor, "update", "courier", id, "Veículo " + body.vehicleType());
        return profile(token, id);
    }

    @GetMapping("/{id}/shifts")
    public List<Map<String, Object>> shifts(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        admin(token);
        return jdbc.queryForList("SELECT id, started_at, ended_at FROM courier_shifts WHERE courier_id = ? ORDER BY id DESC LIMIT 100", id);
    }

    @PostMapping("/{id}/shifts")
    public ResponseEntity<Map<String, Object>> startShift(@CookieValue(value = "foodie_session", required = false) String token,
                                                          @PathVariable @Positive long id) {
        User actor = admin(token);
        requireCourier(id);
        // Grava a loja do entregador e respeita o turno único (parte D): com turno aberto, devolve o existente.
        CourierShiftService.SupportShift shift = shifts.openBySupport(id);
        if (shift.created()) audit.record(actor, "create", "courier_shift", shift.id(), "Turno iniciado");
        return ResponseEntity.status(shift.created() ? 201 : 200).body(Map.of("id", shift.id()));
    }

    @PatchMapping("/{id}/shifts/{shiftId}/end")
    public Map<String, Boolean> endShift(@CookieValue(value = "foodie_session", required = false) String token,
                                         @PathVariable @Positive long id, @PathVariable @Positive long shiftId) {
        admin(token);
        if (jdbc.update("UPDATE courier_shifts SET ended_at = NOW() WHERE id = ? AND courier_id = ? AND ended_at IS NULL", shiftId, id) == 0) {
            throw new ApiException(404, "Turno aberto não encontrado");
        }
        return Map.of("ok", true);
    }

    @GetMapping("/{id}/incentives")
    public List<Map<String, Object>> incentives(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        admin(token);
        return jdbc.queryForList("SELECT id, description, amount_cents, active, created_at FROM courier_incentives WHERE courier_id = ? ORDER BY id DESC", id);
    }

    @PostMapping("/{id}/incentives")
    public ResponseEntity<Map<String, Object>> addIncentive(@CookieValue(value = "foodie_session", required = false) String token,
                                                            @PathVariable @Positive long id,
                                                            @Valid @RequestBody IncentiveRequest body) {
        User actor = admin(token);
        requireCourier(id);
        jdbc.update("INSERT INTO courier_incentives (courier_id, description, amount_cents) VALUES (?, ?, ?)", id, body.description().strip(), body.amountCents());
        audit.record(actor, "create", "courier_incentive", id, "Incentivo " + body.amountCents());
        return ResponseEntity.status(201).body(Map.of("ok", true));
    }

    @DeleteMapping("/{id}/incentives/{incentiveId}")
    public Map<String, Boolean> deleteIncentive(@CookieValue(value = "foodie_session", required = false) String token,
                                                @PathVariable @Positive long id, @PathVariable @Positive long incentiveId) {
        admin(token);
        jdbc.update("DELETE FROM courier_incentives WHERE id = ? AND courier_id = ?", incentiveId, id);
        return Map.of("ok", true);
    }

    @GetMapping("/{id}/reviews")
    public List<Map<String, Object>> reviews(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        admin(token);
        return jdbc.queryForList(
            "SELECT rv.id, rv.rating, rv.comment, rv.created_at, o.id AS order_id FROM reviews rv "
                + "JOIN orders o ON o.id = rv.order_id WHERE o.courier_id = ? ORDER BY rv.id DESC LIMIT 100", id);
    }

    @GetMapping("/export")
    public ResponseEntity<String> export(@CookieValue(value = "foodie_session", required = false) String token) {
        admin(token);
        StringBuilder csv = new StringBuilder("id,nome,email,aprovado,suspenso,veiculo\n");
        for (Map<String, Object> row : jdbc.queryForList(
            "SELECT u.id, u.name, u.email, u.courier_approved_at IS NOT NULL AS approved, u.suspended_at IS NOT NULL AS suspended, "
                + "COALESCE(cp.vehicle_type, '') AS vehicle_type FROM users u LEFT JOIN courier_profiles cp ON cp.user_id = u.id "
                + "WHERE u.role = 'courier' ORDER BY u.name")) {
            csv.append(row.get("id")).append(',')
                .append(csvText(row.get("name"))).append(',')
                .append(csvText(row.get("email"))).append(',')
                .append(Boolean.TRUE.equals(row.get("approved")) ? "sim" : "nao").append(',')
                .append(Boolean.TRUE.equals(row.get("suspended")) ? "sim" : "nao").append(',')
                .append(csvText(row.get("vehicle_type"))).append('\n');
        }
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"entregadores.csv\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(csv.toString());
    }

    private void requireCourier(long id) {
        Integer exists = jdbc.query("SELECT 1 FROM users WHERE id = ? AND role = 'courier'", rs -> rs.next() ? 1 : null, id);
        if (exists == null) throw new ApiException(404, "Entregador não encontrado");
    }

    private User admin(String token) {
        User user = auth.requireUser(token, "admin");
        permissions.require(user, AdminPermissions.COURIERS_MANAGE);
        return user;
    }

    private static String csvText(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) return '"' + text.replace("\"", "\"\"") + '"';
        return text;
    }

    public record ProfileRequest(@NotBlank @Pattern(regexp = "moto|bike|carro|van|a_pe") String vehicleType,
                                 @Size(max = 20) String vehiclePlate,
                                 @Min(0) @Max(100_000) Integer extraFeeCents) {}
    public record LinkRequest(@Positive long restaurantId, @NotBlank @Size(max = 500) String reason) {}
    public record IncentiveRequest(@NotBlank @Size(min = 2, max = 255) String description,
                                   @Positive @Max(10_000_000) long amountCents) {}
}
