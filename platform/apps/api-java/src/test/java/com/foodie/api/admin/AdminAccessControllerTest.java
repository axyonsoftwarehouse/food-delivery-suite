package com.foodie.api.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminAccessController.class)
class AdminAccessControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;

    @MockitoBean
    private AdminPermissionService permissions;

    @MockitoBean
    private AdminAccessService access;

    @MockitoBean
    private AdminAuditService audit;

    @Test
    void fullAdminListsRoles() throws Exception {
        when(auth.requireUser("s", "admin")).thenReturn(new User(1, "Admin", "admin@demo.local", "admin", null));
        doNothing().when(permissions).require(any(), eq(AdminPermissions.ADMIN_MANAGE));
        when(access.listRoles()).thenReturn(List.of(
            new AdminRoleRepository.Role(3, "Suporte", "Atendimento", List.of(AdminPermissions.ORDERS_MANAGE), true)));

        mvc.perform(get("/admin/roles").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Suporte"))
            .andExpect(jsonPath("$[0].permissions[0]").value("orders.manage"));
    }

    @Test
    void restrictedAdminIsForbidden() throws Exception {
        when(auth.requireUser("s", "admin")).thenReturn(new User(2, "Suporte", "suporte@demo.local", "admin", null));
        doThrow(new ApiException(403, "Acesso não autorizado")).when(permissions).require(any(), eq(AdminPermissions.ADMIN_MANAGE));

        mvc.perform(get("/admin/roles").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s")))
            .andExpect(status().isForbidden());
    }

    @Test
    void createsAdminEmployee() throws Exception {
        when(auth.requireUser("s", "admin")).thenReturn(new User(1, "Admin", "admin@demo.local", "admin", null));
        doNothing().when(permissions).require(any(), eq(AdminPermissions.ADMIN_MANAGE));
        when(access.createEmployee(eq("Maria"), eq("maria@demo.local"), eq("senha-bem-longa"), eq(3L)))
            .thenReturn(new AdminRoleRepository.Employee(8, "Maria", "maria@demo.local", 3L, false));

        mvc.perform(post("/admin/employees").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Maria\",\"email\":\"maria@demo.local\",\"password\":\"senha-bem-longa\",\"adminRoleId\":3}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(8));
    }

    @Test
    void auditRequiresItsPermission() throws Exception {
        when(auth.requireUser("s", "admin")).thenReturn(new User(3, "Auditor", "auditor@demo.local", "admin", null));
        doThrow(new ApiException(403, "Acesso não autorizado")).when(permissions).require(any(), eq(AdminPermissions.AUDIT_VIEW));

        mvc.perform(get("/admin/audit").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s")))
            .andExpect(status().isForbidden());
    }

    @Test
    void auditReturnsEntries() throws Exception {
        when(auth.requireUser("s", "admin")).thenReturn(new User(1, "Admin", "admin@demo.local", "admin", null));
        doNothing().when(permissions).require(any(), eq(AdminPermissions.AUDIT_VIEW));
        when(audit.list(any(), any(), any(), any(), any(), any())).thenReturn(List.of(
            new AdminAuditRepository.Entry(5, 1L, "Admin", "create", "zone", 7L, "Zona Fortaleza", "2026-09-27T10:00:00Z")));

        mvc.perform(get("/admin/audit").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].entity").value("zone"))
            .andExpect(jsonPath("$.items[0].summary").value("Zona Fortaleza"));
    }
}
