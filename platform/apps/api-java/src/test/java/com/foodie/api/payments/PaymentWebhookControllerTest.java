package com.foodie.api.payments;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PaymentWebhookController.class)
class PaymentWebhookControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OnlinePaymentService online;

    @MockitoBean
    private PaymentGatewayRegistry gateways;

    @MockitoBean
    private PaymentGateway gateway;

    @Test
    void ignoresNonPaymentEvents() throws Exception {
        when(gateways.resolve("mercadopago")).thenReturn(gateway);
        when(gateway.webhookChargeId(any())).thenReturn(Optional.empty());

        mvc.perform(post("/webhooks/mercadopago").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"merchant_order\",\"data\":{\"id\":\"1\"}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ignored").value(true));
        verify(online, never()).handleWebhook(any(), any());
    }

    @Test
    void rejectsInvalidSignature() throws Exception {
        when(gateways.resolve("mercadopago")).thenReturn(gateway);
        when(gateway.webhookChargeId(any())).thenReturn(Optional.of("123"));
        when(gateway.verifyWebhook(any())).thenReturn(false);

        mvc.perform(post("/webhooks/mercadopago").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"payment\",\"data\":{\"id\":\"123\"}}"))
            .andExpect(status().isUnauthorized());
        verify(online, never()).handleWebhook(any(), any());
    }

    @Test
    void delegatesValidPaymentWebhook() throws Exception {
        when(gateways.resolve("mercadopago")).thenReturn(gateway);
        when(gateway.provider()).thenReturn("mercadopago");
        when(gateway.webhookChargeId(any())).thenReturn(Optional.of("123"));
        when(gateway.verifyWebhook(any())).thenReturn(true);
        when(online.handleWebhook("mercadopago", "123")).thenReturn(Map.of("ok", true, "status", "paid"));

        mvc.perform(post("/webhooks/mercadopago").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"payment\",\"data\":{\"id\":\"123\"}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("paid"));
        verify(online).handleWebhook("mercadopago", "123");
    }
}
