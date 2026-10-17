package com.foodie.api.permissions;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RoleController.class)
@Import(PermissionService.class)
class RoleControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;

    @MockitoBean
    private RoleService roles;

    @MockitoBean
    private RoleRepository roleRepository;

    @MockitoBean
    private AdminPermissionService adminPermissions;

    @Test
    void ownerListsRoles() throws Exception {
        when(auth.requireUser("s", "restaurant", "kitchen")).thenReturn(new User(5, "Dono", "dono@demo.local", "restaurant", 7L));
        when(roleRepository.findForUser(5)).thenReturn(Optional.empty());
        when(roles.list(7L)).thenReturn(List.of(new RoleRepository.Role(1, 7, "Cozinha", List.of(Permissions.ORDERS_VIEW))));

        mvc.perform(get("/restaurant/roles").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Cozinha"))
            .andExpect(jsonPath("$[0].permissions[0]").value("orders.view"));
    }

    @Test
    void kitchenWithoutStaffManageIsForbidden() throws Exception {
        when(auth.requireUser("k", "restaurant", "kitchen")).thenReturn(new User(6, "Cozinha", "cozinha@demo.local", "kitchen", 7L));
        when(roleRepository.findForUser(6)).thenReturn(Optional.empty());

        mvc.perform(get("/restaurant/roles").cookie(new jakarta.servlet.http.Cookie("foodie_session", "k")))
            .andExpect(status().isForbidden());
    }

    @Test
    void ownerCreatesRole() throws Exception {
        when(auth.requireUser("s", "restaurant", "kitchen")).thenReturn(new User(5, "Dono", "dono@demo.local", "restaurant", 7L));
        when(roleRepository.findForUser(5)).thenReturn(Optional.empty());
        when(roles.create(eq(7L), eq("Garçom"), eq(List.of(Permissions.ORDERS_VIEW))))
            .thenReturn(new RoleRepository.Role(3, 7, "Garçom", List.of(Permissions.ORDERS_VIEW)));

        mvc.perform(post("/restaurant/roles").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Garçom\",\"permissions\":[\"orders.view\"]}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(3));
    }

    @Test
    void staffWithTeamPermissionCannotRemoveItsRoleOrCreateOwnerAccess() throws Exception {
        when(auth.requireUser("s", "restaurant", "kitchen")).thenReturn(new User(8, "Equipe", "e@demo.local", "restaurant", 7L));
        when(roleRepository.findForUser(8)).thenReturn(Optional.of(new RoleRepository.Role(3, 7, "Gerente", List.of(Permissions.STAFF_MANAGE))));
        mvc.perform(patch("/restaurant/staff/8/role").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"staffRoleId\":null}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/restaurant/staff").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Novo acesso\",\"email\":\"novo@demo.local\",\"password\":\"senha-teste-12345\",\"role\":\"restaurant\"}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(roles);
    }

    @Test
    void staffCannotEscalateByChangingOrCreatingPermissionRoles() throws Exception {
        when(auth.requireUser("s", "restaurant", "kitchen")).thenReturn(new User(8, "Equipe", "e@demo.local", "restaurant", 7L));
        when(roleRepository.findForUser(8)).thenReturn(Optional.of(new RoleRepository.Role(3, 7, "Gerente", List.of(Permissions.STAFF_MANAGE))));
        mvc.perform(post("/restaurant/roles").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Financeiro\",\"permissions\":[\"payments.manage\"]}"))
            .andExpect(status().isForbidden());
        mvc.perform(patch("/restaurant/roles/3").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"permissions\":[\"payments.manage\"]}"))
            .andExpect(status().isForbidden());
        mvc.perform(delete("/restaurant/roles/3").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s")))
            .andExpect(status().isForbidden());
        verifyNoInteractions(roles);
    }

    @Test
    void ownerCanAssignARestrictedRoleToStaff() throws Exception {
        when(auth.requireUser("s", "restaurant", "kitchen")).thenReturn(new User(5, "Dono", "d@demo.local", "restaurant", 7L));
        when(roleRepository.findForUser(5)).thenReturn(Optional.empty());
        mvc.perform(patch("/restaurant/staff/8/role").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"staffRoleId\":3}"))
            .andExpect(status().isOk());
        org.mockito.Mockito.verify(roles).assignStaffRole(7, 8, 3L);
    }

    @Test
    void permissionCatalogRequiresLogin() throws Exception {
        when(auth.requireUser(null)).thenThrow(new ApiException(401, "Faça login para continuar"));
        mvc.perform(get("/permissions")).andExpect(status().isUnauthorized());
    }

    @Test
    void kitchenSeesOnlyItsEffectivePermissions() throws Exception {
        when(auth.requireUser("k")).thenReturn(new User(6, "Cozinha", "cozinha@demo.local", "kitchen", 7L));
        when(roleRepository.findForUser(6)).thenReturn(Optional.empty());

        mvc.perform(get("/me/permissions").cookie(new jakarta.servlet.http.Cookie("foodie_session", "k")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("kitchen"))
            .andExpect(jsonPath("$.permissions[?(@=='orders.accept')]").exists())
            .andExpect(jsonPath("$.permissions[?(@=='catalog.manage')]").doesNotExist());
    }
}
