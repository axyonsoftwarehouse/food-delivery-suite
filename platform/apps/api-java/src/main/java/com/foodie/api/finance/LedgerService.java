package com.foodie.api.finance;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Razão financeiro (E15). É a fonte única de saldos: saldo de uma parte é a soma dos lançamentos.
 * Lançamentos nunca são editados; correções entram como lançamento compensatório.
 */
@Service
public class LedgerService {
    public static final Set<String> COMPLETED = Set.of("delivered", "completed", "served");

    private final LedgerRepository ledger;
    private final CommissionRepository commissions;

    public LedgerService(LedgerRepository ledger, CommissionRepository commissions) {
        this.ledger = ledger;
        this.commissions = commissions;
    }

    public BigDecimal commissionFor(long restaurantId) {
        return commissions.forRestaurant(restaurantId).or(() -> commissions.global()).orElse(BigDecimal.ZERO);
    }

    /** Lança a venda de um pedido concluído e pago. Idempotente. */
    @Transactional
    public void postOrder(long orderId) {
        if (ledger.orderPosted(orderId)) return;
        Map<String, Object> order = ledger.orderFinance(orderId).orElse(null);
        if (order == null) return;
        if (!COMPLETED.contains(String.valueOf(order.get("status")))) return;
        if (!"paid".equals(order.get("payment_status"))) return;

        long restaurantId = number(order, "restaurant_id");
        long subtotal = number(order, "subtotal_cents");
        long serviceFee = number(order, "service_fee_cents");
        long fee = number(order, "delivery_fee_cents");
        long tip = order.get("tip_cents") == null ? 0 : number(order, "tip_cents");
        Long courierId = order.get("courier_id") == null ? null : number(order, "courier_id");
        long commission = Math.round(subtotal * commissionFor(restaurantId).doubleValue() / 100.0);

        ledger.insert("restaurant", restaurantId, orderId, "sale", subtotal + serviceFee, "Venda do pedido #" + orderId);
        if (commission > 0) {
            ledger.insert("restaurant", restaurantId, orderId, "commission", -commission, "Comissão do pedido #" + orderId);
            ledger.insert("admin", null, orderId, "commission", commission, "Comissão do pedido #" + orderId);
        }
        if (courierId != null && fee > 0) {
            ledger.insert("courier", courierId, orderId, "delivery_fee", fee, "Taxa de entrega do pedido #" + orderId);
        }
        if (courierId != null && tip > 0) {
            ledger.insert("courier", courierId, orderId, "tip", tip, "Gorjeta do pedido #" + orderId);
        }
    }

    /** Estorna um pedido reembolsado com lançamentos compensatórios. Idempotente. */
    @Transactional
    public void reverseOrder(long orderId) {
        if (ledger.orderReversed(orderId)) return;
        List<Map<String, Object>> entries = ledger.forOrder(orderId);
        for (Map<String, Object> entry : entries) {
            String party = (String) entry.get("party");
            Long partyId = entry.get("party_id") == null ? null : number(entry, "party_id");
            long amount = number(entry, "amount_cents");
            ledger.insert(party, partyId, orderId, "refund", -amount, "Estorno do pedido #" + orderId);
        }
    }

    public long balance(Party party) {
        return ledger.sum(party.party(), party.id());
    }

    public List<Map<String, Object>> statement(Party party, String from, String to, int limit) {
        return ledger.list(party.party(), party.id(), blank(from), blank(to), limit);
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }
}
