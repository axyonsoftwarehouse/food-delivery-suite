package com.foodie.api.payments;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.permissions.PermissionService;
import com.foodie.api.permissions.Permissions;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OfflinePaymentController.class)
class OfflinePaymentControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;

    @MockitoBean
    private OfflinePaymentService offline;

    @MockitoBean
    private PermissionService permissions;

    @MockitoBean
    private com.foodie.api.admin.AdminPermissionService adminPermissions;

    @Test
    void listRequiresLogin() throws Exception {
        when(auth.requireUser(null)).thenThrow(new ApiException(401, "Faça login para continuar"));
        mvc.perform(get("/offline-payment-methods")).andExpect(status().isUnauthorized());
    }

    @Test
    void customerSubmitsProof() throws Exception {
        when(auth.requireUser("s", "customer", "admin")).thenReturn(new User(7, "Cliente", "cliente@demo.local", "customer", null));
        when(offline.submitProof(any(), eq(15L), eq(3L), eq("https://cdn.foodie.local/prova.png"), eq("pago no pix")))
            .thenReturn(Map.of("orderId", 15L, "status", "pending"));

        mvc.perform(post("/orders/15/payment/offline").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"methodId\":3,\"proofUrl\":\"https://cdn.foodie.local/prova.png\",\"note\":\"pago no pix\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("pending"));
    }

    @Test
    void restaurantWithoutPaymentsPermissionIsForbidden() throws Exception {
        when(auth.requireUser("r", "restaurant", "admin")).thenReturn(new User(5, "Restaurante", "loja@demo.local", "restaurant", 7L));
        doThrow(new ApiException(403, "Acesso não autorizado")).when(permissions).require(any(User.class), eq(Permissions.PAYMENTS_MANAGE));

        mvc.perform(post("/orders/15/payment/verify").cookie(new jakarta.servlet.http.Cookie("foodie_session", "r"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"approve\":true}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCreatesMethod() throws Exception {
        when(auth.requireUser("a", "admin")).thenReturn(new User(1, "Admin", "admin@demo.local", "admin", null));
        when(offline.createMethod(eq("Transferência"), eq("transferencia"), any(), eq(true), eq(true)))
            .thenReturn(Map.of("id", 3L, "name", "Transferência", "slug", "transferencia", "active", true));

        mvc.perform(post("/admin/offline-payment-methods").cookie(new jakarta.servlet.http.Cookie("foodie_session", "a"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Transferência\",\"slug\":\"transferencia\",\"requiresProof\":true}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.slug").value("transferencia"));
    }
}
