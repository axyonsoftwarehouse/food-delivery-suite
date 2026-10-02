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
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

/** Caminho de cobrança online nova: bloqueado por padrão, habilitado só no modo de teste. */
class OnlinePaymentServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PaymentGatewayRegistry gateways = mock(PaymentGatewayRegistry.class);
    private final PaymentGateway gateway = mock(PaymentGateway.class);
    private final User customer = new User(7, "Cliente", "cliente@demo.local", "customer", null);
    private final User stranger = new User(9, "Outro", "outro@demo.local", "customer", null);

    private final Map<String, Object> order = new LinkedHashMap<>();
    private final Map<String, Object> payment = new LinkedHashMap<>();

    private OnlinePaymentService service(boolean allowDirectOnlineCharges) {
        return new OnlinePaymentService(jdbc, gateways, allowDirectOnlineCharges);
    }

    @BeforeEach
    void setUp() {
        order.put("id", 1L);
        order.put("customer_id", 7L);
        order.put("total_cents", 1000L);
        order.put("status", "placed");
        payment.put("id", 11L);
        payment.put("status", "pending");
        payment.put("external_id", null);
        payment.put("qr_code", null);
        payment.put("ticket_url", null);
        payment.put("amount_due_cents", 1000L);
        when(jdbc.queryForList(argThat(sql -> sql != null && sql.contains("FROM orders")), any(Object[].class))).thenReturn(List.of(order));
        when(jdbc.queryForList(argThat(sql -> sql != null && sql.contains("FROM order_payments")), any(Object[].class))).thenReturn(List.of(payment));
        when(jdbc.queryForObject(anyString(), any(Class.class), any(Object[].class))).thenReturn("cliente@demo.local");
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(gateways.resolve(any())).thenReturn(gateway);
        when(gateway.provider()).thenReturn("mercadopago");
    }

    private PaymentGateway.Charge pixCharge() {
        return new PaymentGateway.Charge("123456", "1", 1000, "pending", "pending", "00020126...",
            "base64image", "https://www.mercadopago.com.br/payments/123456/ticket", null);
    }

    @Test
    void withoutFlagNewChargesStayBlocked() {
        ApiException error = assertThrows(ApiException.class, () -> service(false).startIntent(customer, 1, "pix", null));

        assertEquals(409, error.status());
        assertEquals("Novas cobranças online exigem recebimento direto pelo restaurante", error.getMessage());
        verify(gateway, never()).create(any());
    }

    @Test
    void withFlagCreatesAndPersistsTheCharge() {
        when(gateway.create(any())).thenReturn(pixCharge());

        service(true).startIntent(customer, 1, "pix", null);

        ArgumentCaptor<PaymentGateway.ChargeRequest> request = ArgumentCaptor.forClass(PaymentGateway.ChargeRequest.class);
        verify(gateway).create(request.capture());
        assertEquals(1L, request.getValue().orderId());
        assertEquals(1000L, request.getValue().amountCents());
        assertEquals("cliente@demo.local", request.getValue().payerEmail());
        assertEquals("order-1-11", request.getValue().idempotencyKey());

        ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), values.capture());
        Object[] saved = values.getValue();
        assertEquals("mercadopago", saved[0]);
        assertEquals("pix", saved[1]);
        assertEquals("123456", saved[2]);
        assertEquals("order-1-11", saved[3]);
        assertEquals("pending", saved[4]);
        assertEquals("00020126...", saved[6]);
        assertEquals("base64image", saved[7]);
        assertEquals("https://www.mercadopago.com.br/payments/123456/ticket", saved[8]);
        assertEquals(1L, saved[10]);
    }

    @Test
    void doesNotChargeTwiceWhenAChargeAlreadyExists() {
        payment.put("external_id", "123456");

        service(true).startIntent(customer, 1, "pix", null);

        verify(gateway, never()).create(any());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void reusesTheExistingQrWithoutChargingAgain() {
        payment.put("qr_code", "00020126...");

        Map<String, Object> result = service(true).startIntent(customer, 1, "pix", null);

        verify(gateway, never()).create(any());
        assertEquals(11L, result.get("id"));
        assertEquals("00020126...", ((Map<?, ?>) result.get("image")).get("qr_code"));
    }

    @Test
    void rejectsPaidOrderAndStranger() {
        payment.put("status", "paid");
        assertEquals(409, assertThrows(ApiException.class, () -> service(true).startIntent(customer, 1, "pix", null)).status());

        payment.put("status", "pending");
        assertEquals(403, assertThrows(ApiException.class, () -> service(true).startIntent(stranger, 1, "pix", null)).status());
        assertEquals(400, assertThrows(ApiException.class, () -> service(true).startIntent(customer, 1, "cash", null)).status());
    }

    @Test
    void requiresCustomerEmail() {
        when(jdbc.queryForObject(anyString(), any(Class.class), any(Object[].class))).thenReturn(null);

        ApiException error = assertThrows(ApiException.class, () -> service(true).startIntent(customer, 1, "pix", null));

        assertEquals(400, error.status());
        verify(gateway, never()).create(any());
    }
}
