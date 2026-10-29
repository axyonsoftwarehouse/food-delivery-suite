package com.foodie.api.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foodie.api.ApiException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OrderChatServiceTest {
    private static Map<String, Object> order(String status, Long courierId) {
        Map<String, Object> order = new HashMap<>();
        order.put("status", status);
        order.put("customer_id", 10L);
        order.put("courier_id", courierId);
        order.put("restaurant_id", 3L);
        return order;
    }

    @Test
    void partyComesFromTheRoleInThatOrder() {
        Map<String, Object> order = order("accepted", 20L);
        assertThat(OrderChatService.party(order, 10, "customer", null)).isEqualTo("customer");
        assertThat(OrderChatService.party(order, 20, "courier", null)).isEqualTo("courier");
        assertThat(OrderChatService.party(order, 30, "restaurant", 3L)).isEqualTo("store");
        assertThat(OrderChatService.party(order, 31, "kitchen", 3L)).isEqualTo("store");
        // Equipe de outra loja, outro cliente e o entregador que saiu do pedido não participam.
        assertThat(OrderChatService.party(order, 32, "restaurant", 4L)).isNull();
        assertThat(OrderChatService.party(order, 11, "customer", null)).isNull();
        assertThat(OrderChatService.party(order, 21, "courier", null)).isNull();
    }

    @Test
    void onlyTheCustomerConversationsExist() {
        OrderChatService.requirePair("customer", "store");
        OrderChatService.requirePair("customer", "courier");
        OrderChatService.requirePair("store", "customer");
        OrderChatService.requirePair("courier", "customer");
        assertThatThrownBy(() -> OrderChatService.requirePair("store", "courier")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> OrderChatService.requirePair("customer", "customer")).isInstanceOf(ApiException.class);
    }

    @Test
    void closedOrdersAndMissingCourierAreReadOnly() {
        assertThat(OrderChatService.canSend(order("placed", null), "store")).isTrue();
        assertThat(OrderChatService.canSend(order("placed", null), "courier")).isFalse();
        assertThat(OrderChatService.canSend(order("picked_up", 20L), "courier")).isTrue();
        assertThat(OrderChatService.canSend(order("delivered", 20L), "store")).isFalse();
        assertThat(OrderChatService.canSend(order("cancelled", null), "customer")).isFalse();
    }
}
