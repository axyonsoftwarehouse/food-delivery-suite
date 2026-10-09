package com.foodie.api.courier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.permissions.PermissionService;
import com.foodie.api.permissions.Permissions;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CourierReputationController.class)
class CourierReputationControllerTest {
    private static final User COURIER = new User(9, "Bia", "bia@demo.local", "courier", 3L);
    private static final User STORE = new User(2, "Loja", "loja@demo.local", "restaurant", 3L);
    private static final Cookie SESSION = new Cookie("foodie_session", "s");
    private static final String OWNERSHIP = "SELECT 1 FROM users WHERE id = ? AND role = 'courier' AND restaurant_id = ?";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;

    @MockitoBean
    private PermissionService permissions;

    @MockitoBean
    private CourierReputationService reputation;

    @MockitoBean
    private JdbcTemplate jdbc;

    @Test
    void courierSeesOwnReputation() throws Exception {
        when(auth.requireUser("s", "courier")).thenReturn(COURIER);
        when(reputation.forCourier(9)).thenReturn(Map.of("count", 3));
        mvc.perform(get("/courier/reputation").cookie(SESSION))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(3));
        verify(reputation).forCourier(9);
    }

    @Test
    void storeSeesReputationOfItsCourier() throws Exception {
        when(auth.requireUser("s", "restaurant", "kitchen")).thenReturn(STORE);
        when(jdbc.query(eq(OWNERSHIP), any(ResultSetExtractor.class), eq(7L), eq(3L))).thenReturn(1);
        when(reputation.forCourier(7)).thenReturn(Map.of("count", 6));
        mvc.perform(get("/restaurant/couriers/7/reputation").cookie(SESSION))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(6));
        verify(permissions).require(STORE, Permissions.COURIERS_MANAGE);
    }

    @Test
    void courierOfAnotherStoreIs404() throws Exception {
        when(auth.requireUser("s", "restaurant", "kitchen")).thenReturn(STORE);
        mvc.perform(get("/restaurant/couriers/7/reputation").cookie(SESSION)).andExpect(status().isNotFound());
        verify(reputation, never()).forCourier(7);
    }

    @Test
    void withoutPermissionIs403() throws Exception {
        when(auth.requireUser("s", "restaurant", "kitchen")).thenReturn(STORE);
        doThrow(new ApiException(403, "Acesso não autorizado")).when(permissions).require(STORE, Permissions.COURIERS_MANAGE);
        mvc.perform(get("/restaurant/couriers/7/reputation").cookie(SESSION)).andExpect(status().isForbidden());
        verify(reputation, never()).forCourier(7);
    }
}
