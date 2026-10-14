package com.foodie.api.payments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** O motivo da recusa que o cliente lê: nunca o código cru do provedor. */
class PaymentDisplayTest {
    @Test
    void translatesTheProviderReason() {
        assertEquals("O banco emissor não autorizou.", PaymentDisplay.rejectionMessage("rejected_by_issuer"));
        assertEquals("O banco emissor não autorizou.", PaymentDisplay.rejectionMessage("cc_rejected_call_for_authorize"));
        assertEquals("Limite ou saldo insuficiente.", PaymentDisplay.rejectionMessage("amount_limit_exceeded"));
        assertEquals("Limite ou saldo insuficiente.", PaymentDisplay.rejectionMessage("cc_rejected_insufficient_amount"));
        assertEquals("Algum dado do cartão está incorreto.", PaymentDisplay.rejectionMessage("cc_rejected_bad_filled_security_code"));
        assertEquals("Algum dado do cartão está incorreto.", PaymentDisplay.rejectionMessage("invalid_card_data"));
        assertEquals("Recusado pela análise de segurança.", PaymentDisplay.rejectionMessage("cc_rejected_high_risk"));
        assertEquals("Recusado pela análise de segurança.", PaymentDisplay.rejectionMessage("high_risk"));
        assertEquals("Este cartão já foi recusado neste pagamento. Use outro cartão.", PaymentDisplay.rejectionMessage("cc_rejected_duplicated_payment"));
    }

    @Test
    void failedBankVerificationHasItsOwnReason() {
        assertEquals("O banco não confirmou a verificação. Tente de novo ou use outro cartão ou o Pix.",
            PaymentDisplay.rejectionMessage("cc_rejected_3ds_challenge"));
    }

    @Test
    void unknownOrMissingReasonGetsTheGenericText() {
        assertEquals("O pagamento não foi autorizado.", PaymentDisplay.rejectionMessage("qualquer_coisa"));
        assertEquals("O pagamento não foi autorizado.", PaymentDisplay.rejectionMessage(null));
        assertEquals("O pagamento não foi autorizado.", PaymentDisplay.rejectionMessage(" "));
    }

    @Test
    void payDeadlineOnlyForAnOpenOnlineOrderNotYetPaid() {
        String sql = PaymentDisplay.PAY_DEADLINE_COLUMN;
        assertTrue(sql.contains("o.status = 'placed'"));
        assertTrue(sql.contains("order_payments.modality = 'online'"));
        assertTrue(sql.contains("order_payments.status IN ('pending', 'rejected', 'cancelled')"));
        assertTrue(sql.contains("INTERVAL 15 MINUTE"));
        assertTrue(sql.endsWith("AS pay_deadline"));
    }
}
