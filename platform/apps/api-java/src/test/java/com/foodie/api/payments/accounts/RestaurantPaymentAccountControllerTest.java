package com.foodie.api.payments.accounts;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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
import com.foodie.api.permissions.PermissionService;
import com.foodie.api.permissions.Permissions;
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
    @MockitoBean private PermissionService permissions;

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

    @Test
    void staffCannotConnect() throws Exception {
        when(auth.requireUser("s", "restaurant")).thenReturn(owner);
        when(permissions.isStaff(owner)).thenReturn(true);
        mvc.perform(post("/restaurant/payment-account/mercadopago/connect").cookie(SESSION)).andExpect(status().isForbidden());
        verify(accounts, never()).startConnection(any());
    }

    @Test
    void staffCannotDisconnect() throws Exception {
        when(auth.requireUser("s", "restaurant")).thenReturn(owner);
        when(permissions.isStaff(owner)).thenReturn(true);
        mvc.perform(delete("/restaurant/payment-account").cookie(SESSION)).andExpect(status().isForbidden());
        verify(accounts, never()).disconnectByOwner(any());
    }

    @Test
    void statusRequiresPaymentsManage() throws Exception {
        when(auth.requireUser("s", "restaurant")).thenReturn(owner);
        doThrow(new ApiException(403, "Acesso não autorizado")).when(permissions).require(owner, Permissions.PAYMENTS_MANAGE);
        mvc.perform(get("/restaurant/payment-account").cookie(SESSION)).andExpect(status().isForbidden());
        verify(accounts, never()).status(org.mockito.ArgumentMatchers.anyLong());
    }
}
