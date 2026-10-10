package com.foodie.api.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.support.SupportActionService;
import jakarta.servlet.http.Cookie;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Suporte liga a uma loja o entregador que ficou sem loja na migração V061 (decisão de 08/10/2026). */
@WebMvcTest(CourierAdminController.class)
class CourierAdminControllerTest {
    private static final User ADMIN = new User(1, "Admin", "admin@demo.local", "admin", null);
    private static final Cookie SESSION = new Cookie("foodie_session", "s");
    private static final String BODY = "{\"restaurantId\":3,\"reason\":\"Entregador confirmou que atende a loja 3\"}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;

    @MockitoBean
    private AdminPermissionService permissions;

    @MockitoBean
    private AdminAuditService audit;

    @MockitoBean
    private SupportActionService support;

    @MockitoBean
    private JdbcTemplate jdbc;

    @MockitoBean
    private com.foodie.api.courier.CourierShiftService shifts;

    @Test
    @SuppressWarnings("unchecked")
    void supportLinksACourierWithoutStore() throws Exception {
        when(auth.requireUser("s", "admin")).thenReturn(ADMIN);
        when(jdbc.query(eq("SELECT 1 FROM users WHERE id = ? AND role = 'courier'"), any(ResultSetExtractor.class), eq(12L))).thenReturn(1);
        when(jdbc.update(anyString(), eq(3L), eq(12L))).thenReturn(1);
        when(support.act(eq(ADMIN), eq(3L), eq("courier.link"), eq("courier"), eq(12L), anyString(), anyString(), any()))
            .thenAnswer(call -> ((Supplier<Object>) call.getArgument(7)).get());

        mvc.perform(patch("/admin/couriers/12/restaurant").cookie(SESSION).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isOk());

        verify(permissions).require(ADMIN, AdminPermissions.COURIERS_MANAGE);
        verify(permissions).require(ADMIN, AdminPermissions.SUPPORT_ACT);
        verify(jdbc).update("UPDATE users SET restaurant_id = ? WHERE id = ? AND role = 'courier' AND restaurant_id IS NULL", 3L, 12L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void courierAlreadyInAStoreIsNotMoved() throws Exception {
        // Trocar de loja com entregas em andamento quebraria o vínculo: não se move, só se liga quem está sem loja.
        when(auth.requireUser("s", "admin")).thenReturn(ADMIN);
        when(jdbc.query(eq("SELECT 1 FROM users WHERE id = ? AND role = 'courier'"), any(ResultSetExtractor.class), eq(12L))).thenReturn(1);
        when(jdbc.update(anyString(), eq(3L), eq(12L))).thenReturn(0);
        when(support.act(eq(ADMIN), eq(3L), anyString(), anyString(), eq(12L), anyString(), anyString(), any()))
            .thenAnswer(call -> ((Supplier<Object>) call.getArgument(7)).get());

        mvc.perform(patch("/admin/couriers/12/restaurant").cookie(SESSION).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isConflict());
    }

    @Test
    void unknownCourierIsNotFound() throws Exception {
        when(auth.requireUser("s", "admin")).thenReturn(ADMIN);

        mvc.perform(patch("/admin/couriers/99/restaurant").cookie(SESSION).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isNotFound());
        verify(support, never()).act(any(), org.mockito.ArgumentMatchers.anyLong(), anyString(), anyString(), any(), anyString(), anyString(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void supportShiftReturnsTheOpenOneWithoutAuditing() throws Exception {
        when(auth.requireUser("s", "admin")).thenReturn(ADMIN);
        when(jdbc.query(eq("SELECT 1 FROM users WHERE id = ? AND role = 'courier'"), any(ResultSetExtractor.class), eq(12L))).thenReturn(1);
        when(shifts.openBySupport(12)).thenReturn(new com.foodie.api.courier.CourierShiftService.SupportShift(40L, false));
        mvc.perform(post("/admin/couriers/12/shifts").cookie(SESSION))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(40));
        verify(audit, never()).record(any(), anyString(), anyString(), any(), anyString());
    }
}
