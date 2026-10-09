package com.foodie.api.courier;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CourierReviewController.class)
class CourierReviewControllerTest {
    private static final User CUSTOMER = new User(8, "Ana", "ana@demo.local", "customer", null);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;

    @MockitoBean
    private CourierReviewService reviews;

    @Test
    void ratingOutOfRangeIsRejected() throws Exception {
        mvc.perform(post("/orders/40/courier-review").cookie(new Cookie("foodie_session", "s")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":6}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void validReviewReturns201AndCallsTheService() throws Exception {
        when(auth.requireUser("s", "customer")).thenReturn(CUSTOMER);
        when(reviews.create(CUSTOMER, 40, 5, "ok")).thenReturn(Map.of("rating", 5));
        mvc.perform(post("/orders/40/courier-review").cookie(new Cookie("foodie_session", "s")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":5,\"comment\":\"ok\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.rating").value(5));
        verify(reviews).create(CUSTOMER, 40, 5, "ok");
    }

    @Test
    void tooLongCommentIsRejected() throws Exception {
        mvc.perform(post("/orders/40/courier-review").cookie(new Cookie("foodie_session", "s")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":5,\"comment\":\"" + "a".repeat(301) + "\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getReturnsTheReviewState() throws Exception {
        when(auth.requireUser("s", "customer")).thenReturn(CUSTOMER);
        when(reviews.forOrder(CUSTOMER, 40)).thenReturn(Map.of("canReview", true));
        mvc.perform(get("/orders/40/courier-review").cookie(new Cookie("foodie_session", "s")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.canReview").value(true));
    }
}
