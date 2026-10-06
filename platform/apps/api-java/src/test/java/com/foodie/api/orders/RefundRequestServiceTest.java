package com.foodie.api.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import com.foodie.api.auth.User;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class RefundRequestServiceTest {
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final PaymentService payments = mock(PaymentService.class);
    final RefundRequestService service = new RefundRequestService(jdbc, payments);
    final User owner = new User(5, "Dona", "dona@cantina.com.br", "restaurant", 3L);
    final User admin = new User(1, "Ana Suporte", "ana@demo.local", "admin", null);

    void stored(String status, long restaurantId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 7L); row.put("order_id", 30L); row.put("status", status); row.put("restaurant_id", restaurantId);
        when(jdbc.queryForList(contains("FROM refunds r JOIN orders o"), eq(7L))).thenReturn(List.of(row));
    }

    @Test
    void storeApprovesAndTheOrderIsRefunded() {
        stored("requested", 3);
        assertEquals("approved", service.decide(owner, 7, "approve", "ok").get("status"));
        verify(payments).refund(owner, 30, "ok");
        verify(jdbc).update(contains("status = 'approved'"), eq(5L), eq("ok"), eq(7L));
    }

    @Test
    void storeRejectsWithoutRefunding() {
        stored("requested", 3);
        assertEquals("rejected", service.decide(owner, 7, "reject", "fora do prazo").get("status"));
        verify(payments, never()).refund(any(), anyLong(), any());
    }

    @Test
    void anotherStoresRequestIs404() {
        stored("requested", 4);
        assertEquals(404, assertThrows(ApiException.class, () -> service.decide(owner, 7, "approve", "ok")).status());
        verify(payments, never()).refund(any(), anyLong(), any());
    }

    @Test
    void alreadyDecidedIs409AndUnknownIs404() {
        stored("approved", 3);
        assertEquals(409, assertThrows(ApiException.class, () -> service.decide(admin, 7, "approve", "Loja pediu ao suporte")).status());
        when(jdbc.queryForList(contains("FROM refunds r JOIN orders o"), eq(8L))).thenReturn(List.of());
        assertEquals(404, assertThrows(ApiException.class, () -> service.decide(admin, 8, "approve", "x")).status());
    }

    @Test
    void listFiltersByStore() {
        service.list(3L, "requested");
        verify(jdbc).queryForList(contains("o.restaurant_id = ?"), eq(3L), eq(3L), eq("requested"), eq("requested"));
    }
}
