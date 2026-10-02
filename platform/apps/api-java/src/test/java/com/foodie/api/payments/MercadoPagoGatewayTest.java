package com.foodie.api.payments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClientResponseException;

/** Ponto de entrada da preferência e a mensagem que o provedor devolve. */
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

    private RestClientResponseException erro(int status, String corpo) {
        RestClientResponseException error = mock(RestClientResponseException.class);
        when(error.getStatusCode()).thenReturn(HttpStatusCode.valueOf(status));
        when(error.getResponseBodyAsString()).thenReturn(corpo);
        return error;
    }

    @Test
    void surfacesWhatTheProviderSaid() {
        // Os dois casos reais de 02/10: email do pagador recusado e Pix barrado no sandbox.
        assertEquals("HTTP 400 - payer.email must be a valid email",
            MercadoPagoGateway.providerMessage(erro(400, "{\"message\":\"payer.email must be a valid email\",\"error\":\"bad_request\",\"status\":400}")));
        assertEquals("HTTP 401 - Unauthorized use of live credentials",
            MercadoPagoGateway.providerMessage(erro(401, "{\"cause\":[{\"code\":7,\"description\":\"Unauthorized use of live credentials\"}],\"error\":\"unauthorized\"}")));
        assertEquals("HTTP 500", MercadoPagoGateway.providerMessage(erro(500, "")));
        assertEquals("HTTP 500", MercadoPagoGateway.providerMessage(erro(500, "nao e json")));
    }
}
