package com.foodie.api.routing;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.orders.OrderService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TrackingController.class)
class TrackingControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;

    @MockitoBean
    private OrderService orders;

    @MockitoBean
    private JdbcTemplate jdbc;

    @MockitoBean
    private AdminPermissionService adminPermissions;

    private void order(String status) {
        when(auth.requireUser("s")).thenReturn(new User(8, "Ana", "ana@demo.local", "customer", null));
        Map<String, Object> row = new HashMap<>();
        row.put("id", 40L); row.put("status", status); row.put("courier_id", 9L);
        when(jdbc.queryForMap(anyString(), eq(40L))).thenReturn(row);
        when(jdbc.queryForList(contains("FROM courier_locations"), anyLong()))
            .thenReturn(List.of(Map.of("latitude", -3.73, "longitude", -38.52)));
    }

    @Test
    void showsTheCourierDuringTheDelivery() throws Exception {
        order("picked_up");
        mvc.perform(get("/orders/40/tracking").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.courierLocation.latitude").value(-3.73));
    }

    @Test
    void hidesTheCourierAfterTheDelivery() throws Exception {
        order("delivered");
        mvc.perform(get("/orders/40/tracking").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.courierLocation").doesNotExist());
        verify(jdbc, never()).queryForList(contains("FROM courier_locations"), anyLong());
    }
}
