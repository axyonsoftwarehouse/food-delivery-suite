package com.foodie.api.restaurant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

@WebMvcTest(StorefrontController.class)
class StorefrontControllerTest {
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
    void publicStorefrontReturnsData() throws Exception {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
            Map.of("name", "Cozinha Demo", "headline", "Comida caseira", "about", "Feito na hora")));

        mvc.perform(get("/public/restaurants/1/storefront"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Cozinha Demo"));
    }

    @Test
    void ownerWithoutSettingsPermissionIsForbidden() throws Exception {
        when(auth.requireUser("k", "restaurant", "kitchen")).thenReturn(new User(6, "Cozinha", "cozinha@demo.local", "kitchen", 1L));
        doThrow(new ApiException(403, "Acesso não autorizado")).when(permissions).require(any(), eq(Permissions.SETTINGS_MANAGE));

        mvc.perform(get("/restaurant/storefront").cookie(new jakarta.servlet.http.Cookie("foodie_session", "k")))
            .andExpect(status().isForbidden());
    }
}
