package com.foodie.api.catalog;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

@WebMvcTest(RestaurantProductsController.class)
class RestaurantProductsControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;

    @MockitoBean
    private JdbcTemplate jdbc;

    @Test
    void restaurantCanChangeOnlyOwnProduct() throws Exception {
        when(auth.requireUser("session", "restaurant"))
            .thenReturn(new User(5, "Cozinha", "cozinha@demo.local", "restaurant", 7L));
        when(jdbc.update("UPDATE products SET available = ? WHERE id = ? AND restaurant_id = ?", false, 11L, 7L))
            .thenReturn(1);

        mvc.perform(patch("/restaurant/products/11/availability")
                .cookie(new jakarta.servlet.http.Cookie("foodie_session", "session"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"available\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false));
        verify(jdbc).update("UPDATE products SET available = ? WHERE id = ? AND restaurant_id = ?", false, 11L, 7L);
    }

    @Test
    void otherRestaurantProductIsHidden() throws Exception {
        when(auth.requireUser("session", "restaurant"))
            .thenReturn(new User(5, "Cozinha", "cozinha@demo.local", "restaurant", 7L));
        mvc.perform(patch("/restaurant/products/12/availability")
                .cookie(new jakarta.servlet.http.Cookie("foodie_session", "session"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"available\":true}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("Produto não encontrado"));
    }
}
