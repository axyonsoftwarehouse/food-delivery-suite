package com.foodie.api.payments.accounts;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PaymentOAuthCallbackController.class)
@TestPropertySource(properties = "app.payments.account-return-url=https://restaurante.foodie.test/painel/configuracoes")
class PaymentOAuthCallbackControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private PaymentAccountService accounts;

    @Test
    void successGoesBackToThePanel() throws Exception {
        when(accounts.receiveCallback("TG-c", "st", null)).thenReturn(new PaymentAccountService.Callback("confirmar", "tok123"));
        mvc.perform(get("/payments/mercadopago/oauth/callback").param("code", "TG-c").param("state", "st"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "https://restaurante.foodie.test/painel/configuracoes?mercadopago=confirmar&token=tok123"));
    }

    @Test
    void failureCarriesTheReason() throws Exception {
        when(accounts.receiveCallback(null, "st", "access_denied")).thenReturn(new PaymentAccountService.Callback("negado", null));
        mvc.perform(get("/payments/mercadopago/oauth/callback").param("state", "st").param("error", "access_denied"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "https://restaurante.foodie.test/painel/configuracoes?mercadopago=erro&motivo=negado"));
    }
}
