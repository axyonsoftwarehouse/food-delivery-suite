package com.foodie.api.chat;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import com.foodie.api.auth.User;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

class ChatPolicyTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ChatPolicy policy = new ChatPolicy(jdbc);

    private static final User CUSTOMER = new User(8, "Ana", "ana@demo.local", "customer", null);
    private static final User OTHER_CUSTOMER = new User(20, "Caio", "caio@demo.local", "customer", null);
    private static final User STORE = new User(2, "Loja", "loja@demo.local", "restaurant", 3L);
    private static final User ADMIN = new User(1, "Admin", "admin@demo.local", "admin", null);

    @BeforeEach
    void setUp() {
        role(8L, "customer");
        role(20L, "customer");
        role(2L, "restaurant");
        role(1L, "admin");
        Map<String, Object> order = new HashMap<>();
        order.put("id", 40L); order.put("customer_id", 8L); order.put("courier_id", null); order.put("restaurant_id", 3L);
        when(jdbc.queryForList(contains("FROM orders WHERE id = ?"), eq(40L))).thenReturn(List.of(order));
        staff(2L, true);
        staff(20L, false);
        staff(8L, false);
    }

    private void role(long userId, String role) {
        when(jdbc.queryForList(contains("SELECT role FROM users"), eq(String.class), eq(userId))).thenReturn(List.of(role));
    }

    @SuppressWarnings("unchecked")
    private void staff(long userId, boolean isStaff) {
        when(jdbc.query(contains("role IN ('restaurant', 'kitchen')"), any(ResultSetExtractor.class), eq(userId), eq(3L)))
            .thenReturn(isStaff ? 1 : null);
    }

    @SuppressWarnings("unchecked")
    private void adminWroteTo(long userId, boolean wrote) {
        when(jdbc.query(contains("FROM chat_messages"), any(ResultSetExtractor.class), eq(1L), eq(userId))).thenReturn(wrote ? 1 : null);
    }

    @Test
    void customerAndStoreTalkAboutTheirOrder() {
        assertThatCode(() -> policy.requireCanSend(CUSTOMER, 2L, 40L)).doesNotThrowAnyException();
        assertThatCode(() -> policy.requireCanSend(STORE, 8L, 40L)).doesNotThrowAnyException();
    }

    @Test
    void messageBetweenUsersNeedsAnOrder() {
        assertThatThrownBy(() -> policy.requireCanSend(CUSTOMER, 2L, null))
            .isInstanceOf(ApiException.class).hasMessage("Informe o pedido da conversa");
    }

    @Test
    void customerCannotWriteToAStrangerThroughAnOrder() {
        assertThatThrownBy(() -> policy.requireCanSend(CUSTOMER, 20L, 40L))
            .isInstanceOf(ApiException.class).hasMessage("Vocês não participam deste pedido");
        assertThatThrownBy(() -> policy.requireCanSend(OTHER_CUSTOMER, 2L, 40L))
            .isInstanceOf(ApiException.class).hasMessage("Vocês não participam deste pedido");
    }

    @Test
    void adminWritesToAnyone() {
        assertThatCode(() -> policy.requireCanSend(ADMIN, 20L, null)).doesNotThrowAnyException();
    }

    @Test
    void writingToAnAdminOnlyAsAReply() {
        adminWroteTo(8L, false);
        assertThatThrownBy(() -> policy.requireCanSend(CUSTOMER, 1L, null))
            .isInstanceOf(ApiException.class).hasMessage("Só é possível responder a um contato do suporte");
        adminWroteTo(8L, true);
        assertThatCode(() -> policy.requireCanSend(CUSTOMER, 1L, null)).doesNotThrowAnyException();
    }

    @Test
    void unknownRecipientOrOrder() {
        when(jdbc.queryForList(contains("SELECT role FROM users"), eq(String.class), eq(99L))).thenReturn(List.of());
        assertThatThrownBy(() -> policy.requireCanSend(CUSTOMER, 99L, 40L)).hasMessage("Destinatário não encontrado");
        when(jdbc.queryForList(contains("FROM orders WHERE id = ?"), eq(41L))).thenReturn(List.of());
        assertThatThrownBy(() -> policy.requireCanSend(CUSTOMER, 2L, 41L)).hasMessage("Pedido não encontrado");
        assertThatThrownBy(() -> policy.requireCanSend(CUSTOMER, 8L, 40L)).hasMessage("Destinatário inválido");
    }
}
