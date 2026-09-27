package com.foodie.api.finance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminAuditService;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.admin.AdminPermissions;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FinanceController.class)
class FinanceControllerTest {
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
    private CommissionRepository commissions;

    @MockitoBean
    private LedgerService ledger;

    @MockitoBean
    private PayoutService payouts;

    @MockitoBean
    private ExpenseRepository expenses;

    @Test
    void returnsCommissionForFullAdmin() throws Exception {
        when(auth.requireUser("s", "admin")).thenReturn(new User(1, "Admin", "admin@demo.local", "admin", null));
        doNothing().when(permissions).require(any(), eq(AdminPermissions.FINANCE_VIEW));
        when(commissions.global()).thenReturn(Optional.of(new BigDecimal("10")));
        when(commissions.rules()).thenReturn(List.of(Map.of("scope", "global", "percent", 10)));

        mvc.perform(get("/admin/finance/commission").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.global").value(10));
    }

    @Test
    void restrictedAdminIsForbidden() throws Exception {
        when(auth.requireUser("s", "admin")).thenReturn(new User(2, "Suporte", "suporte@demo.local", "admin", null));
        doThrow(new ApiException(403, "Acesso não autorizado")).when(permissions).require(any(), eq(AdminPermissions.FINANCE_VIEW));

        mvc.perform(get("/admin/finance/commission").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s")))
            .andExpect(status().isForbidden());
    }
}
