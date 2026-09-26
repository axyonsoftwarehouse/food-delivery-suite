package com.foodie.api.orders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReviewController.class)
class ReviewControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;

    @MockitoBean
    private ReviewService reviews;

    @Test
    void customerReviewsDeliveredOrder() throws Exception {
        when(auth.requireUser("session", "customer")).thenReturn(new User(7, "Cliente", "cliente@demo.local", "customer", null));
        when(reviews.create(any(), eq(15L), eq(5), eq("Excelente!"))).thenReturn(Map.of("id", 3L, "order_id", 15L, "rating", 5));

        mvc.perform(post("/orders/15/review")
                .cookie(new jakarta.servlet.http.Cookie("foodie_session", "session"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":5,\"comment\":\"Excelente!\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rating").value(5));
    }

    @Test
    void restaurantReviewsArePublic() throws Exception {
        when(reviews.byRestaurant(7L)).thenReturn(Map.of("average", 4.5, "count", 2L, "items", List.of()));

        mvc.perform(get("/restaurants/7/reviews"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.average").value(4.5))
            .andExpect(jsonPath("$.count").value(2));
    }
}
