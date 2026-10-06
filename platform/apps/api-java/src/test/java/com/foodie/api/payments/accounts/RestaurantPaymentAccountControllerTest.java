package com.foodie.api.payments.accounts;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RestaurantPaymentAccountController.class)
class RestaurantPaymentAccountControllerTest {
    private static final Cookie SESSION = new Cookie("foodie_session", "s");
    private final User owner = new User(5, "Dona", "dona@cantina.com.br", "restaurant", 3L);

    @Autowired private MockMvc mvc;
    @MockitoBean private AuthService auth;
    @MockitoBean private PaymentAccountService accounts;

    @Test
    void ownerSeesTheStatusOfTheOwnStore() throws Exception {
        when(auth.requireUser("s", "restaurant")).thenReturn(owner);
        when(accounts.status(3)).thenReturn(Map.of("status", "connected", "nickname", "TESTUSER4062"));
        mvc.perform(get("/restaurant/payment-account").cookie(SESSION))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("connected"));
    }

    @Test
    void connectReturnsTheAuthorizationUrl() throws Exception {
        when(auth.requireUser("s", "restaurant")).thenReturn(owner);
        when(accounts.startConnection(owner)).thenReturn("https://auth.mercadopago.com.br/authorization?x");
        mvc.perform(post("/restaurant/payment-account/mercadopago/connect").cookie(SESSION))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authorizationUrl").value("https://auth.mercadopago.com.br/authorization?x"));
    }

    @Test
    void ownerDisconnects() throws Exception {
        when(auth.requireUser("s", "restaurant")).thenReturn(owner);
        when(accounts.disconnectByOwner(owner)).thenReturn(Map.of("status", "disconnected"));
        mvc.perform(delete("/restaurant/payment-account").cookie(SESSION))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("disconnected"));
        verify(accounts).disconnectByOwner(owner);
    }

    @Test
    void otherRolesAreRefused() throws Exception {
        when(auth.requireUser("s", "restaurant")).thenThrow(new ApiException(403, "Acesso não autorizado"));
        mvc.perform(get("/restaurant/payment-account").cookie(SESSION)).andExpect(status().isForbidden());
    }
}
