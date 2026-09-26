package com.foodie.api.pos;

import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.permissions.PermissionService;
import com.foodie.api.permissions.Permissions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PosController {
    private final AuthService auth;
    private final PosService pos;
    private final PermissionService permissions;

    public PosController(AuthService auth, PosService pos, PermissionService permissions) {
        this.auth = auth;
        this.pos = pos;
        this.permissions = permissions;
    }

    @GetMapping("/pos/customers")
    public List<Map<String, Object>> customers(@CookieValue(value = "foodie_session", required = false) String token,
                                               @RequestParam(required = false) String search) {
        operator(token);
        return pos.customers(search);
    }

    @PostMapping("/pos/orders")
    public ResponseEntity<Map<String, Object>> create(@CookieValue(value = "foodie_session", required = false) String token,
                                                      @Valid @RequestBody PosRequest body) {
        return ResponseEntity.status(201).body(pos.createOrder(operator(token), body));
    }

    private User operator(String token) {
        User user = auth.requireUser(token, "restaurant");
        permissions.require(user, Permissions.POS_MANAGE);
        return user;
    }

    public record PosItem(@Positive long productId,
                          @Positive Long variationId,
                          @Positive @Max(20) int quantity,
                          @Size(max = 20) List<@Positive Long> addonIds) {}

    public record PosRequest(@NotEmpty @Size(max = 50) List<@Valid PosItem> items,
                             @NotBlank @Pattern(regexp = "cash|card|pix") String paymentMethod,
                             @Min(0) @Max(100_000_000) Integer changeForCents,
                             @Pattern(regexp = "take_away|dine_in") String orderType,
                             @Positive Long tableId,
                             @Min(1) @Max(50) Integer partySize,
                             @Positive Long customerId) {}
}
