package com.foodie.api.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import com.foodie.api.auth.User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

class PaymentServiceTest {
    private final JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
    private final com.foodie.api.finance.LedgerService ledger = Mockito.mock(com.foodie.api.finance.LedgerService.class);
    private final com.foodie.api.rewards.RewardsService rewards = Mockito.mock(com.foodie.api.rewards.RewardsService.class);
    private final PaymentService service = new PaymentService(jdbc, ledger, rewards);
    private final User admin = new User(1, "Admin", "admin@demo.local", "admin", null);
    private final User courier = new User(5, "Entregador", "entregador@demo.local", "courier", null);

    private void storedPayment(String status, String method, long due, Long courierId) {
        java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("status", status);
        row.put("method", method);
        row.put("amount_due_cents", due);
        row.put("courier_id", courierId);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row));
    }

    @Test
    void rejectsInvalidMethodAndChange() {
        assertEquals(400, assertThrows(ApiException.class, () -> service.create(1, "bitcoin", "on_delivery", 1000, null)).status());
        assertEquals(400, assertThrows(ApiException.class, () -> service.create(1, "card", "on_delivery", 1000, 2000)).status());
        assertEquals(400, assertThrows(ApiException.class, () -> service.create(1, "cash", "on_delivery", 1000, 500)).status());
        assertEquals(400, assertThrows(ApiException.class, () -> service.create(1, "cash", "online", 1000, null)).status());
        assertEquals(400, assertThrows(ApiException.class, () -> service.create(1, "pix", "online", 1000, 1000)).status());
        assertEquals(400, assertThrows(ApiException.class, () -> service.create(1, "pix", "nonsense", 1000, null)).status());
    }

    @Test
    void acceptsOnlinePixAndCard() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        service.create(1, "pix", "online", 1000, null);
        service.create(1, "card", "online", 1000, null);
        verify(jdbc, Mockito.times(2)).update(anyString(), any(Object[].class));
    }

    @Test
    void confirmsCashAndComputesChange() {
        storedPayment("pending", "cash", 1000, 5L);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        Map<String, Object> result = service.confirm(courier, 1, 1500, null);

        assertEquals("paid", result.get("status"));
        assertEquals(500L, result.get("changeCents"));
        verify(jdbc).update(anyString(), any(Object[].class));
    }

    @Test
    void rejectsSecondConfirmation() {
        storedPayment("paid", "cash", 1000, 5L);
        assertEquals(409, assertThrows(ApiException.class, () -> service.confirm(admin, 1, 1000, null)).status());
    }

    @Test
    void rejectsCourierNotAssignedToOrder() {
        storedPayment("pending", "cash", 1000, 9L);
        assertEquals(403, assertThrows(ApiException.class, () -> service.confirm(courier, 1, 1000, null)).status());
    }

    @Test
    void rejectsCardPaymentWithDivergentAmount() {
        storedPayment("pending", "card", 1000, 5L);
        assertEquals(400, assertThrows(ApiException.class, () -> service.confirm(courier, 1, 1200, null)).status());
    }

    @Test
    void refundRequiresAdminAndPaidStatus() {
        assertEquals(403, assertThrows(ApiException.class, () -> service.refund(courier, 1, null)).status());
        storedPayment("paid", "cash", 1000, 5L);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
        assertEquals(409, assertThrows(ApiException.class, () -> service.refund(admin, 1, "cliente reclamou")).status());
    }
}
