package com.foodie.api.orders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminAuditService;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.admin.AdminPermissions;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.payments.OnlinePaymentService;
import com.foodie.api.payments.PaymentGatewayRegistry;
import com.foodie.api.payments.accounts.PaymentAccountService;
import com.foodie.api.permissions.PermissionService;
import com.foodie.api.permissions.Permissions;
import com.foodie.api.support.SupportActionService;
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

@WebMvcTest(PaymentController.class)
class PaymentControllerRefundTest {
    private static final Cookie SESSION = new Cookie("foodie_session", "s");
    private final User owner = new User(5, "Dona", "dona@cantina.com.br", "restaurant", 3L);
    private final User admin = new User(1, "Ana Suporte", "ana@demo.local", "admin", null);

    @Autowired private MockMvc mvc;
    @MockitoBean private AuthService auth;
    @MockitoBean private PaymentService payments;
    @MockitoBean private OnlinePaymentService online;
    @MockitoBean private PaymentGatewayRegistry gateways;
    @MockitoBean private PaymentAccountService accounts;
    @MockitoBean private PermissionService permissions;
    @MockitoBean private AdminPermissionService adminPermissions;
    @MockitoBean private SupportActionService support;
    @MockitoBean private AdminAuditService audit;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(support.act(any(), anyLong(), anyString(), anyString(), any(), anyString(), any(), any()))
            .thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(7)).get());
    }

    @Test
    void storeRefundsWithPaymentsPermissionAndIsAudited() throws Exception {
        when(auth.requireUser("s", "restaurant", "admin")).thenReturn(owner);
        when(payments.refund(owner, 30, "cliente desistiu")).thenReturn(Map.of("status", "refunded"));
        mvc.perform(post("/orders/30/payment/refund").cookie(SESSION).contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"cliente desistiu\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("refunded"));
        verify(permissions).require(owner, Permissions.PAYMENTS_MANAGE);
        verify(audit).record(eq(owner), eq("order.refund"), eq("order"), eq(30L), anyString());
        verify(support, never()).act(any(), anyLong(), anyString(), anyString(), any(), anyString(), any(), any());
    }

    @Test
    void storeWithoutPermissionIs403() throws Exception {
        when(auth.requireUser("s", "restaurant", "admin")).thenReturn(owner);
        doThrow(new ApiException(403, "Acesso não autorizado")).when(permissions).require(owner, Permissions.PAYMENTS_MANAGE);
        mvc.perform(post("/orders/30/payment/refund").cookie(SESSION).contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"x\"}"))
            .andExpect(status().isForbidden());
        verify(payments, never()).refund(any(), anyLong(), any());
    }

    @Test
    void supportRefundsInTheStoresNameWithReason() throws Exception {
        when(auth.requireUser("s", "restaurant", "admin")).thenReturn(admin);
        when(payments.restaurantOf(30)).thenReturn(3L);
        when(payments.refund(admin, 30, "Loja pediu estorno por telefone")).thenReturn(Map.of("status", "refunded"));
        mvc.perform(post("/orders/30/payment/refund").cookie(SESSION).contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Loja pediu estorno por telefone\"}"))
            .andExpect(status().isOk());
        verify(adminPermissions).require(admin, AdminPermissions.SUPPORT_ACT);
        verify(support).act(eq(admin), eq(3L), eq("order.refund"), eq("order"), eq(30L), eq("Pedido #30 estornado"),
            eq("Loja pediu estorno por telefone"), any());
    }

    @Test
    void adminWithoutSupportActIs403() throws Exception {
        when(auth.requireUser("s", "restaurant", "admin")).thenReturn(admin);
        doThrow(new ApiException(403, "Acesso não autorizado")).when(adminPermissions).require(admin, AdminPermissions.SUPPORT_ACT);
        mvc.perform(post("/orders/30/payment/refund").cookie(SESSION).contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Loja pediu estorno por telefone\"}"))
            .andExpect(status().isForbidden());
        verify(payments, never()).refund(any(), anyLong(), any());
    }

    @Test
    void supportWithShortReasonIs400() throws Exception {
        when(auth.requireUser("s", "restaurant", "admin")).thenReturn(admin);
        mvc.perform(post("/orders/30/payment/refund").cookie(SESSION).contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"curto\"}"))
            .andExpect(status().isBadRequest());
        verify(payments, never()).refund(any(), anyLong(), any());
    }
}
