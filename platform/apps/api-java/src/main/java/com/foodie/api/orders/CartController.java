package com.foodie.api.orders;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.EmailVerificationGuard;
import com.foodie.api.auth.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cart")
public class CartController {
    private final AuthService auth;
    private final CartService cart;
    private final EmailVerificationGuard verification;

    public CartController(AuthService auth, CartService cart, EmailVerificationGuard verification) {
        this.auth = auth;
        this.cart = cart;
        this.verification = verification;
    }

    @GetMapping
    public CartService.CartSnapshot get(@CookieValue(value = "foodie_session", required = false) String token) {
        return cart.get(customer(token));
    }

    @PatchMapping("/items/{productId}")
    public CartService.CartSnapshot change(@CookieValue(value = "foodie_session", required = false) String token,
                                           @PathVariable @Positive long productId,
                                           @Valid @RequestBody DeltaRequest request) {
        if (Math.abs(request.delta()) != 1) throw new ApiException(400, "Alteração de quantidade inválida");
        return cart.change(customer(token), productId, request.variationId() == null ? 0 : request.variationId(), request.addonIds(), request.delta());
    }

    @PostMapping("/import")
    public CartService.CartSnapshot importLocal(@CookieValue(value = "foodie_session", required = false) String token,
                                                 @Valid @RequestBody ImportRequest request) {
        return cart.importIfEmpty(customer(token), request.items());
    }

    @DeleteMapping
    public CartService.CartSnapshot clear(@CookieValue(value = "foodie_session", required = false) String token) {
        return cart.clear(customer(token));
    }

    @PostMapping("/checkout")
    public ResponseEntity<Map<String, Object>> checkout(@CookieValue(value = "foodie_session", required = false) String token,
                                                        @Valid @RequestBody CheckoutRequest request) {
        User customer = customer(token);
        verification.requireVerified(customer);
        return ResponseEntity.status(201).body(cart.checkout(customer, request.addressId(), request.expectedTotalCents(), request.expectedVersion(), request.idempotencyKey(), request.paymentMethod(), request.changeForCents(), request.modality(), request.couponCode(), request.scheduledFor(), request.orderType(), request.tableId(), request.partySize()));
    }

    private User customer(String token) {
        return auth.requireUser(token, "customer");
    }

    public record DeltaRequest(@NotNull Integer delta, @Positive Long variationId, @Size(max = 20) List<@Positive Long> addonIds) {}
    public record ImportRequest(@NotNull @Size(max = 30) List<@Valid ImportItem> items) {}
    public record ImportItem(@Positive long productId, @Positive Long variationId, @Size(max = 20) List<@Positive Long> addonIds, @Positive @Max(20) int quantity) {}
    public record CheckoutRequest(@Positive Long addressId, @Positive long expectedTotalCents,
                                  @Size(max = 32) String expectedVersion,
                                  @Size(max = 80) String idempotencyKey,
                                  @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Pattern(regexp = "cash|card|pix") String paymentMethod,
                                  @jakarta.validation.constraints.Min(0) @Max(100_000_000) Integer changeForCents,
                                  @jakarta.validation.constraints.Pattern(regexp = "on_delivery|online") String modality,
                                  @Size(max = 40) String couponCode,
                                  @Size(max = 30) String scheduledFor,
                                  @jakarta.validation.constraints.Pattern(regexp = "delivery|take_away|dine_in") String orderType,
                                  @Positive Long tableId,
                                  @jakarta.validation.constraints.Min(1) @Max(50) Integer partySize) {}
}
