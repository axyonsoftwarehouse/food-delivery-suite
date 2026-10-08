package com.foodie.api.payments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import com.foodie.api.auth.User;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class OfflinePaymentServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final OfflinePaymentService service = new OfflinePaymentService(jdbc);
    private final User customer = new User(7, "Cliente", "cliente@demo.local", "customer", null);
    private final Map<String, Object> payment = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        payment.put("status", "pending");
        payment.put("modality", "online");
        payment.put("external_id", "ORDTST01ABC");
        payment.put("customer_id", 7L);
        payment.put("order_status", "placed");
        when(jdbc.queryForList(argThat(sql -> sql != null && sql.contains("FROM order_payments")), any(Object[].class))).thenReturn(List.of(payment));
        when(jdbc.queryForList(argThat(sql -> sql != null && sql.contains("FROM offline_payment_methods")), any(Object[].class)))
            .thenReturn(List.of(Map.of("id", 3L, "slug", "pix-manual", "requires_proof", false)));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    }

    @Test
    void cannotSwitchToProofWhileAnOnlineChargeIsOpen() {
        // Revisão de 08/10/2026: com o Pix online ainda pagável, trocar para comprovante permitia pagar
        // duas vezes — e o aviso do Pix chegava num pagamento já "pago" e era ignorado, sem estorno.
        ApiException error = assertThrows(ApiException.class, () -> service.submitProof(customer, 1, 3, null, null));
        assertEquals(409, error.status());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void canSwitchToProofAfterTheOnlineChargeFailed() {
        payment.put("status", "rejected");
        assertEquals("pending", service.submitProof(customer, 1, 3, null, null).get("status"));
    }

    @Test
    void paymentOnDeliveryCanSwitchToProof() {
        payment.put("modality", "on_delivery");
        payment.put("external_id", null);
        assertEquals("pending", service.submitProof(customer, 1, 3, null, null).get("status"));
    }
}
