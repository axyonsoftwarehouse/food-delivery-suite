package com.foodie.api.orders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
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
    void customerCanPlaceOrderWithPrototypeResponse() throws Exception {
        User customer = new User(7, "Cliente", "cliente@demo.local", "customer", null);
        when(auth.requireUser("session", "customer")).thenReturn(customer);
        when(orders.create(any(), any())).thenReturn(Map.of("id", 12, "status", "placed", "subtotalCents", 2500, "deliveryFeeCents", 599, "totalCents", 3099, "address", "Rua, 1"));

        mvc.perform(post("/orders").cookie(new jakarta.servlet.http.Cookie("foodie_session", "session"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"restaurantId\":1,\"addressId\":2,\"items\":[{\"productId\":3,\"quantity\":1}]}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("placed"))
            .andExpect(jsonPath("$.totalCents").value(3099));
    }

    @Test
    void unauthenticatedRequestGetsPrototypeError() throws Exception {
        when(auth.requireUser(null)).thenThrow(new ApiException(401, "Faça login para continuar"));
        mvc.perform(patch("/orders/12/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"accept\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Faça login para continuar"));
    }
}
