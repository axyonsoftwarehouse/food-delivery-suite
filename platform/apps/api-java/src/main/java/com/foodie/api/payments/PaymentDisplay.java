package com.foodie.api.payments;

import java.util.Locale;

/**
 * O que a tela mostra de um pagamento online. O código do provedor ({@code raw_status}) fica para o suporte;
 * o cliente lê o motivo em português (roteiro de 11/10/2026: "HTTP 402 … rejected_by_issuer" na tela).
 */
public final class PaymentDisplay {
    private PaymentDisplay() {}

    /** Prazo para pagar: 15 min desde a criação, só enquanto o pedido online aguarda pagamento. */
    public static final String PAY_DEADLINE_COLUMN = "(SELECT o.created_at + INTERVAL 15 MINUTE FROM orders o"
        + " WHERE o.id = order_payments.order_id AND o.status = 'placed'"
        + " AND order_payments.modality = 'online' AND order_payments.status <> 'paid') AS pay_deadline";

    public static String rejectionMessage(String rawStatus) {
        String code = rawStatus == null ? "" : rawStatus.trim().toLowerCase(Locale.ROOT);
        if (code.equals("cc_rejected_duplicated_payment")) return "Este cartão já foi recusado neste pagamento. Use outro cartão.";
        if (code.equals("rejected_by_issuer") || code.equals("cc_rejected_call_for_authorize") || code.equals("cc_rejected_card_disabled")) {
            return "O banco emissor não autorizou.";
        }
        if (code.equals("amount_limit_exceeded") || code.equals("cc_rejected_insufficient_amount") || code.equals("insufficient_amount")) {
            return "Limite ou saldo insuficiente.";
        }
        if (code.startsWith("cc_rejected_bad_filled") || code.equals("invalid_card_data") || code.equals("bad_filled_card_data")) {
            return "Algum dado do cartão está incorreto.";
        }
        if (code.equals("cc_rejected_high_risk") || code.equals("high_risk") || code.equals("cc_rejected_blacklist")) {
            return "Recusado pela análise de segurança.";
        }
        return "O pagamento não foi autorizado.";
    }
}
