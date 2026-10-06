package com.foodie.api.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import com.foodie.api.auth.User;
import com.foodie.api.catalog.AddonService;
import com.foodie.api.catalog.PostalCoverageService;
import com.foodie.api.finance.LedgerService;
import com.foodie.api.hours.RestaurantHoursService;
import com.foodie.api.notifications.NotificationService;
import com.foodie.api.rewards.RewardsService;
import com.foodie.api.routing.DeliveryService;
import com.foodie.api.support.SupportActionService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class OrderServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PaymentService payments = mock(PaymentService.class);
    private final OrderService orders = new OrderService(jdbc, mock(NamedParameterJdbcTemplate.class), mock(PostalCoverageService.class),
        mock(RestaurantHoursService.class), payments, mock(DeliveryService.class), mock(NotificationService.class),
        mock(AddonService.class), mock(CouponService.class), mock(CampaignService.class), mock(LedgerService.class),
        mock(RewardsService.class), mock(SupportActionService.class));

    private static final User ADMIN = new User(1, "Admin", "admin@demo.local", "admin", null);
    private static final User RESTAURANT = new User(2, "Loja", "loja@demo.local", "restaurant", 3L);
    private static final User CUSTOMER = new User(8, "Ana", "ana@demo.local", "customer", null);
    private static final User COURIER = new User(9, "Bia", "bia@demo.local", "courier", null);

    @Test
    void lookupFindsAnyOrderForAdmin() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of("id", 26L, "customer_name", "Ana")));

        assertThat(orders.lookup(ADMIN, 26)).containsEntry("id", 26L).containsEntry("customer_name", "Ana");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForList(sql.capture(), args.capture());
        assertThat(sql.getValue()).contains("WHERE 1 = 1 AND o.id = ?");
        assertThat(args.getValue()).containsExactly(26L);
    }

    @Test
    void lookupKeepsRestaurantScope() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        assertThatThrownBy(() -> orders.lookup(RESTAURANT, 26))
            .isInstanceOf(ApiException.class)
            .hasMessage("Pedido não encontrado");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForList(sql.capture(), args.capture());
        assertThat(sql.getValue()).contains("WHERE o.restaurant_id = ? AND o.id = ?");
        assertThat(args.getValue()).containsExactly(3L, 26L);
    }

    private void storedOrder(long id, String status) {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("id", id); row.put("customer_id", 8L); row.put("restaurant_id", 3L); row.put("courier_id", 9L); row.put("status", status);
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.startsWith("SELECT id, customer_id, restaurant_id, courier_id, status FROM orders"), any(Object[].class)))
            .thenReturn(List.of(row));
    }

    @Test
    void customerCancellingAPaidOnlineOrderIsRefundedBeforeTheStatusChanges() {
        // Adendo 06/10/2026: cancelar só cancelava pagamento pendente; o pago ficava retido.
        storedOrder(40, "placed");

        orders.changeStatus(CUSTOMER, 40, "cancel", null, "mudei de ideia");

        org.mockito.InOrder ordem = org.mockito.Mockito.inOrder(payments, jdbc);
        ordem.verify(payments).refundIfPaidOnline(8L, 40L, "Estorno automático: pedido cancelado pelo cliente");
        ordem.verify(jdbc).update("UPDATE orders SET status = ? WHERE id = ?", "cancelled", 40L);
        ordem.verify(payments).cancelPending(40L);
    }

    @Test
    void cancellationTellsTheCallerWhenThePaymentWasRefunded() {
        // A loja do cliente usa isto para dizer "O pagamento foi estornado." sem adivinhar pela lista.
        storedOrder(40, "placed");
        when(payments.refundIfPaidOnline(8L, 40L, "Estorno automático: pedido cancelado pelo cliente")).thenReturn(true);

        assertThat(orders.changeStatus(CUSTOMER, 40, "cancel", null, "mudei de ideia")).containsEntry("paymentRefunded", true);
    }

    @Test
    void cancellationWithoutRefundDoesNotClaimOne() {
        storedOrder(40, "placed");

        assertThat(orders.changeStatus(CUSTOMER, 40, "cancel", null, "mudei de ideia")).doesNotContainKey("paymentRefunded");
    }

    @Test
    void supportCancellationUsesTheSupportNote() {
        storedOrder(40, "accepted");

        orders.changeStatus(ADMIN, 40, "cancel", null, "cliente pediu por telefone");

        verify(payments).refundIfPaidOnline(1L, 40L, "Estorno automático: pedido cancelado pelo suporte");
    }

    @Test
    void failedRefundKeepsTheOrderUnchanged() {
        storedOrder(40, "placed");
        when(payments.refundIfPaidOnline(any(), org.mockito.ArgumentMatchers.anyLong(), anyString()))
            .thenThrow(new ApiException(409, "A loja desconectou o Mercado Pago. Estorne pelo painel do Mercado Pago ou reconecte a conta."));

        assertThatThrownBy(() -> orders.changeStatus(CUSTOMER, 40, "cancel", null, "mudei de ideia"))
            .isInstanceOf(ApiException.class)
            .hasMessage("A loja desconectou o Mercado Pago. Estorne pelo painel do Mercado Pago ou reconecte a conta.");

        verify(jdbc, org.mockito.Mockito.never()).update(org.mockito.ArgumentMatchers.startsWith("UPDATE orders SET status"), any(Object[].class));
        verify(payments, org.mockito.Mockito.never()).cancelPending(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void storeRejectionUsesTheRejectionNote() {
        storedOrder(40, "placed");

        orders.changeStatus(RESTAURANT, 40, "reject", null, "sem ingrediente");

        org.mockito.InOrder ordem = org.mockito.Mockito.inOrder(payments, jdbc);
        ordem.verify(payments).refundIfPaidOnline(2L, 40L, "Estorno automático: pedido recusado pela loja");
        ordem.verify(jdbc).update("UPDATE orders SET status = ? WHERE id = ?", "rejected", 40L);
    }

    @Test
    void failedDeliveryIsNotRefundedAutomatically() {
        // `failed` segue para o reembolso decidido pela loja.
        storedOrder(40, "picked_up");

        orders.changeStatus(COURIER, 40, "fail", null, "cliente ausente");

        verify(payments, org.mockito.Mockito.never()).refundIfPaidOnline(any(), org.mockito.ArgumentMatchers.anyLong(), anyString());
        verify(payments).cancelPending(40L);
    }

    private void staleOrders(Long... ids) {
        org.mockito.Mockito.doReturn(List.of(ids)).when(jdbc).query(org.mockito.ArgumentMatchers.contains("status = 'placed' AND created_at"), any(org.springframework.jdbc.core.RowMapper.class));
    }

    private void lockedReread(String status, Object due) {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("status", status); row.put("created_at", null); row.put("scheduled_at", null); row.put("due", due);
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.startsWith("SELECT status, created_at, scheduled_at"), any(Object[].class))).thenReturn(List.of(row));
    }

    @Test
    void expiryRereadsTheLockedOrderAndSkipsOneAcceptedMeanwhile() {
        // A loja aceitou entre a busca dos vencidos e a trava: nem estorna nem expira.
        staleOrders(41L);
        lockedReread("accepted", 1L);

        orders.list(ADMIN);

        verify(jdbc).queryForList(org.mockito.ArgumentMatchers.endsWith("FROM orders WHERE id = ? FOR UPDATE"), eq(41L));
        verify(payments, org.mockito.Mockito.never()).refundIfPaidOnline(any(), org.mockito.ArgumentMatchers.anyLong(), anyString());
        verify(jdbc, org.mockito.Mockito.never()).update(org.mockito.ArgumentMatchers.contains("'expired'"), any(Object[].class));
    }

    @Test
    void expirySkipsAnOrderNoLongerDue() {
        staleOrders(41L);
        lockedReread("placed", 0L);

        orders.list(ADMIN);

        verify(payments, org.mockito.Mockito.never()).refundIfPaidOnline(any(), org.mockito.ArgumentMatchers.anyLong(), anyString());
        verify(jdbc, org.mockito.Mockito.never()).update(org.mockito.ArgumentMatchers.contains("'expired'"), any(Object[].class));
    }

    @Test
    void eachStaleOrderExpiresInItsOwnTransaction() {
        java.util.concurrent.atomic.AtomicInteger transactions = new java.util.concurrent.atomic.AtomicInteger();
        org.springframework.transaction.support.TransactionOperations counting = new org.springframework.transaction.support.TransactionOperations() {
            @Override
            public <T> T execute(org.springframework.transaction.support.TransactionCallback<T> action) {
                transactions.incrementAndGet();
                return org.springframework.transaction.support.TransactionOperations.withoutTransaction().execute(action);
            }
        };
        OrderService transactional = new OrderService(jdbc, mock(NamedParameterJdbcTemplate.class), mock(PostalCoverageService.class),
            mock(RestaurantHoursService.class), payments, mock(DeliveryService.class), mock(NotificationService.class),
            mock(AddonService.class), mock(CouponService.class), mock(CampaignService.class), mock(LedgerService.class),
            mock(RewardsService.class), mock(SupportActionService.class), counting);
        staleOrders(41L, 42L);
        lockedReread("placed", true);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        transactional.list(ADMIN);

        assertThat(transactions.get()).isEqualTo(2);
    }

    @Test
    void expiryRefundsBeforeExpiring() {
        staleOrders(41L);
        lockedReread("placed", 1L);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        orders.list(ADMIN);

        org.mockito.InOrder ordem = org.mockito.Mockito.inOrder(payments, jdbc);
        ordem.verify(payments).refundIfPaidOnline(null, 41L, "Estorno automático: pedido expirado sem aceite");
        ordem.verify(jdbc).update("UPDATE orders SET status = 'expired' WHERE id = ? AND status = 'placed'", 41L);
    }

    @Test
    void failedExpiryRefundLeavesTheOrderPlaced() {
        staleOrders(41L, 42L);
        lockedReread("placed", 1L);
        when(payments.refundIfPaidOnline(null, 41L, "Estorno automático: pedido expirado sem aceite"))
            .thenThrow(new ApiException(409, "A loja desconectou o Mercado Pago. Estorne pelo painel do Mercado Pago ou reconecte a conta."));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        orders.list(ADMIN);

        verify(jdbc, org.mockito.Mockito.never()).update("UPDATE orders SET status = 'expired' WHERE id = ? AND status = 'placed'", 41L);
        verify(payments, org.mockito.Mockito.never()).cancelPending(41L);
        // Um pedido com estorno falho não trava a expiração dos outros.
        verify(jdbc).update("UPDATE orders SET status = 'expired' WHERE id = ? AND status = 'placed'", 42L);
    }
}
