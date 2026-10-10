package com.foodie.api.courier;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
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

@WebMvcTest(CourierShiftController.class)
class CourierShiftControllerTest {
    private static final User COURIER = new User(9, "Bia", "bia@demo.local", "courier", 3L);
    private static final User OWNER = new User(2, "Loja", "loja@demo.local", "restaurant", 3L);
    private static final User NO_STORE = new User(4, "Sem loja", "x@demo.local", "restaurant", null);
    private static final Cookie SESSION = new Cookie("foodie_session", "s");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;

    @MockitoBean
    private PermissionService permissions;

    @MockitoBean
    private CourierShiftService shifts;

    @Test
    void opensWith201AndReturns200WhenAlreadyOpen() throws Exception {
        when(auth.requireUser("s", "courier")).thenReturn(COURIER);
        when(shifts.open(9)).thenReturn(new CourierShiftService.Opened(true, Map.of("open", true, "startedAt", "2026-10-10T15:00:00Z")));
        mvc.perform(post("/courier/shift").cookie(SESSION)).andExpect(status().isCreated()).andExpect(jsonPath("$.open").value(true));

        when(shifts.open(9)).thenReturn(new CourierShiftService.Opened(false, Map.of("open", true, "startedAt", "2026-10-10T15:00:00Z")));
        mvc.perform(post("/courier/shift").cookie(SESSION)).andExpect(status().isOk());
    }

    @Test
    void getAndCloseUseTheLoggedCourier() throws Exception {
        when(auth.requireUser("s", "courier")).thenReturn(COURIER);
        when(shifts.status(9)).thenReturn(Map.of("open", false));
        when(shifts.close(9)).thenReturn(Map.of("ok", true, "keepsSharing", true));
        mvc.perform(get("/courier/shift").cookie(SESSION)).andExpect(jsonPath("$.open").value(false));
        mvc.perform(delete("/courier/shift").cookie(SESSION)).andExpect(status().isOk()).andExpect(jsonPath("$.keepsSharing").value(true));
    }

    @Test
    void boardNeedsDispatchPermissionAndUsesTheUserStore() throws Exception {
        when(auth.requireUser("s", "restaurant", "kitchen")).thenReturn(OWNER);
        when(shifts.board(3)).thenReturn(Map.of("restaurant", Map.of(), "couriers", List.of()));
        mvc.perform(get("/restaurant/couriers/board").cookie(SESSION)).andExpect(status().isOk()).andExpect(jsonPath("$.couriers").isArray());
        verify(permissions).require(OWNER, Permissions.ORDERS_DISPATCH);
    }

    @Test
    void boardRefusesAUserWithoutStore() throws Exception {
        when(auth.requireUser("s", "restaurant", "kitchen")).thenReturn(NO_STORE);
        mvc.perform(get("/restaurant/couriers/board").cookie(SESSION)).andExpect(status().isForbidden());
    }

    @Test
    void historyNeedsCouriersManageAndPassesThePeriod() throws Exception {
        when(auth.requireUser("s", "restaurant", "kitchen")).thenReturn(OWNER);
        when(shifts.history(3, 12, 30)).thenReturn(Map.of("totalMinutes", 0, "shifts", List.of()));
        mvc.perform(get("/restaurant/couriers/12/shifts?days=30").cookie(SESSION)).andExpect(status().isOk());
        verify(permissions).require(OWNER, Permissions.COURIERS_MANAGE);
        verify(shifts).history(3, 12, 30);
    }
}
