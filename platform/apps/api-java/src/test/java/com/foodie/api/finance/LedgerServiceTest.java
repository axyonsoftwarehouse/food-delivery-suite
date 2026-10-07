package com.foodie.api.finance;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LedgerServiceTest {
    private final LedgerRepository ledger = mock(LedgerRepository.class);
    private final LedgerService service = new LedgerService(ledger);

    @Test
    void reversalInsertsOpposites() {
        when(ledger.orderReversed(20)).thenReturn(false);
        Map<String, Object> customer = new HashMap<>();
        customer.put("party", "customer");
        customer.put("party_id", 7L);
        customer.put("amount_cents", 9000L);
        when(ledger.forOrder(20)).thenReturn(List.of(customer));

        service.reverseOrder(20);

        verify(ledger).insert("customer", 7L, 20L, "refund", -9000, "Estorno do pedido #20");
    }

    @Test
    void skipsReversalWhenAlreadyReversed() {
        when(ledger.orderReversed(21)).thenReturn(true);

        service.reverseOrder(21);

        verify(ledger, never()).forOrder(21);
    }
}
