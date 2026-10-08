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
        storedOrder(id, status, "delivery");
    }

    private void storedOrder(long id, String status, String orderType) {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("id", id); row.put("customer_id", 8L); row.put("restaurant_id", 3L); row.put("courier_id", 9L); row.put("status", status);
        row.put("order_type", orderType);
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.startsWith("SELECT id, customer_id, restaurant_id, courier_id, status, order_type FROM orders"), any(Object[].class)))
            .thenReturn(List.of(row));
    }

    @Test
    void customerCancellingAPaidOnlineOrderIsRefundedAsTheLastStep() {
        // Adendo 06/10/2026: cancelar só cancelava pagamento pendente; o pago ficava retido. O estorno no
        // provedor vem por último: nenhum passo do banco pode falhar depois de o dinheiro sair.
        storedOrder(40, "placed");

        orders.changeStatus(CUSTOMER, 40, "cancel", null, "mudei de ideia");

        org.mockito.InOrder ordem = org.mockito.Mockito.inOrder(payments, jdbc);
        ordem.verify(jdbc).update("UPDATE orders SET status = ? WHERE id = ?", "cancelled", 40L);
        ordem.verify(payments).cancelPending(40L);
        ordem.verify(payments).refundIfPaidOnline(8L, 40L, "Estorno automático: pedido cancelado pelo cliente");
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
    void storeCancellationAfterAcceptUsesTheStoreNote() {
        storedOrder(40, "ready");

        orders.changeStatus(RESTAURANT, 40, "cancel", null, "acabou o ingrediente");

        verify(payments).refundIfPaidOnline(2L, 40L, "Estorno automático: pedido cancelado pela loja");
    }

    private Integer courierLookup(User actor) {
        storedOrder(40, "ready");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.ResultSetExtractor.class), any(), any())).thenReturn(1);
        orders.changeStatus(actor, 40, "assign", 12L, null);
        verify(jdbc).query(sql.capture(), any(org.springframework.jdbc.core.ResultSetExtractor.class), args.capture(), args.capture());
        assertThat(sql.getValue()).contains("role = 'courier'").contains("restaurant_id = ?");
        assertThat(args.getAllValues()).containsExactly(12L, 3L);
        return 1;
    }

    @Test
    void storeAssignsOnlyACourierOfTheOrdersStore() {
        // Decisão de 08/10/2026: o entregador é exclusivo de uma loja. A busca do entregador exige a loja do pedido.
        courierLookup(RESTAURANT);
        verify(jdbc).update("UPDATE orders SET status = ?, courier_id = ? WHERE id = ?", "assigned", 12L, 40L);
    }

    @Test
    void supportAlsoAssignsOnlyACourierOfTheOrdersStore() {
        courierLookup(ADMIN);
    }

    @Test
    void courierOfAnotherStoreIsRefused() {
        storedOrder(40, "ready");
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.ResultSetExtractor.class), any(), any())).thenReturn(null);

        assertThatThrownBy(() -> orders.changeStatus(RESTAURANT, 40, "assign", 12L, null))
            .isInstanceOf(ApiException.class)
            .hasMessage("Entregador não é da loja, não está aprovado ou está suspenso");
    }

    @Test
    void failedRefundPropagatesSoTheTransactionUndoesTheStatusChange() {
        // changeStatus é @Transactional: a exceção do estorno (último passo) desfaz a mudança de status, o
        // pagamento pendente e o evento. Aqui, sem banco, o que se verifica é que a falha sobe.
        storedOrder(40, "placed");
        when(payments.refundIfPaidOnline(any(), org.mockito.ArgumentMatchers.anyLong(), anyString()))
            .thenThrow(new ApiException(409, "A loja desconectou o Mercado Pago. Estorne pelo painel do Mercado Pago ou reconecte a conta."));

        assertThatThrownBy(() -> orders.changeStatus(CUSTOMER, 40, "cancel", null, "mudei de ideia"))
            .isInstanceOf(ApiException.class);
        assertThat(OrderService.class.getMethods()).filteredOn(method -> method.getName().equals("changeStatus"))
            .allMatch(method -> method.isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class));
    }

    @Test
    void customerSeesAFriendlyMessageWhenTheAutomaticRefundFails() {
        // "Estorne pelo painel do Mercado Pago" é instrução para a loja; o cliente não tem o que fazer com ela.
        storedOrder(40, "placed");
        when(payments.refundIfPaidOnline(any(), org.mockito.ArgumentMatchers.anyLong(), anyString()))
            .thenThrow(new ApiException(409, "A loja desconectou o Mercado Pago. Estorne pelo painel do Mercado Pago ou reconecte a conta."));

        assertThatThrownBy(() -> orders.changeStatus(CUSTOMER, 40, "cancel", null, "mudei de ideia"))
            .isInstanceOf(ApiException.class)
            .hasMessage("Não foi possível estornar o pagamento agora. Tente de novo em instantes ou fale com a loja.")
            .satisfies(error -> assertThat(((ApiException) error).status()).isEqualTo(409));
    }

    @Test
    void storeKeepsTheOriginalMessageWhenTheAutomaticRefundFails() {
        storedOrder(40, "placed");
        when(payments.refundIfPaidOnline(any(), org.mockito.ArgumentMatchers.anyLong(), anyString()))
            .thenThrow(new ApiException(409, "A loja desconectou o Mercado Pago. Estorne pelo painel do Mercado Pago ou reconecte a conta."));

        assertThatThrownBy(() -> orders.changeStatus(RESTAURANT, 40, "reject", null, "sem ingrediente"))
            .isInstanceOf(ApiException.class)
            .hasMessage("A loja desconectou o Mercado Pago. Estorne pelo painel do Mercado Pago ou reconecte a conta.");
    }

    @Test
    void retryAfterTheProviderAlreadyRefundedCancelsNormally() {
        // Primeira tentativa estornou no provedor mas falhou depois; o webhook marcou `refunded`. Na nova
        // tentativa não há o que estornar (`refundIfPaidOnline` devolve false) e o cancelamento segue.
        storedOrder(40, "placed");
        when(payments.refundIfPaidOnline(8L, 40L, "Estorno automático: pedido cancelado pelo cliente")).thenReturn(false);

        Map<String, Object> result = orders.changeStatus(CUSTOMER, 40, "cancel", null, "mudei de ideia");

        assertThat(result).containsEntry("status", "cancelled").doesNotContainKey("paymentRefunded");
        verify(jdbc).update("UPDATE orders SET status = ? WHERE id = ?", "cancelled", 40L);
        verify(payments).cancelPending(40L);
    }

    @Test
    void storeRejectionUsesTheRejectionNote() {
        storedOrder(40, "placed");

        orders.changeStatus(RESTAURANT, 40, "reject", null, "sem ingrediente");

        org.mockito.InOrder ordem = org.mockito.Mockito.inOrder(payments, jdbc);
        ordem.verify(jdbc).update("UPDATE orders SET status = ? WHERE id = ?", "rejected", 40L);
        ordem.verify(payments).refundIfPaidOnline(2L, 40L, "Estorno automático: pedido recusado pela loja");
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

        verify(jdbc).queryForList(org.mockito.ArgumentMatchers.endsWith("FROM orders WHERE id = ? FOR UPDATE SKIP LOCKED"), eq(41L));
        verify(payments, org.mockito.Mockito.never()).refundIfPaidOnline(any(), org.mockito.ArgumentMatchers.anyLong(), anyString());
        verify(jdbc, org.mockito.Mockito.never()).update(org.mockito.ArgumentMatchers.contains("'expired'"), any(Object[].class));
    }

    @Test
    void expirySkipsAnOrderLockedBySomeoneElse() {
        // SKIP LOCKED: outra transação (aceite, cancelamento com estorno no Mercado Pago) segura a linha e a
        // releitura volta vazia — fica para a próxima leitura, sem estornar nem expirar.
        staleOrders(41L);
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.startsWith("SELECT status, created_at, scheduled_at"), any(Object[].class))).thenReturn(List.of());

        orders.list(ADMIN);

        verify(payments, org.mockito.Mockito.never()).refundIfPaidOnline(any(), org.mockito.ArgumentMatchers.anyLong(), anyString());
        verify(jdbc, org.mockito.Mockito.never()).update(org.mockito.ArgumentMatchers.contains("'expired'"), any(Object[].class));
        verify(payments, org.mockito.Mockito.never()).cancelPending(org.mockito.ArgumentMatchers.anyLong());
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
    void expiryRefundsAsTheLastStep() {
        staleOrders(41L);
        lockedReread("placed", 1L);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        orders.list(ADMIN);

        org.mockito.InOrder ordem = org.mockito.Mockito.inOrder(payments, jdbc);
        ordem.verify(jdbc).update("UPDATE orders SET status = 'expired' WHERE id = ? AND status = 'placed'", 41L);
        ordem.verify(payments).cancelPending(41L);
        ordem.verify(payments).refundIfPaidOnline(null, 41L, "Estorno automático: pedido expirado sem aceite");
    }

    @Test
    void failedExpiryRefundDoesNotStopTheOthers() {
        // Cada pedido expira na própria transação (REQUIRES_NEW, ver eachStaleOrderExpiresInItsOwnTransaction):
        // a falha no estorno de um desfaz só ele, e ele continua `placed` para a próxima leitura.
        staleOrders(41L, 42L);
        lockedReread("placed", 1L);
        when(payments.refundIfPaidOnline(null, 41L, "Estorno automático: pedido expirado sem aceite"))
            .thenThrow(new ApiException(409, "A loja desconectou o Mercado Pago. Estorne pelo painel do Mercado Pago ou reconecte a conta."));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        orders.list(ADMIN);

        verify(payments).refundIfPaidOnline(null, 42L, "Estorno automático: pedido expirado sem aceite");
        verify(jdbc).update("UPDATE orders SET status = 'expired' WHERE id = ? AND status = 'placed'", 42L);
    }

    @Test
    void stockReturnsWhenTheOrderDiesBeforePreparation() {
        storedOrder(40, "placed");

        orders.changeStatus(CUSTOMER, 40, "cancel", null, "mudei de ideia");

        verify(jdbc).update(org.mockito.ArgumentMatchers.startsWith("UPDATE products p JOIN"), eq(40L));
    }

    @Test
    void stockStaysWhenTheStoreHadAlreadyAccepted() {
        storedOrder(40, "accepted");

        orders.changeStatus(ADMIN, 40, "cancel", null, "cliente pediu por telefone");

        verify(jdbc, org.mockito.Mockito.never()).update(org.mockito.ArgumentMatchers.startsWith("UPDATE products p JOIN"), any(Object[].class));
    }

    @Test
    void expiredOrderReturnsItsStock() {
        staleOrders(41L);
        lockedReread("placed", 1L);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        orders.list(ADMIN);

        verify(jdbc).update(org.mockito.ArgumentMatchers.startsWith("UPDATE products p JOIN"), eq(41L));
    }

    @Test
    void storeCannotCompleteADeliveryOrder() {
        // Antes a loja concluía a entrega direto de "pronto", pulando entregador e pagamento.
        storedOrder(40, "ready", "delivery");

        assertThatThrownBy(() -> orders.changeStatus(RESTAURANT, 40, "complete", null, null))
            .isInstanceOf(ApiException.class)
            .hasMessage("Esta ação não se aplica a este tipo de pedido");
        verify(jdbc, org.mockito.Mockito.never()).update(org.mockito.ArgumentMatchers.startsWith("UPDATE orders SET status"), any(Object[].class));
    }

    @Test
    void storeCompletesATakeAwayOrder() {
        storedOrder(40, "ready", "take_away");

        assertThat(orders.changeStatus(RESTAURANT, 40, "complete", null, null)).containsEntry("status", "completed");
    }

    @Test
    void orderTypeRulesMirrorTheKitchenApp() {
        OrderService.requireOrderTypeAllows("serve", "dine_in");
        OrderService.requireOrderTypeAllows("complete", "dine_in");
        OrderService.requireOrderTypeAllows("complete", "take_away");
        OrderService.requireOrderTypeAllows("assign", "delivery");
        OrderService.requireOrderTypeAllows("accept", "take_away");
        for (String[] denied : new String[][] { {"serve", "take_away"}, {"serve", "delivery"}, {"complete", "delivery"},
                {"assign", "dine_in"}, {"pickup", "take_away"}, {"deliver", "dine_in"}, {"fail", "take_away"} }) {
            assertThatThrownBy(() -> OrderService.requireOrderTypeAllows(denied[0], denied[1]))
                .as(denied[0] + " em " + denied[1]).isInstanceOf(ApiException.class);
        }
    }

    // Hora do datetime-local é a da loja (Fortaleza, UTC-3). Às 18:00 locais (21:00 UTC), agendar para 19:00
    // locais é daqui a 1h — antes o servidor comparava 19:00 com 21:00 UTC e recusava.
    @Test
    void scheduleUsesTheRestaurantTimezone() {
        java.time.Instant now = java.time.Instant.parse("2026-10-06T21:00:00Z");
        long delay = OrderService.scheduleDelaySeconds(java.time.LocalDateTime.parse("2026-10-06T19:00"),
            java.time.ZoneId.of("America/Fortaleza"), now);
        assertThat(delay).isEqualTo(3600L);
    }

    @Test
    void scheduleRequiresFifteenMinutesAndAtMostSevenDays() {
        java.time.Instant now = java.time.Instant.parse("2026-10-06T21:00:00Z");
        java.time.ZoneId fortaleza = java.time.ZoneId.of("America/Fortaleza");
        assertThatThrownBy(() -> OrderService.scheduleDelaySeconds(java.time.LocalDateTime.parse("2026-10-06T18:10"), fortaleza, now))
            .isInstanceOf(ApiException.class).hasMessageContaining("15 minutos");
        assertThatThrownBy(() -> OrderService.scheduleDelaySeconds(java.time.LocalDateTime.parse("2026-10-13T18:30"), fortaleza, now))
            .isInstanceOf(ApiException.class).hasMessageContaining("7 dias");
    }
}
