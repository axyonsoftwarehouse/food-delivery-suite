package com.foodie.api.orders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.payments.OnlinePaymentService;
import com.foodie.api.payments.PaymentGatewayRegistry;
import com.foodie.api.payments.accounts.PaymentAccountService;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PaymentController.class)
class PaymentControllerPublicConfigTest {
    private static final Cookie SESSION = new Cookie("foodie_session", "s");

    @Autowired private MockMvc mvc;
    @MockitoBean private AuthService auth;
    @MockitoBean private PaymentService payments;
    @MockitoBean private OnlinePaymentService online;
    @MockitoBean private PaymentGatewayRegistry gateways;
    @MockitoBean private PaymentAccountService accounts;

    @BeforeEach
    void setUp() {
        when(auth.requireUser("s")).thenReturn(new User(7, "Cliente", "c@e.com.br", "customer", null));
        when(gateways.defaultProvider()).thenReturn("mercadopago");
        when(online.directChargesAllowed()).thenReturn(true);
    }

    @Test
    void connectedStoreOffersOnlinePaymentWithItsPublicKey() throws Exception {
        when(accounts.publicKeyFor(3)).thenReturn(Optional.of("APP_USR-pk-loja"));
        mvc.perform(get("/payments/public-config").param("restaurantId", "3").cookie(SESSION))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.onlineCharges").value(true))
            .andExpect(jsonPath("$.cardTransparent").value(true))
            .andExpect(jsonPath("$.publicKey").value("APP_USR-pk-loja"));
    }

    @Test
    void storeWithoutAccountOffersNothingOnline() throws Exception {
        when(accounts.publicKeyFor(4)).thenReturn(Optional.empty());
        mvc.perform(get("/payments/public-config").param("restaurantId", "4").cookie(SESSION))
            .andExpect(jsonPath("$.onlineCharges").value(false))
            .andExpect(jsonPath("$.publicKey").value(""));
        mvc.perform(get("/payments/public-config").cookie(SESSION))
            .andExpect(jsonPath("$.onlineCharges").value(false));
    }
}
