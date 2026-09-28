package com.foodie.api.finance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LedgerServiceTest {
    private final LedgerRepository ledger = mock(LedgerRepository.class);
    private final LedgerService service = new LedgerService(ledger);

    private Map<String, Object> order(Long courierId, long subtotal, long fee, long serviceFee, String status, String paymentStatus) {
        Map<String, Object> row = new HashMap<>();
        row.put("restaurant_id", 1L);
        row.put("courier_id", courierId);
        row.put("subtotal_cents", subtotal);
        row.put("delivery_fee_cents", fee);
        row.put("service_fee_cents", serviceFee);
        row.put("status", status);
        row.put("payment_status", paymentStatus);
        return row;
    }

    @Test
    void postsCourierFeesWithoutMerchantWalletOrCommission() {
        when(ledger.orderPosted(10)).thenReturn(false);
        when(ledger.orderFinance(10)).thenReturn(Optional.of(order(5L, 10000, 500, 0, "delivered", "paid")));
        when(ledger.markOrderPosted(10)).thenReturn(true);

        service.postOrder(10);

        verify(ledger).insert("courier", 5L, 10L, "delivery_fee", 500, "Taxa de entrega do pedido #10");
        verify(ledger, never()).insert(org.mockito.ArgumentMatchers.eq("restaurant"), any(), any(), anyString(), anyLong(), anyString());
    }

    @Test
    void skipsWhenOrderNotCompletedOrUnpaid() {
        when(ledger.orderPosted(11)).thenReturn(false);
        when(ledger.orderFinance(11)).thenReturn(Optional.of(order(null, 10000, 0, 0, "accepted", "paid")));
        service.postOrder(11);
        verify(ledger, never()).insert(anyString(), any(), any(), anyString(), anyLong(), anyString());
    }

    @Test
    void skipsWhenAlreadyPosted() {
        when(ledger.orderPosted(12)).thenReturn(true);
        service.postOrder(12);
        verify(ledger, never()).orderFinance(12);
    }

    @Test
    void skipsWhenAnotherTransactionPostedFirst() {
        when(ledger.orderFinance(13)).thenReturn(Optional.of(order(5L, 10000, 500, 0, "delivered", "paid")));
        when(ledger.markOrderPosted(13)).thenReturn(false);
        service.postOrder(13);
        verify(ledger, never()).insert(anyString(), any(), any(), anyString(), anyLong(), anyString());
    }

    @Test
    void reversalInsertsOpposites() {
        when(ledger.orderReversed(20)).thenReturn(false);
        Map<String, Object> restaurant = new HashMap<>();
        restaurant.put("party", "restaurant");
        restaurant.put("party_id", 1L);
        restaurant.put("amount_cents", 9000L);
        when(ledger.forOrder(20)).thenReturn(List.of(restaurant));

        service.reverseOrder(20);

        verify(ledger).insert("restaurant", 1L, 20L, "refund", -9000, "Estorno do pedido #20");
    }
}
