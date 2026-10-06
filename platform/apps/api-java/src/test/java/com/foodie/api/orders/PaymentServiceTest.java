package com.foodie.api.orders;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
    private final com.foodie.api.payments.PaymentGatewayRegistry gateways = Mockito.mock(com.foodie.api.payments.PaymentGatewayRegistry.class);
    private final com.foodie.api.payments.PaymentGateway gateway = Mockito.mock(com.foodie.api.payments.PaymentGateway.class);
    private final com.foodie.api.payments.accounts.PaymentAccountService accounts = Mockito.mock(com.foodie.api.payments.accounts.PaymentAccountService.class);
    private final PaymentService service = new PaymentService(jdbc, ledger, rewards, gateways, accounts, false);
    private final PaymentService withOnline = new PaymentService(jdbc, ledger, rewards, gateways, accounts, true);
    private final User admin = new User(1, "Admin", "admin@demo.local", "admin", null);
    private final User courier = new User(5, "Entregador", "entregador@demo.local", "courier", null);

    private final User owner = new User(5, "Dona", "dona@cantina.com.br", "restaurant", 3L);

    @Test
    void storeRefundsItsOwnOrder() {
        storedPayment("paid", "cash", 1000, 5L);
        when(jdbc.queryForList(eq("SELECT restaurant_id FROM orders WHERE id = ?"), eq(Long.class), eq(1L))).thenReturn(List.of(3L));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        assertEquals("refunded", service.refund(owner, 1, "cliente desistiu").get("status"));
        verify(ledger).reverseOrder(1);
    }

    @Test
    void directRefundClosesTheOpenCustomerRequest() {
        storedPayment("paid", "cash", 1000, 5L);
        when(jdbc.queryForList(eq("SELECT restaurant_id FROM orders WHERE id = ?"), eq(Long.class), eq(1L))).thenReturn(List.of(3L));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        service.refund(owner, 1, "cliente desistiu");

        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("UPDATE refunds SET status = 'approved'"), eq(5L), eq("Pagamento estornado diretamente"), eq(1L));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("WHERE order_id = ? AND status = 'requested'"), eq(5L), eq("Pagamento estornado diretamente"), eq(1L));
    }

    @Test
    void storeCannotRefundAnotherStoresOrder() {
        when(jdbc.queryForList(eq("SELECT restaurant_id FROM orders WHERE id = ?"), eq(Long.class), eq(1L))).thenReturn(List.of(4L));
        assertEquals(404, assertThrows(ApiException.class, () -> service.refund(owner, 1, "x")).status());
        verify(jdbc, Mockito.never()).update(anyString(), any(Object[].class));
    }

    @Test
    void restaurantOfMissingOrderIs404() {
        when(jdbc.queryForList(eq("SELECT restaurant_id FROM orders WHERE id = ?"), eq(Long.class), eq(9L))).thenReturn(List.of());
        assertEquals(404, assertThrows(ApiException.class, () -> service.restaurantOf(9)).status());
    }

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
        assertEquals(409, assertThrows(ApiException.class, () -> service.create(1, "cash", "online", 1000, null)).status());
        assertEquals(409, assertThrows(ApiException.class, () -> service.create(1, "pix", "online", 1000, 1000)).status());
        assertEquals(400, assertThrows(ApiException.class, () -> service.create(1, "pix", "nonsense", 1000, null)).status());
    }

    @Test
    void rejectsNewOnlineCharges() {
        assertEquals(409, assertThrows(ApiException.class, () -> service.create(1, "pix", "online", 1000, null)).status());
        assertEquals(409, assertThrows(ApiException.class, () -> service.create(1, "card", "online", 1000, null)).status());
        verify(jdbc, Mockito.never()).update(anyString(), any(Object[].class));
    }

    @Test
    void createsOnlineChargeOnlyWhenAllowed() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        assertDoesNotThrow(() -> withOnline.create(1, "pix", "online", 1000, null));
        assertDoesNotThrow(() -> withOnline.create(1, "card", "online", 1000, null));
        verify(jdbc, Mockito.times(2)).update(anyString(), any(Object[].class));
    }

    @Test
    void onlineStillRejectsCash() {
        assertEquals(400, assertThrows(ApiException.class, () -> withOnline.create(1, "cash", "online", 1000, null)).status());
        verify(jdbc, Mockito.never()).update(anyString(), any(Object[].class));
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

    private void storedOnlinePayment() {
        java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("status", "paid");
        row.put("method", "pix");
        row.put("modality", "online");
        row.put("provider", "mercadopago");
        row.put("external_id", "ORDTST01ABC");
        row.put("payment_account_id", 9L);
        row.put("provider_user_id", "3588446200");
        row.put("amount_received_cents", null);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row));
    }

    @Test
    void onlineRefundGoesThroughTheProviderBeforeMarkingRefunded() {
        // 05/10/2026, pedido #23: o estorno do admin marcou `refunded` no Foodie e a order continuou
        // `processed/accredited` no Mercado Pago — o dinheiro não voltava para o cliente.
        storedOnlinePayment();
        when(accounts.credentialsForAccount(9L)).thenReturn(java.util.Optional.of(new com.foodie.api.payments.accounts.MerchantCredentials(9, 3, "t", "pk", "3588446200")));
        when(gateways.resolve("mercadopago")).thenReturn(gateway);
        when(gateway.refund(any(), eq("ORDTST01ABC"), eq("refund-order-1"))).thenReturn(
            new com.foodie.api.payments.PaymentGateway.Charge("ORDTST01ABC", "1", 1000, "refunded", "refunded", null, null, null, null));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        Map<String, Object> result = service.refund(admin, 1, "pedido de teste");

        org.mockito.InOrder ordem = Mockito.inOrder(gateway, jdbc, ledger);
        ordem.verify(gateway).refund(any(), eq("ORDTST01ABC"), eq("refund-order-1"));
        ordem.verify(jdbc).update(org.mockito.ArgumentMatchers.contains("status = 'refunded'"), eq("pedido de teste"), eq("refunded"), eq(1L), eq(1L));
        ordem.verify(ledger).reverseOrder(1);
        assertEquals("refunded", result.get("status"));
        assertEquals("refunded", result.get("providerStatus"));
    }

    @Test
    void providerRefusalLeavesThePaymentPaid() {
        storedOnlinePayment();
        when(accounts.credentialsForAccount(9L)).thenReturn(java.util.Optional.of(new com.foodie.api.payments.accounts.MerchantCredentials(9, 3, "t", "pk", "3588446200")));
        when(gateways.resolve("mercadopago")).thenReturn(gateway);
        when(gateway.refund(any(), anyString(), anyString())).thenThrow(new ApiException(502, "Mercado Pago recusou o estorno: HTTP 400"));

        assertEquals(502, assertThrows(ApiException.class, () -> service.refund(admin, 1, "pedido de teste")).status());
        verify(jdbc, Mockito.never()).update(anyString(), any(Object[].class));
        verify(ledger, Mockito.never()).reverseOrder(Mockito.anyLong());
    }

    @Test
    void paymentReceivedOnDeliveryRefundsWithoutTheProvider() {
        storedPayment("paid", "cash", 1000, 5L);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        assertEquals("refunded", service.refund(admin, 1, "troco errado").get("status"));
        verify(gateways, Mockito.never()).resolve(any());
        verify(ledger).reverseOrder(1);
    }

    @Test
    void disconnectedStoreCannotRefundThroughTheProvider() {
        storedOnlinePayment();
        when(accounts.credentialsForAccount(9L)).thenReturn(java.util.Optional.empty());
        ApiException erro = assertThrows(ApiException.class, () -> service.refund(admin, 1, "pedido de teste"));
        assertEquals(409, erro.status());
        assertEquals("A loja desconectou o Mercado Pago. Estorne pelo painel do Mercado Pago ou reconecte a conta.", erro.getMessage());
        verify(jdbc, Mockito.never()).update(anyString(), any(Object[].class));
    }

    @Test
    void storeThatSwitchedAccountsCannotRefundAnOldCharge() {
        storedOnlinePayment();
        when(accounts.credentialsForAccount(9L)).thenReturn(java.util.Optional.of(new com.foodie.api.payments.accounts.MerchantCredentials(9, 3, "t", "pk", "999")));
        ApiException erro = assertThrows(ApiException.class, () -> service.refund(admin, 1, "pedido de teste"));
        assertEquals(409, erro.status());
        assertEquals("A loja trocou de conta Mercado Pago depois desta cobrança. Estorne pelo painel da conta que recebeu.", erro.getMessage());
    }

    @Test
    void chargeMadeBeforeStoreAccountsIsRefusedClearly() {
        storedOnlinePayment();
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(inv -> {
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("status", "paid"); row.put("method", "pix"); row.put("modality", "online"); row.put("provider", "mercadopago");
            row.put("external_id", "ORDTST01ABC"); row.put("payment_account_id", null); row.put("provider_user_id", null); row.put("amount_received_cents", null);
            return List.of(row);
        });
        assertEquals(409, assertThrows(ApiException.class, () -> service.refund(admin, 1, "pedido de teste")).status());
    }

    @Test
    void automaticRefundSkipsPaymentOnDelivery() {
        java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("status", "paid"); row.put("modality", "on_delivery"); row.put("method", "cash");
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row));

        assertEquals(false, service.refundIfPaidOnline(1L, 1, "Estorno automático: pedido cancelado pelo cliente"));
        verify(jdbc, Mockito.never()).update(anyString(), any(Object[].class));
        verify(gateways, Mockito.never()).resolve(any());
        verify(ledger, Mockito.never()).reverseOrder(Mockito.anyLong());
    }

    @Test
    void automaticRefundSkipsPendingOnlinePayment() {
        java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("status", "pending"); row.put("modality", "online"); row.put("method", "pix");
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row));

        assertEquals(false, service.refundIfPaidOnline(1L, 1, "Estorno automático: pedido expirado"));
        verify(jdbc, Mockito.never()).update(anyString(), any(Object[].class));
        verify(gateways, Mockito.never()).resolve(any());
    }

    @Test
    void automaticRefundSkipsOrderWithoutPayment() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        assertEquals(false, service.refundIfPaidOnline(null, 1, "Estorno automático: pedido expirado sem aceite"));
        verify(jdbc, Mockito.never()).update(anyString(), any(Object[].class));
    }

    @Test
    void automaticRefundReturnsPaidOnlineMoneyThroughTheProvider() {
        storedOnlinePayment();
        when(accounts.credentialsForAccount(9L)).thenReturn(java.util.Optional.of(new com.foodie.api.payments.accounts.MerchantCredentials(9, 3, "t", "pk", "3588446200")));
        when(gateways.resolve("mercadopago")).thenReturn(gateway);
        when(gateway.refund(any(), eq("ORDTST01ABC"), eq("refund-order-1"))).thenReturn(
            new com.foodie.api.payments.PaymentGateway.Charge("ORDTST01ABC", "1", 1000, "refunded", "refunded", null, null, null, null));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        assertEquals(true, service.refundIfPaidOnline(7L, 1, "Estorno automático: pedido cancelado pelo cliente"));

        org.mockito.InOrder ordem = Mockito.inOrder(gateway, jdbc, ledger);
        ordem.verify(gateway).refund(any(), eq("ORDTST01ABC"), eq("refund-order-1"));
        ordem.verify(jdbc).update(org.mockito.ArgumentMatchers.contains("status = 'refunded'"), eq("Estorno automático: pedido cancelado pelo cliente"), eq("refunded"), eq(7L), eq(1L));
        ordem.verify(ledger).reverseOrder(1);
    }

    @Test
    void expiryRefundRecordsNoActor() {
        storedOnlinePayment();
        when(accounts.credentialsForAccount(9L)).thenReturn(java.util.Optional.of(new com.foodie.api.payments.accounts.MerchantCredentials(9, 3, "t", "pk", "3588446200")));
        when(gateways.resolve("mercadopago")).thenReturn(gateway);
        when(gateway.refund(any(), anyString(), anyString())).thenReturn(
            new com.foodie.api.payments.PaymentGateway.Charge("ORDTST01ABC", "1", 1000, "refunded", "refunded", null, null, null, null));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        assertEquals(true, service.refundIfPaidOnline(null, 1, "Estorno automático: pedido expirado sem aceite"));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("status = 'refunded'"), eq("Estorno automático: pedido expirado sem aceite"), eq("refunded"), org.mockito.ArgumentMatchers.isNull(), eq(1L));
    }

    @Test
    void automaticRefundFailureSurfaces() {
        storedOnlinePayment();
        when(accounts.credentialsForAccount(9L)).thenReturn(java.util.Optional.empty());

        assertEquals(409, assertThrows(ApiException.class, () -> service.refundIfPaidOnline(7L, 1, "Estorno automático: pedido cancelado pelo cliente")).status());
        verify(jdbc, Mockito.never()).update(anyString(), any(Object[].class));
    }
}
