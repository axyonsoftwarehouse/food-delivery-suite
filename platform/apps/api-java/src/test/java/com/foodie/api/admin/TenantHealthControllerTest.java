package com.foodie.api.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.ApiException;
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

@WebMvcTest(TenantHealthController.class)
class TenantHealthControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;

    @MockitoBean
    private AdminPermissionService permissions;

    @MockitoBean
    private JdbcTemplate jdbc;

    @Test
    void returnsTenantHealth() throws Exception {
        when(auth.requireUser("s", "admin")).thenReturn(new User(1, "Admin", "admin@demo.local", "admin", null));
        doNothing().when(permissions).require(any(), eq(AdminPermissions.REPORTS_VIEW));
        Map<String, Object> row = new HashMap<>();
        row.put("id", 1L);
        row.put("name", "Cozinha Demo");
        row.put("active", true);
        row.put("approval", "approved");
        row.put("orders", 10L);
        row.put("completed", 8L);
        row.put("gmv", 20000L);
        row.put("canceled", 1L);
        row.put("last_order_at", "2026-09-27T10:00:00Z");
        row.put("subscription_status", "active");
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row));

        mvc.perform(get("/admin/tenants/health").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tenants[0].name").value("Cozinha Demo"))
            .andExpect(jsonPath("$.tenants[0].gmvCents").value(20000))
            .andExpect(jsonPath("$.totals.gmvCents").value(20000));
    }

    @Test
    void restrictedAdminIsForbidden() throws Exception {
        when(auth.requireUser("s", "admin")).thenReturn(new User(2, "Suporte", "suporte@demo.local", "admin", null));
        doThrow(new ApiException(403, "Acesso não autorizado")).when(permissions).require(any(), eq(AdminPermissions.REPORTS_VIEW));

        mvc.perform(get("/admin/tenants/health").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s")))
            .andExpect(status().isForbidden());
    }
}
