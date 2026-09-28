package com.foodie.api.restaurant;

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
import com.foodie.api.admin.ModuleAccessService;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.permissions.PermissionService;
import com.foodie.api.permissions.Permissions;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InventoryController.class)
class InventoryControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;

    @MockitoBean
    private PermissionService permissions;

    @MockitoBean
    private ModuleAccessService modules;

    @MockitoBean
    private JdbcTemplate jdbc;

    @Test
    void ownerListsSuppliers() throws Exception {
        when(auth.requireUser("s", "restaurant", "kitchen")).thenReturn(new User(3, "Dono", "dono@demo.local", "restaurant", 1L));
        doNothing().when(permissions).require(any(), eq(Permissions.INVENTORY_MANAGE));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of("id", 1, "name", "Atacadão")));

        mvc.perform(get("/restaurant/suppliers").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Atacadão"));
    }

    @Test
    void staffWithoutPermissionIsForbidden() throws Exception {
        when(auth.requireUser("k", "restaurant", "kitchen")).thenReturn(new User(6, "Cozinha", "cozinha@demo.local", "kitchen", 1L));
        doThrow(new ApiException(403, "Acesso não autorizado")).when(permissions).require(any(), eq(Permissions.INVENTORY_MANAGE));

        mvc.perform(get("/restaurant/inventory").cookie(new jakarta.servlet.http.Cookie("foodie_session", "k")))
            .andExpect(status().isForbidden());
    }

    @Test
    void disabledModuleIsForbiddenEvenWithPermission() throws Exception {
        when(auth.requireUser("s", "restaurant", "kitchen")).thenReturn(new User(3, "Dono", "dono@demo.local", "restaurant", 1L));
        doThrow(new ApiException(403, "Módulo não habilitado para esta loja")).when(modules).require(1L, "inventory");

        mvc.perform(get("/restaurant/suppliers").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s")))
            .andExpect(status().isForbidden());
    }
}
