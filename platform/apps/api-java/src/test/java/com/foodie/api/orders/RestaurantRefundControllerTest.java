package com.foodie.api.orders;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminAuditService;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.permissions.PermissionService;
import com.foodie.api.permissions.Permissions;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RestaurantRefundController.class)
class RestaurantRefundControllerTest {
    private static final Cookie SESSION = new Cookie("foodie_session", "s");
    private final User owner = new User(5, "Dona", "dona@cantina.com.br", "restaurant", 3L);

    @Autowired private MockMvc mvc;
    @MockitoBean private AuthService auth;
    @MockitoBean private PermissionService permissions;
    @MockitoBean private RefundRequestService refunds;
    @MockitoBean private AdminAuditService audit;

    @Test
    void listsOwnStoreRefunds() throws Exception {
        when(auth.requireUser("s", "restaurant")).thenReturn(owner);
        when(refunds.list(3L, null)).thenReturn(List.of());
        mvc.perform(get("/restaurant/refunds").cookie(SESSION)).andExpect(status().isOk());
        verify(permissions).require(owner, Permissions.PAYMENTS_MANAGE);
        verify(refunds).list(3L, null);
    }

    @Test
    void storeDecidesAndIsAudited() throws Exception {
        when(auth.requireUser("s", "restaurant")).thenReturn(owner);
        when(refunds.decide(owner, 7, "approve", "ok")).thenReturn(Map.of("id", 7L, "status", "approved"));
        mvc.perform(post("/restaurant/refunds/7/decision").cookie(SESSION).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"approve\",\"note\":\"ok\"}"))
            .andExpect(status().isOk());
        verify(refunds).decide(owner, 7, "approve", "ok");
        verify(audit).record(eq(owner), eq("refund.decide"), eq("refund"), eq(7L), anyString());
    }

    @Test
    void withoutPermissionIs403() throws Exception {
        when(auth.requireUser("s", "restaurant")).thenReturn(owner);
        doThrow(new ApiException(403, "Acesso não autorizado")).when(permissions).require(owner, Permissions.PAYMENTS_MANAGE);
        mvc.perform(post("/restaurant/refunds/7/decision").cookie(SESSION).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"approve\",\"note\":\"ok\"}"))
            .andExpect(status().isForbidden());
        verify(refunds, never()).decide(any(), anyLong(), any(), any());
    }

    @Test
    void invalidDecisionIs400() throws Exception {
        when(auth.requireUser("s", "restaurant")).thenReturn(owner);
        mvc.perform(post("/restaurant/refunds/7/decision").cookie(SESSION).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"talvez\"}"))
            .andExpect(status().isBadRequest());
    }
}
