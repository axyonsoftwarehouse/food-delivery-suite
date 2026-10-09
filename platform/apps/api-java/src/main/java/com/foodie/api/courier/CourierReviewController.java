package com.foodie.api.courier;

import com.foodie.api.auth.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Avaliação da entrega pelo cliente (entregador, parte C). */
@RestController
@Validated
public class CourierReviewController {
    private final AuthService auth;
    private final CourierReviewService reviews;

    public CourierReviewController(AuthService auth, CourierReviewService reviews) {
        this.auth = auth;
        this.reviews = reviews;
    }

    @PostMapping("/orders/{id}/courier-review")
    public ResponseEntity<Map<String, Object>> create(@CookieValue(value = "foodie_session", required = false) String token,
                                                      @PathVariable @Positive long id, @Valid @RequestBody CourierReviewRequest body) {
        return ResponseEntity.status(201).body(reviews.create(auth.requireUser(token, "customer"), id, body.rating(), body.comment()));
    }

    @GetMapping("/orders/{id}/courier-review")
    public Map<String, Object> forOrder(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        return reviews.forOrder(auth.requireUser(token, "customer"), id);
    }

    public record CourierReviewRequest(@Min(1) @Max(5) int rating, @Size(max = 300) String comment) {}
}
