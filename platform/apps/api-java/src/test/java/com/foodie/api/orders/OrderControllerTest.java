package com.foodie.api.orders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderController.class)
class OrderControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;

    @MockitoBean
    private OrderService orders;

    @MockitoBean
    private JdbcTemplate jdbc;

    @Test
    void unauthenticatedRequestGetsPrototypeError() throws Exception {
        when(auth.requireUser(null)).thenThrow(new ApiException(401, "Faça login para continuar"));
        mvc.perform(patch("/orders/12/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"accept\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Faça login para continuar"));
    }

    @Test
    void historyUsesCursorPaginationAndRejectsInvalidLimit() throws Exception {
        when(auth.requireUser("session")).thenReturn(new User(7, "Cliente", "cliente@demo.local", "customer", null));
        when(orders.history(any(), eq("delivered"), eq(null), eq(20))).thenReturn(Map.of("items", List.of(Map.of("id", 5)), "nextCursor", 4L));

        mvc.perform(get("/orders/history").cookie(new jakarta.servlet.http.Cookie("foodie_session", "session")).param("status", "delivered"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nextCursor").value(4));
        mvc.perform(get("/orders/history").cookie(new jakarta.servlet.http.Cookie("foodie_session", "session")).param("limit", "99"))
            .andExpect(status().isBadRequest());
    }
}
