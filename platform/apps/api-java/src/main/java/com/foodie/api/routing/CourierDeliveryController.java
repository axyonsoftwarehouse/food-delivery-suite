package com.foodie.api.routing;

import com.foodie.api.auth.AuthService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Área do entregador (parte A): entregas em andamento e histórico do próprio entregador. */
@RestController
@RequestMapping("/courier")
public class CourierDeliveryController {
    private final AuthService auth;
    private final CourierDeliveryService deliveries;

    public CourierDeliveryController(AuthService auth, CourierDeliveryService deliveries) {
        this.auth = auth;
        this.deliveries = deliveries;
    }

    @GetMapping("/deliveries/active")
    public List<Map<String, Object>> active(@CookieValue(value = "foodie_session", required = false) String token) {
        return deliveries.active(auth.requireUser(token, "courier").id());
    }

    @GetMapping("/profile")
    public Map<String, Object> profile(@CookieValue(value = "foodie_session", required = false) String token) {
        return deliveries.profile(auth.requireUser(token, "courier").id());
    }

    @GetMapping("/deliveries/history")
    public List<Map<String, Object>> history(@CookieValue(value = "foodie_session", required = false) String token,
                                             @RequestParam(required = false) String period) {
        return deliveries.history(auth.requireUser(token, "courier").id(), period);
    }
}
