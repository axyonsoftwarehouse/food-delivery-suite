package com.foodie.api.chat;

import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.permissions.PermissionService;
import com.foodie.api.permissions.Permissions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Chat do pedido: cliente ↔ loja e cliente ↔ entregador (ver {@link OrderChatService}). */
@RestController
public class OrderChatController {
    private final AuthService auth;
    private final PermissionService permissions;
    private final OrderChatService chat;

    public OrderChatController(AuthService auth, PermissionService permissions, OrderChatService chat) {
        this.auth = auth;
        this.permissions = permissions;
        this.chat = chat;
    }

    @GetMapping("/orders/{id}/chat")
    public Map<String, Object> orderChat(@CookieValue(value = "foodie_session", required = false) String token,
                                         @PathVariable @Positive long id,
                                         @RequestParam @Pattern(regexp = "customer|store|courier") String with) {
        return chat.thread(participant(token), id, with);
    }

    @PostMapping("/orders/{id}/chat")
    public ResponseEntity<Map<String, Object>> sendOrderMessage(@CookieValue(value = "foodie_session", required = false) String token,
                                                                @PathVariable @Positive long id,
                                                                @Valid @RequestBody OrderMessageRequest body) {
        return ResponseEntity.status(201).body(chat.send(participant(token), id, body.to(), body.body()));
    }

    @GetMapping("/orders/chat/unread")
    public List<Map<String, Object>> orderChatUnread(@CookieValue(value = "foodie_session", required = false) String token) {
        return chat.unread(participant(token));
    }

    private User participant(String token) {
        User user = auth.requireUser(token, "customer", "courier", "restaurant", "kitchen");
        // A equipe da loja conversa sobre pedidos que ela pode ver.
        if ("restaurant".equals(user.role()) || "kitchen".equals(user.role())) permissions.require(user, Permissions.ORDERS_VIEW);
        return user;
    }

    public record OrderMessageRequest(@NotBlank @Pattern(regexp = "customer|store|courier") String to, @NotBlank @Size(max = 1000) String body) {}
}
