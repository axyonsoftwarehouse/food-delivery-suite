package com.foodie.api.orders;

import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReviewController {
    private final AuthService auth;
    private final ReviewService reviews;

    public ReviewController(AuthService auth, ReviewService reviews) {
        this.auth = auth;
        this.reviews = reviews;
    }

    @PostMapping("/orders/{id}/review")
    public Map<String, Object> create(@CookieValue(value = "foodie_session", required = false) String token,
                                      @PathVariable @Positive long id, @Valid @RequestBody ReviewRequest body) {
        User customer = auth.requireUser(token, "customer");
        return reviews.create(customer, id, body.rating(), body.comment());
    }

    @GetMapping("/orders/{id}/review")
    public Map<String, Object> forOrder(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        return reviews.forOrder(auth.requireUser(token), id);
    }

    @GetMapping("/restaurants/{id}/reviews")
    public Map<String, Object> byRestaurant(@PathVariable @Positive long id) {
        return reviews.byRestaurant(id);
    }

    public record ReviewRequest(@Min(1) @Max(5) int rating, @Size(max = 500) String comment) {}
}
