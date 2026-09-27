package com.foodie.api.orders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminAuditService;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.admin.AdminPermissions;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderExtrasController.class)
class OrderExtrasControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;

    @MockitoBean
    private AdminPermissionService permissions;

    @MockitoBean
    private AdminAuditService audit;

    @MockitoBean
    private JdbcTemplate jdbc;

    @MockitoBean
    private OrderService orders;

    @MockitoBean
    private PaymentService payments;

    @Test
    void cancelReasonsRequireLogin() throws Exception {
        when(auth.requireUser(null)).thenThrow(new ApiException(401, "Faça login para continuar"));
        mvc.perform(get("/order-cancel-reasons")).andExpect(status().isUnauthorized());
    }

    @Test
    void refundsRequireOrderPermission() throws Exception {
        when(auth.requireUser("s", "admin")).thenReturn(new User(2, "Suporte", "suporte@demo.local", "admin", null));
        doThrow(new ApiException(403, "Acesso não autorizado")).when(permissions).require(any(), eq(AdminPermissions.ORDERS_MANAGE));

        mvc.perform(get("/admin/refunds").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s")))
            .andExpect(status().isForbidden());
    }

    @Test
    void invoiceRendersHtml() throws Exception {
        User customer = new User(7, "Cliente", "cliente@demo.local", "customer", null);
        when(auth.requireUser("s")).thenReturn(customer);
        Map<String, Object> detail = new HashMap<>();
        detail.put("id", 5L);
        detail.put("restaurant_name", "Cozinha Demo");
        detail.put("created_at", "2026-09-27T10:00:00Z");
        detail.put("status", "delivered");
        detail.put("delivery_address_text", "Rua 1");
        detail.put("subtotal_cents", 10000L);
        detail.put("delivery_fee_cents", 500L);
        detail.put("service_fee_cents", 0L);
        detail.put("tip_cents", 0L);
        detail.put("discount_cents", 0L);
        detail.put("total_cents", 10500L);
        detail.put("items", List.of(Map.of("name", "Prato", "quantity", 2, "unit_price_cents", 5000L)));
        when(orders.detail(eq(customer), eq(5L))).thenReturn(detail);

        mvc.perform(get("/orders/5/invoice").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s")))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Fatura #5")));
    }
}
