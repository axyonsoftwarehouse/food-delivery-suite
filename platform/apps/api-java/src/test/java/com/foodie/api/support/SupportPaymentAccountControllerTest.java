package com.foodie.api.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.admin.AdminPermissions;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.payments.accounts.PaymentAccountService;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SupportPaymentAccountController.class)
class SupportPaymentAccountControllerTest {
    private static final Cookie SESSION = new Cookie("foodie_session", "s");
    private final User admin = new User(1, "Ana Suporte", "ana@demo.local", "admin", null);

    @Autowired private MockMvc mvc;
    @MockitoBean private AuthService auth;
    @MockitoBean private AdminPermissionService permissions;
    @MockitoBean private SupportActionService support;
    @MockitoBean private PaymentAccountService accounts;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(auth.requireUser("s", "admin")).thenReturn(admin);
        when(support.act(any(), anyLong(), anyString(), anyString(), any(), anyString(), any(), any()))
            .thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(7)).get());
    }

    @Test
    void supportSeesTheStatusWithViewPermission() throws Exception {
        when(accounts.status(7)).thenReturn(Map.of("status", "connected"));
        mvc.perform(get("/admin/support/restaurants/7/payment-account").cookie(SESSION))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("connected"));
        verify(permissions).require(admin, AdminPermissions.SUPPORT_VIEW);
    }

    @Test
    void supportDisconnectsWithReason() throws Exception {
        when(accounts.status(7)).thenReturn(Map.of("status", "disconnected"));
        mvc.perform(post("/admin/support/restaurants/7/payment-account/disconnect").cookie(SESSION)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Loja pediu ao suporte para desconectar\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("disconnected"));
        verify(permissions).require(admin, AdminPermissions.SUPPORT_ACT);
        verify(support).act(eq(admin), eq(7L), eq("payment_account.disconnect"), eq("restaurant"), eq(7L),
            eq("Mercado Pago desconectado"), eq("Loja pediu ao suporte para desconectar"), any());
        verify(accounts).disconnect(7, 1, "Loja pediu ao suporte para desconectar");
    }

    @Test
    void withoutActPermissionNothingChanges() throws Exception {
        doThrow(new ApiException(403, "Acesso não autorizado")).when(permissions).require(admin, AdminPermissions.SUPPORT_ACT);
        mvc.perform(post("/admin/support/restaurants/7/payment-account/disconnect").cookie(SESSION)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Loja pediu ao suporte para desconectar\"}"))
            .andExpect(status().isForbidden());
        verify(accounts, never()).disconnect(anyLong(), anyLong(), anyString());
    }
}
