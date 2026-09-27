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
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CustomerController.class)
class CustomerControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;

    @MockitoBean
    private AdminPermissionService permissions;

    @MockitoBean
    private AdminAuditService audit;

    @MockitoBean
    private CustomerRepository customers;

    @MockitoBean
    private com.foodie.api.rewards.RewardsService rewards;

    @Test
    void listsCustomersForFullAdmin() throws Exception {
        when(auth.requireUser("s", "admin")).thenReturn(new User(1, "Admin", "admin@demo.local", "admin", null));
        doNothing().when(permissions).require(any(), eq(AdminPermissions.CUSTOMERS_MANAGE));
        when(customers.list(any(), any(), eq(30))).thenReturn(List.of(
            Map.of("id", 5, "name", "Cliente A", "orders", 2, "spend_cents", 5000)));

        mvc.perform(get("/admin/customers").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].name").value("Cliente A"))
            .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void restrictedAdminIsForbidden() throws Exception {
        when(auth.requireUser("s", "admin")).thenReturn(new User(2, "Suporte", "suporte@demo.local", "admin", null));
        doThrow(new ApiException(403, "Acesso não autorizado")).when(permissions).require(any(), eq(AdminPermissions.CUSTOMERS_MANAGE));

        mvc.perform(get("/admin/customers").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s")))
            .andExpect(status().isForbidden());
    }

    @Test
    void detailReturnsNotFound() throws Exception {
        when(auth.requireUser("s", "admin")).thenReturn(new User(1, "Admin", "admin@demo.local", "admin", null));
        doNothing().when(permissions).require(any(), eq(AdminPermissions.CUSTOMERS_MANAGE));
        when(customers.find(99)).thenReturn(Optional.empty());

        mvc.perform(get("/admin/customers/99").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s")))
            .andExpect(status().isNotFound());
    }
}
