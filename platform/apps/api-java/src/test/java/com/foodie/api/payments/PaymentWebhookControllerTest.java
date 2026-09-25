package com.foodie.api.payments;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
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

    @Test
    void ignoresNonPaymentEvents() throws Exception {
        mvc.perform(post("/webhooks/mercadopago").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"merchant_order\",\"data\":{\"id\":\"1\"}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ignored").value(true));
        verify(online, never()).handleWebhook(anyString());
    }

    @Test
    void rejectsInvalidSignature() throws Exception {
        when(online.verifySignature(any(), any(), any(), any())).thenReturn(false);
        mvc.perform(post("/webhooks/mercadopago").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"payment\",\"data\":{\"id\":\"123\"}}"))
            .andExpect(status().isUnauthorized());
        verify(online, never()).handleWebhook(anyString());
    }

    @Test
    void delegatesValidPaymentWebhook() throws Exception {
        when(online.verifySignature(any(), any(), any(), any())).thenReturn(true);
        when(online.handleWebhook("123")).thenReturn(Map.of("ok", true, "status", "paid"));
        mvc.perform(post("/webhooks/mercadopago").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"payment\",\"data\":{\"id\":\"123\"}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("paid"));
        verify(online).handleWebhook("123");
    }
}
