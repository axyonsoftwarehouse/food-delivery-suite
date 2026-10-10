package com.foodie.api.courier;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.permissions.PermissionService;
import com.foodie.api.permissions.Permissions;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Turno do entregador e quadro dos entregadores para a loja (parte D). */
@RestController
public class CourierShiftController {
    private final AuthService auth;
    private final PermissionService permissions;
    private final CourierShiftService shifts;

    public CourierShiftController(AuthService auth, PermissionService permissions, CourierShiftService shifts) {
        this.auth = auth;
        this.permissions = permissions;
        this.shifts = shifts;
    }

    @GetMapping("/courier/shift")
    public Map<String, Object> current(@CookieValue(value = "foodie_session", required = false) String token) {
        return shifts.status(auth.requireUser(token, "courier").id());
    }

    @PostMapping("/courier/shift")
    public ResponseEntity<Map<String, Object>> open(@CookieValue(value = "foodie_session", required = false) String token) {
        CourierShiftService.Opened opened = shifts.open(auth.requireUser(token, "courier").id());
        return ResponseEntity.status(opened.created() ? 201 : 200).body(opened.shift());
    }

    @DeleteMapping("/courier/shift")
    public Map<String, Object> close(@CookieValue(value = "foodie_session", required = false) String token) {
        return shifts.close(auth.requireUser(token, "courier").id());
    }

    @GetMapping("/restaurant/couriers/board")
    public Map<String, Object> board(@CookieValue(value = "foodie_session", required = false) String token) {
        return shifts.board(store(token, Permissions.ORDERS_DISPATCH));
    }

    @GetMapping("/restaurant/couriers/{id}/shifts")
    public Map<String, Object> history(@CookieValue(value = "foodie_session", required = false) String token,
                                       @PathVariable @Positive long id,
                                       @RequestParam(defaultValue = "7") int days) {
        return shifts.history(store(token, Permissions.COURIERS_MANAGE), id, days);
    }

    private long store(String token, String permission) {
        User user = auth.requireUser(token, "restaurant", "kitchen");
        if (user.restaurantId() == null) throw new ApiException(403, "Acesso não autorizado");
        permissions.require(user, permission);
        return user.restaurantId();
    }
}
