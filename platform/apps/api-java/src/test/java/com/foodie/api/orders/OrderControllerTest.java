package com.foodie.api.orders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.admin.AdminPermissions;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.permissions.PermissionService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderController.class)
class OrderControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;

    @MockitoBean
    private OrderService orders;

    @MockitoBean
    private JdbcTemplate jdbc;

    @MockitoBean
    private PermissionService permissions;

    @MockitoBean
    private AdminPermissionService adminPermissions;

    @Test
    void unauthenticatedRequestGetsPrototypeError() throws Exception {
        when(auth.requireUser(null)).thenThrow(new ApiException(401, "Faça login para continuar"));
        mvc.perform(patch("/orders/12/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"accept\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Faça login para continuar"));
    }

    @Test
    void historyUsesCursorPaginationAndRejectsInvalidLimit() throws Exception {
        when(auth.requireUser("session")).thenReturn(new User(7, "Cliente", "cliente@demo.local", "customer", null));
        when(orders.history(any(), eq("delivered"), eq(null), eq(20))).thenReturn(Map.of("items", List.of(Map.of("id", 5)), "nextCursor", 4L));

        mvc.perform(get("/orders/history").cookie(new jakarta.servlet.http.Cookie("foodie_session", "session")).param("status", "delivered"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nextCursor").value(4));
        mvc.perform(get("/orders/history").cookie(new jakarta.servlet.http.Cookie("foodie_session", "session")).param("limit", "99"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void lookupFindsOrderByNumberWithViewerScope() throws Exception {
        User restaurant = new User(9, "Loja", "loja@demo.local", "restaurant", 3L);
        when(auth.requireUser("session")).thenReturn(restaurant);
        when(orders.lookup(restaurant, 26)).thenReturn(Map.of("id", 26, "restaurant_id", 3));
        when(orders.lookup(restaurant, 27)).thenThrow(new ApiException(404, "Pedido não encontrado"));

        mvc.perform(get("/orders/lookup").cookie(new jakarta.servlet.http.Cookie("foodie_session", "session")).param("id", "26"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(26));
        mvc.perform(get("/orders/lookup").cookie(new jakarta.servlet.http.Cookie("foodie_session", "session")).param("id", "27"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("Pedido não encontrado"));
        mvc.perform(get("/orders/lookup").cookie(new jakarta.servlet.http.Cookie("foodie_session", "session")).param("id", "0"))
            .andExpect(status().isBadRequest());
        org.mockito.Mockito.verify(permissions, org.mockito.Mockito.atLeastOnce()).require(restaurant, com.foodie.api.permissions.Permissions.ORDERS_VIEW);
    }

    @Test
    void restrictedAdminCannotViewOrChangeOrders() throws Exception {
        User admin = new User(1, "Conteúdo", "conteudo@demo.local", "admin", null);
        when(auth.requireUser("session")).thenReturn(admin);
        doThrow(new ApiException(403, "Acesso não autorizado")).when(adminPermissions)
            .requireIfAdmin(admin, AdminPermissions.ORDERS_MANAGE, AdminPermissions.SUPPORT_VIEW);
        doThrow(new ApiException(403, "Acesso não autorizado")).when(adminPermissions)
            .requireIfAdmin(admin, AdminPermissions.ORDERS_MANAGE);

        mvc.perform(get("/orders").cookie(new jakarta.servlet.http.Cookie("foodie_session", "session")))
            .andExpect(status().isForbidden());
        mvc.perform(patch("/orders/12/status").cookie(new jakarta.servlet.http.Cookie("foodie_session", "session"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"cancel\",\"reason\":\"teste\"}"))
            .andExpect(status().isForbidden());
        org.mockito.Mockito.verifyNoInteractions(orders);
    }
}
