package com.foodie.api.payments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClientResponseException;

/**
 * O formato que a API de Orders cobra e a mensagem que o provedor devolve.
 *
 * <p>Os testes de ponto de entrada de preferência (Checkout Pro) saíram junto com o caminho antigo:
 * o produto desta aplicação é o Checkout Transparente via Orders, e a preferência não é mais chamada.
 */
class MercadoPagoGatewayTest {

    @Test
    void amountsGoAsTextWithTwoDecimals() {
        // A API de Orders espera "50.00" (texto). Mandar 50 e receber recusa por formato seria um 502
        // que ninguém entenderia olhando a tela.
        assertEquals("50.00", MercadoPagoGateway.dinheiro(5000));
        assertEquals("5.00", MercadoPagoGateway.dinheiro(500));
        assertEquals("0.05", MercadoPagoGateway.dinheiro(5));
        assertEquals("0.01", MercadoPagoGateway.dinheiro(1));
        assertEquals("1234.56", MercadoPagoGateway.dinheiro(123456));
        assertEquals("0.00", MercadoPagoGateway.dinheiro(0));
    }

    private RestClientResponseException erro(int status, String corpo) {
        RestClientResponseException error = mock(RestClientResponseException.class);
        when(error.getStatusCode()).thenReturn(HttpStatusCode.valueOf(status));
        when(error.getResponseBodyAsString()).thenReturn(corpo);
        return error;
    }

    @Test
    void surfacesWhatTheProviderSaid() {
        // Os dois casos reais de 02/10: email do pagador recusado e a cobrança na API antiga.
        assertEquals("HTTP 400 - payer.email must be a valid email",
            MercadoPagoGateway.providerMessage(erro(400, "{\"message\":\"payer.email must be a valid email\",\"error\":\"bad_request\",\"status\":400}")));
        assertEquals("HTTP 401 - Unauthorized use of live credentials",
            MercadoPagoGateway.providerMessage(erro(401, "{\"cause\":[{\"code\":7,\"description\":\"Unauthorized use of live credentials\"}],\"error\":\"unauthorized\"}")));
        // E o formato da API de Orders (03/10): o motivo vem em `errors[]`, com o detalhe do campo.
        assertEquals("HTTP 400 - Properties not supported (additionalProperties '$.notification_url' not allowed)",
            MercadoPagoGateway.providerMessage(erro(400, "{\"errors\":[{\"code\":\"unsupported_properties\",\"message\":\"Properties not supported\","
                + "\"details\":[\"additionalProperties '$.notification_url' not allowed\"]}]}")));
        assertEquals("HTTP 500", MercadoPagoGateway.providerMessage(erro(500, "")));
        assertEquals("HTTP 500", MercadoPagoGateway.providerMessage(erro(500, "nao e json")));
    }
}
