package com.foodie.api.restaurant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.PasswordVerifier;
import com.foodie.api.auth.User;
import com.foodie.api.permissions.PermissionService;
import com.foodie.api.permissions.Permissions;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Entregadores da loja (decisão de 08/10/2026: o entregador é exclusivo de uma loja). */
@WebMvcTest(RestaurantCourierController.class)
class RestaurantCourierControllerTest {
    private static final User OWNER = new User(2, "Loja", "loja@demo.local", "restaurant", 3L);
    private static final Cookie SESSION = new Cookie("foodie_session", "s");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;

    @MockitoBean
    private PermissionService permissions;

    @MockitoBean
    private PasswordVerifier passwords;

    @MockitoBean
    private JdbcTemplate jdbc;

    @MockitoBean
    private com.foodie.api.courier.CourierShiftService shifts;

    @Test
    void listsOnlyTheStoresCouriers() throws Exception {
        when(auth.requireUser("s", "restaurant", "kitchen")).thenReturn(OWNER);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of("id", 12, "name", "Bia")));

        mvc.perform(get("/restaurant/couriers").cookie(SESSION))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Bia"));

        verify(jdbc).queryForList(org.mockito.ArgumentMatchers.contains("role = 'courier' AND restaurant_id = ?"), eq(3L));
        // Quem despacha precisa da lista para escolher; quem gerencia, para cadastrar.
        verify(permissions).require(OWNER, Permissions.COURIERS_MANAGE, Permissions.ORDERS_DISPATCH);
    }

    @Test
    void listCarriesTheRatingAndHidesTheAverageBelowFiveReviews() throws Exception {
        when(auth.requireUser("s", "restaurant", "kitchen")).thenReturn(OWNER);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
            Map.of("id", 12, "name", "Bia", "rating_count", 5L, "rating_sum", 24L),
            Map.of("id", 13, "name", "Caio", "rating_count", 2L, "rating_sum", 9L)));

        mvc.perform(get("/restaurant/couriers").cookie(SESSION))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].ratingCount").value(5))
            .andExpect(jsonPath("$[0].ratingAverage").value(4.8))
            .andExpect(jsonPath("$[0].rating_sum").doesNotExist())
            .andExpect(jsonPath("$[1].ratingCount").value(2))
            .andExpect(jsonPath("$[1].ratingAverage").doesNotExist());
    }

    @Test
    void createsAnApprovedCourierBoundToTheStore() throws Exception {
        when(auth.requireUser("s", "restaurant", "kitchen")).thenReturn(OWNER);
        when(passwords.hash("senha-forte-123")).thenReturn("hash");
        when(jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, "bia@exemplo.com")).thenReturn(12L);

        mvc.perform(post("/restaurant/couriers").cookie(SESSION).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Bia\",\"email\":\"Bia@Exemplo.com\",\"password\":\"senha-forte-123\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(12))
            .andExpect(jsonPath("$.approved").value(true));

        verify(permissions).require(OWNER, Permissions.COURIERS_MANAGE);
        verify(jdbc).update("INSERT INTO users (name, email, password_hash, role, restaurant_id, courier_approved_at) VALUES (?, ?, ?, 'courier', ?, NOW())",
            "Bia", "bia@exemplo.com", "hash", 3L);
    }

    @Test
    void refusesADuplicatedEmail() throws Exception {
        when(auth.requireUser("s", "restaurant", "kitchen")).thenReturn(OWNER);
        when(jdbc.query(eq("SELECT 1 FROM users WHERE email = ?"), any(ResultSetExtractor.class), eq("bia@exemplo.com"))).thenReturn(1);

        mvc.perform(post("/restaurant/couriers").cookie(SESSION).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Bia\",\"email\":\"bia@exemplo.com\",\"password\":\"senha-forte-123\"}"))
            .andExpect(status().isConflict());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void dispatcherWithoutManagePermissionCannotCreate() throws Exception {
        when(auth.requireUser("s", "restaurant", "kitchen")).thenReturn(OWNER);
        doThrow(new ApiException(403, "Acesso não autorizado")).when(permissions).require(OWNER, Permissions.COURIERS_MANAGE);

        mvc.perform(post("/restaurant/couriers").cookie(SESSION).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Bia\",\"email\":\"bia@exemplo.com\",\"password\":\"senha-forte-123\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void suspendsOnlyACourierOfTheStore() throws Exception {
        when(auth.requireUser("s", "restaurant", "kitchen")).thenReturn(OWNER);
        when(jdbc.query(org.mockito.ArgumentMatchers.contains("restaurant_id = ?"), any(ResultSetExtractor.class), eq(12L), eq(3L))).thenReturn(1);

        mvc.perform(patch("/restaurant/couriers/12/suspension").cookie(SESSION).contentType(MediaType.APPLICATION_JSON)
                .content("{\"suspended\":true,\"reason\":\"faltou ao turno\"}"))
            .andExpect(status().isOk());
        verify(auth).setSuspended(12L, true, "faltou ao turno");

        // Entregador de outra loja: 404, sem revelar que existe.
        mvc.perform(patch("/restaurant/couriers/13/suspension").cookie(SESSION).contentType(MediaType.APPLICATION_JSON)
                .content("{\"suspended\":true}"))
            .andExpect(status().isNotFound());
        verify(auth, never()).setSuspended(eq(13L), any(Boolean.class), any());
    }

    @Test
    void approvesOnlyACourierOfTheStore() throws Exception {
        when(auth.requireUser("s", "restaurant", "kitchen")).thenReturn(OWNER);
        when(jdbc.update(org.mockito.ArgumentMatchers.contains("courier_approved_at"), eq(12L), eq(3L))).thenReturn(1);

        mvc.perform(patch("/restaurant/couriers/12/approval").cookie(SESSION)).andExpect(status().isOk());
        mvc.perform(patch("/restaurant/couriers/13/approval").cookie(SESSION)).andExpect(status().isNotFound());
    }

    @Test
    @SuppressWarnings("unchecked")
    void suspendingClosesTheShiftAndClearsThePosition() throws Exception {
        when(auth.requireUser("s", "restaurant", "kitchen")).thenReturn(OWNER);
        when(jdbc.query(startsWith("SELECT 1 FROM users WHERE id = ?"), any(ResultSetExtractor.class), any(Object[].class))).thenReturn(1);
        mvc.perform(patch("/restaurant/couriers/12/suspension").cookie(SESSION).contentType(MediaType.APPLICATION_JSON)
                .content("{\"suspended\":true,\"reason\":\"Faltou ao turno\"}"))
            .andExpect(status().isOk());
        verify(shifts).closeForSuspension(12L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void reactivatingDoesNotTouchTheShift() throws Exception {
        when(auth.requireUser("s", "restaurant", "kitchen")).thenReturn(OWNER);
        when(jdbc.query(startsWith("SELECT 1 FROM users WHERE id = ?"), any(ResultSetExtractor.class), any(Object[].class))).thenReturn(1);
        mvc.perform(patch("/restaurant/couriers/12/suspension").cookie(SESSION).contentType(MediaType.APPLICATION_JSON)
                .content("{\"suspended\":false}"))
            .andExpect(status().isOk());
        verify(shifts, never()).closeForSuspension(anyLong());
    }
}
