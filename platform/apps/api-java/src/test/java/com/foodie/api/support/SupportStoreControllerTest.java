package com.foodie.api.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.hours.RestaurantHoursService;
import jakarta.servlet.http.Cookie;
import java.time.LocalTime;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SupportStoreController.class)
class SupportStoreControllerTest {
    private static final Cookie SESSION = new Cookie("foodie_session", "s");
    private final User admin = new User(1, "Ana Suporte", "ana@demo.local", "admin", null);

    @Autowired private MockMvc mvc;
    @MockitoBean private AuthService auth;
    @MockitoBean private AdminPermissionService permissions;
    @MockitoBean private SupportActionService support;
    @MockitoBean private RestaurantHoursService hours;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(auth.requireUser("s", "admin")).thenReturn(admin);
        when(support.act(any(), anyLong(), anyString(), anyString(), any(), anyString(), any(), any()))
            .thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(7)).get());
    }

    @Test
    void addsOvernightIntervalWithReason() throws Exception {
        when(hours.add(7L, 5, LocalTime.of(18, 0), LocalTime.of(2, 0))).thenReturn(4L);
        mvc.perform(post("/admin/support/restaurants/7/hours").cookie(SESSION)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Loja pediu ajuda no horário de sexta\",\"data\":{\"dayOfWeek\":5,\"opensAt\":\"18:00\",\"closesAt\":\"02:00\"}}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(4))
            .andExpect(jsonPath("$.overnight").value(true));
    }

    @Test
    void pausesTheStoreForAWhile() throws Exception {
        when(hours.pause(7L, 120, "Cozinha alagada, loja pediu pausa")).thenReturn(Map.of("until", "2026-10-01T15:00:00Z", "reason", "Cozinha alagada, loja pediu pausa"));
        mvc.perform(post("/admin/support/restaurants/7/pause").cookie(SESSION)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"minutes\":120,\"reason\":\"Cozinha alagada, loja pediu pausa\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.until").value("2026-10-01T15:00:00Z"));
        verify(support).act(eq(admin), eq(7L), eq("store.pause"), eq("restaurant"), eq(7L), eq("Loja pausada por 120 min"), eq("Cozinha alagada, loja pediu pausa"), any());
    }

    @Test
    void pauseLongerThan72HoursIs400() throws Exception {
        mvc.perform(post("/admin/support/restaurants/7/pause").cookie(SESSION)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"minutes\":5000,\"reason\":\"Cozinha alagada, loja pediu pausa\"}"))
            .andExpect(status().isBadRequest());
        verify(hours, never()).pause(anyLong(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    void resumeWithoutPauseIs409() throws Exception {
        org.mockito.Mockito.doThrow(new ApiException(409, "A loja não está pausada")).when(hours).resume(7L);
        mvc.perform(delete("/admin/support/restaurants/7/pause").cookie(SESSION)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Problema resolvido pela loja\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void discountRouteNoLongerExists() throws Exception {
        mvc.perform(patch("/admin/support/restaurants/7/discount").cookie(SESSION)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Loja pediu para zerar o desconto\",\"data\":{\"percent\":0}}"))
            // Nenhum método mapeado nesse caminho: 404 (não 405, que exige outro método no mesmo caminho).
            .andExpect(status().is4xxClientError());
    }
}
