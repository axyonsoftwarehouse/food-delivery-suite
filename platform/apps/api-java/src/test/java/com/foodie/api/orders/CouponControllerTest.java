package com.foodie.api.orders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CouponController.class)
class CouponControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;

    @MockitoBean
    private CouponService coupons;

    @MockitoBean
    private JdbcTemplate jdbc;

    @Test
    void validateReturnsDiscount() throws Exception {
        when(auth.requireUser("session", "customer")).thenReturn(new User(7, "Cliente", "cliente@demo.local", "customer", null));
        when(coupons.validate("BEMVINDO", 7L, 4000L)).thenReturn(new CouponService.Applied(1L, "BEMVINDO", 400L));

        mvc.perform(post("/coupons/validate")
                .cookie(new jakarta.servlet.http.Cookie("foodie_session", "session"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"BEMVINDO\",\"restaurantId\":7,\"subtotalCents\":4000}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.discountCents").value(400))
            .andExpect(jsonPath("$.code").value("BEMVINDO"));
    }
}
