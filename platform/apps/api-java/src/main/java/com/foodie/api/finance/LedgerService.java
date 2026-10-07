package com.foodie.api.finance;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Razão de créditos do cliente e histórico financeiro anterior à assinatura.
 * A plataforma não repassa valores de pedido: frete e gorjeta são da loja, e o
 * entregador é remunerado fora da plataforma (decisão de 05/10/2026).
 */
@Service
public class LedgerService {
    private final LedgerRepository ledger;
    public LedgerService(LedgerRepository ledger) {
        this.ledger = ledger;
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
