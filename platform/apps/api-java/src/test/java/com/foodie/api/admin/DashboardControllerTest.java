package com.foodie.api.admin;

import static org.mockito.ArgumentMatchers.any;
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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DashboardController.class)
class DashboardControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;

    @MockitoBean
    private AdminPermissionService permissions;

    @MockitoBean
    private DashboardService dashboard;

    @Test
    void returnsSummaryForFullAdmin() throws Exception {
        when(auth.requireUser("s", "admin")).thenReturn(new User(1, "Admin", "admin@demo.local", "admin", null));
        doNothing().when(permissions).require(any(), eq(AdminPermissions.DASHBOARD_VIEW));
        when(dashboard.summary(any(), any(), any())).thenReturn(Map.of(
            "cards", Map.of("orders", 10, "revenueCents", 50000),
            "byDay", List.of(Map.of("day", "2026-09-27", "orders", 3, "revenueCents", 15000, "newUsers", 1))));

        mvc.perform(get("/admin/dashboard").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cards.orders").value(10))
            .andExpect(jsonPath("$.byDay[0].day").value("2026-09-27"));
    }

    @Test
    void restrictedAdminIsForbidden() throws Exception {
        when(auth.requireUser("s", "admin")).thenReturn(new User(2, "Suporte", "suporte@demo.local", "admin", null));
        doThrow(new ApiException(403, "Acesso não autorizado")).when(permissions).require(any(), eq(AdminPermissions.DASHBOARD_VIEW));

        mvc.perform(get("/admin/dashboard").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s")))
            .andExpect(status().isForbidden());
    }
}
