package com.foodie.api.courier;

import com.foodie.api.auth.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Map;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Meta semanal do próprio entregador (parte C). */
@RestController
@RequestMapping("/courier/goal")
public class CourierGoalController {
    private final AuthService auth;
    private final CourierGoalService goals;

    public CourierGoalController(AuthService auth, CourierGoalService goals) {
        this.auth = auth;
        this.goals = goals;
    }

    @GetMapping
    public Map<String, Object> get(@CookieValue(value = "foodie_session", required = false) String token) {
        return goals.get(auth.requireUser(token, "courier").id());
    }

    @PutMapping
    public Map<String, Object> set(@CookieValue(value = "foodie_session", required = false) String token, @Valid @RequestBody GoalRequest body) {
        return goals.set(auth.requireUser(token, "courier").id(), body.weeklyDeliveries());
    }

    public record GoalRequest(@Min(1) @Max(200) Integer weeklyDeliveries) {}
}
