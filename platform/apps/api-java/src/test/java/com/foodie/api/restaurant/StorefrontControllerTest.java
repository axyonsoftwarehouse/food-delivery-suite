package com.foodie.api.restaurant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.ApiException;
import com.foodie.api.admin.ModuleAccessService;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.hours.RestaurantHoursService;
import com.foodie.api.permissions.PermissionService;
import com.foodie.api.permissions.Permissions;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
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

    @MockitoBean
    private RestaurantHoursService hours;

    @Test
    void publicStorefrontReturnsData() throws Exception {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
            Map.of("name", "Cozinha Demo", "timezone", "America/Fortaleza", "prep_minutes", 20, "headline", "Comida caseira", "about", "Feito na hora")));
        when(jdbc.queryForMap(anyString(), any(Object[].class))).thenReturn(Map.of(
            "zones", 2L, "fee_min_cents", 0, "fee_max_cents", 599, "minimum_min_cents", 1500, "minimum_max_cents", 2000,
            "minutes_min", 15, "minutes_max", 30));
        when(hours.isOpen(1L, "America/Fortaleza")).thenReturn(true);
        when(hours.list(1L)).thenReturn(List.of(Map.of("dayOfWeek", 1, "opensAt", "11:00", "closesAt", "22:00", "overnight", false)));

        mvc.perform(get("/public/restaurants/1/storefront"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Cozinha Demo"))
            .andExpect(jsonPath("$.timezone").doesNotExist())
            .andExpect(jsonPath("$.open").value(true))
            .andExpect(jsonPath("$.hours[0].opensAt").value("11:00"))
            .andExpect(jsonPath("$.delivery.fee_max_cents").value(599))
            .andExpect(jsonPath("$.delivery.minimum_min_cents").value(1500));
    }

    @Test
    void publicStorefrontWithoutZonesHasNoDelivery() throws Exception {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
            Map.of("name", "Cozinha Demo", "timezone", "America/Fortaleza", "headline", "", "about", "")));
        when(jdbc.queryForMap(anyString(), any(Object[].class))).thenReturn(Map.of("zones", 0L));
        when(hours.isOpen(1L, "America/Fortaleza")).thenReturn(false);
        when(hours.list(1L)).thenReturn(List.of());

        mvc.perform(get("/public/restaurants/1/storefront"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.open").value(false))
            .andExpect(jsonPath("$.delivery").doesNotExist());
    }

    @Test
    void ownerWithoutSettingsPermissionIsForbidden() throws Exception {
        when(auth.requireUser("k", "restaurant", "kitchen")).thenReturn(new User(6, "Cozinha", "cozinha@demo.local", "kitchen", 1L));
        doThrow(new ApiException(403, "Acesso não autorizado")).when(permissions).require(any(), eq(Permissions.SETTINGS_MANAGE));

        mvc.perform(get("/restaurant/storefront").cookie(new jakarta.servlet.http.Cookie("foodie_session", "k")))
            .andExpect(status().isForbidden());
    }

    @Test
    void saveKeepsCoverSentAsSnakeCase() throws Exception {
        when(auth.requireUser("r", "restaurant", "kitchen")).thenReturn(new User(2, "Loja", "restaurante@demo.local", "restaurant", 1L));

        mvc.perform(put("/restaurant/storefront").cookie(new jakarta.servlet.http.Cookie("foodie_session", "r"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"headline\":\"Oi\",\"about\":\"\",\"cover_url\":\"/foodie-burger-hero.png\",\"whatsapp\":\"\",\"instagram\":\"\",\"published\":true}"))
            .andExpect(status().isOk());

        verify(jdbc).update(anyString(), eq(1L), eq("Oi"), eq(""), eq("/foodie-burger-hero.png"), eq(""), eq(""), eq(true));
    }
}
