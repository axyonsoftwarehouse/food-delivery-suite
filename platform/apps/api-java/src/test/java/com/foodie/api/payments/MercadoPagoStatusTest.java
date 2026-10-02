package com.foodie.api.payments;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MercadoPagoStatusTest {
    @Test
    void mapsProviderStatusesToInternalOnes() {
        assertEquals("paid", MercadoPagoStatus.normalize("approved"));
        assertEquals("pending", MercadoPagoStatus.normalize("pending"));
        assertEquals("pending", MercadoPagoStatus.normalize("in_process"));
        assertEquals("pending", MercadoPagoStatus.normalize("authorized"));
        assertEquals("rejected", MercadoPagoStatus.normalize("rejected"));
        assertEquals("cancelled", MercadoPagoStatus.normalize("cancelled"));
        assertEquals("refunded", MercadoPagoStatus.normalize("refunded"));
        assertEquals("refunded", MercadoPagoStatus.normalize("charged_back"));
        assertEquals("expired", MercadoPagoStatus.normalize("expired"));
        assertEquals("pending", MercadoPagoStatus.normalize(null));
        assertEquals("pending", MercadoPagoStatus.normalize("algo_desconhecido"));
    }

    @Test
    void mapsTheStatusesOfTheOrdersApi() {
        // A API de Orders (Checkout Transparente) usa outros nomes: "processed" é o aprovado e
        // "action_required"/"waiting_transfer" é o Pix que ainda espera o pagamento.
        assertEquals("paid", MercadoPagoStatus.normalize("processed"));
        assertEquals("pending", MercadoPagoStatus.normalize("action_required"));
        assertEquals("pending", MercadoPagoStatus.normalize("waiting_transfer"));
        assertEquals("rejected", MercadoPagoStatus.normalize("failed"));
        assertEquals("cancelled", MercadoPagoStatus.normalize("canceled"));
    }
}
