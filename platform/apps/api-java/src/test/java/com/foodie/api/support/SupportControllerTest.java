package com.foodie.api.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminAuditRepository;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.admin.AdminPermissions;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.hours.RestaurantHoursService;
import com.foodie.api.permissions.PermissionService;
import com.foodie.api.permissions.Permissions;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SupportController.class)
class SupportControllerTest {
    private static final Cookie SESSION = new Cookie("foodie_session", "s");
    private final User admin = new User(1, "Ana Suporte", "ana@demo.local", "admin", null);
    private final User owner = new User(5, "Dona", "dona@demo.local", "restaurant", 7L);

    @Autowired private MockMvc mvc;
    @MockitoBean private AuthService auth;
    @MockitoBean private AdminPermissionService adminPermissions;
    @MockitoBean private PermissionService storePermissions;
    @MockitoBean private SupportQueryService query;
    @MockitoBean private AdminAuditRepository audit;
    @MockitoBean private RestaurantHoursService hours;

    @Test
    void searchesStores() throws Exception {
        when(auth.requireUser("s", "admin")).thenReturn(admin);
        when(query.search("cozinha", 20)).thenReturn(List.of(Map.of("id", 7L, "name", "Cozinha Demo")));
        mvc.perform(get("/admin/support/restaurants").param("q", "cozinha").cookie(SESSION))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Cozinha Demo"));
    }

    @Test
    void searchWithoutViewPermissionIsForbidden() throws Exception {
        when(auth.requireUser("s", "admin")).thenReturn(admin);
        doThrow(new ApiException(403, "Acesso não autorizado")).when(adminPermissions).require(admin, AdminPermissions.SUPPORT_VIEW);
        mvc.perform(get("/admin/support/restaurants").cookie(SESSION)).andExpect(status().isForbidden());
    }

    @Test
    void auditTrailShowsReason() throws Exception {
        when(auth.requireUser("s", "admin")).thenReturn(admin);
        when(audit.listForRestaurant(7L, null, 50)).thenReturn(List.of(new AdminAuditRepository.SupportEntry(
            3L, "Ana Suporte", "product.update", "product", 11L, "Produto #11 pausado", "Item com recall do fornecedor", "2026-10-01T12:00:00Z")));
        mvc.perform(get("/admin/support/restaurants/7/audit").cookie(SESSION))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].reason").value("Item com recall do fornecedor"))
            .andExpect(jsonPath("$[0].actorName").value("Ana Suporte"));
    }

    @Test
    void storeSeesInterventionsSignedAsFoodieSupport() throws Exception {
        when(auth.requireUser("s", "restaurant")).thenReturn(owner);
        when(audit.listForRestaurant(7L, null, 50)).thenReturn(List.of(new AdminAuditRepository.SupportEntry(
            3L, "Ana Suporte", "product.update", "product", 11L, "Produto #11 pausado", "Item com recall do fornecedor", "2026-10-01T12:00:00Z")));
        when(hours.pauseInfo(7L)).thenReturn(null);
        mvc.perform(get("/restaurant/support-log").cookie(SESSION))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entries[0].actorName").value("Suporte Foodie"))
            .andExpect(jsonPath("$.entries[0].reason").value("Item com recall do fornecedor"));
    }

    @Test
    void staffWithoutStaffManageCannotReadTheLog() throws Exception {
        User cook = new User(6, "Cozinheiro", "cozinha@demo.local", "restaurant", 7L);
        when(auth.requireUser("s", "restaurant")).thenReturn(cook);
        doThrow(new ApiException(403, "Acesso não autorizado")).when(storePermissions).require(eq(cook), eq(Permissions.STAFF_MANAGE));
        mvc.perform(get("/restaurant/support-log").cookie(SESSION)).andExpect(status().isForbidden());
    }
}
