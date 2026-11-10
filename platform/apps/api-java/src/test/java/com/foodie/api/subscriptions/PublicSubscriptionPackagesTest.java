package com.foodie.api.subscriptions;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.admin.AdminAuditService;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.EmailVerificationGuard;
import com.foodie.api.permissions.PermissionService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Planos na página "Para restaurantes" (S01): sem login, só planos ativos e só nome, preço e período. */
@WebMvcTest(SubscriptionController.class)
@TestPropertySource(properties = "app.subscriptions.trial-days=15")
class PublicSubscriptionPackagesTest {
    @Autowired private MockMvc mvc;

    @MockitoBean private AuthService auth;
    @MockitoBean private EmailVerificationGuard verification;
    @MockitoBean private AdminPermissionService permissions;
    @MockitoBean private AdminAuditService audit;
    @MockitoBean private JdbcTemplate jdbc;
    @MockitoBean private SubscriptionChargeRepository charges;
    @MockitoBean private PermissionService restaurantPermissions;
    @MockitoBean private SubscriptionBillingService billing;
    @MockitoBean private SubscriptionBillingAccount account;

    @Test
    void listsActivePlansWithoutLoginAndOnlyThePublicFields() throws Exception {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("name", "Essencial");
        plan.put("price_cents", 9900);
        plan.put("period_days", 30);
        when(jdbc.queryForList(argThat((String sql) -> sql != null && sql.contains("subscription_packages")))).thenReturn(List.of(plan));

        mvc.perform(get("/public/subscription-packages"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trial_days").value(15))
            .andExpect(jsonPath("$.packages[0].name").value("Essencial"))
            .andExpect(jsonPath("$.packages[0].price_cents").value(9900))
            .andExpect(jsonPath("$.packages[0].period_days").value(30))
            .andExpect(jsonPath("$.packages[0]", not(hasKey("id"))))
            .andExpect(jsonPath("$.packages[0]", not(hasKey("commission_percent"))))
            .andExpect(jsonPath("$.packages[0]", not(hasKey("active"))));

        // A consulta escolhe só as colunas públicas e só os planos ativos, do mais barato ao mais caro.
        verify(jdbc).queryForList(argThat((String sql) -> sql.startsWith("SELECT name, price_cents, period_days FROM subscription_packages")
            && sql.contains("WHERE active = TRUE") && sql.contains("ORDER BY price_cents")));
        verify(auth, org.mockito.Mockito.never()).requireUser(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(String[].class));
    }

    @Test
    void withoutActivePlansReturnsAnEmptyList() throws Exception {
        when(jdbc.queryForList(argThat((String sql) -> sql != null && sql.contains("subscription_packages")))).thenReturn(List.of());

        mvc.perform(get("/public/subscription-packages"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.packages").isEmpty())
            .andExpect(jsonPath("$.trial_days").value(15));
    }
}
