package com.foodie.api.payments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Ponto de entrada da preferência: sandbox quando a credencial é de teste, produção quando não é. */
class MercadoPagoGatewayTest {
    private Map<String, Object> preference(String initPoint, String sandboxInitPoint) {
        Map<String, Object> preference = new LinkedHashMap<>();
        if (initPoint != null) preference.put("init_point", initPoint);
        if (sandboxInitPoint != null) preference.put("sandbox_init_point", sandboxInitPoint);
        return preference;
    }

    @Test
    void testCredentialUsesSandboxEntryPoint() {
        assertEquals("https://sandbox.mercadopago.com.br/x",
            MercadoPagoGateway.chooseInitPoint(preference("https://www.mercadopago.com.br/x", "https://sandbox.mercadopago.com.br/x"), true));
    }

    @Test
    void realCredentialUsesProductionEntryPoint() {
        assertEquals("https://www.mercadopago.com.br/x",
            MercadoPagoGateway.chooseInitPoint(preference("https://www.mercadopago.com.br/x", "https://sandbox.mercadopago.com.br/x"), false));
    }

    @Test
    void fallsBackWhenTheProviderDoesNotSendTheSandboxPoint() {
        assertEquals("https://www.mercadopago.com.br/x",
            MercadoPagoGateway.chooseInitPoint(preference("https://www.mercadopago.com.br/x", null), true));
        assertNull(MercadoPagoGateway.chooseInitPoint(preference(null, null), true));
    }
}
