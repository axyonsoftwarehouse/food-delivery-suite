package com.foodie.api.courier;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.ApiException;
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
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(CourierGoalController.class)
class CourierGoalControllerTest {
    private static final User COURIER = new User(9, "Leo", "leo@demo.local", "courier", null);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;

    @MockitoBean
    private CourierGoalService goals;

    private ResultActions putGoal(String body) throws Exception {
        return mvc.perform(put("/courier/goal").cookie(new Cookie("foodie_session", "s")).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void goalBelowOneIsRejected() throws Exception {
        putGoal("{\"weeklyDeliveries\":0}").andExpect(status().isBadRequest());
    }

    @Test
    void goalAbove200IsRejected() throws Exception {
        putGoal("{\"weeklyDeliveries\":201}").andExpect(status().isBadRequest());
    }

    @Test
    void validGoalIsSaved() throws Exception {
        when(auth.requireUser("s", "courier")).thenReturn(COURIER);
        when(goals.set(9, 20)).thenReturn(Map.of("weeklyDeliveries", 20));
        putGoal("{\"weeklyDeliveries\":20}").andExpect(status().isOk()).andExpect(jsonPath("$.weeklyDeliveries").value(20));
        verify(goals).set(9, 20);
    }

    @Test
    void nullGoalRemovesIt() throws Exception {
        when(auth.requireUser("s", "courier")).thenReturn(COURIER);
        when(goals.set(9, null)).thenReturn(Map.of("doneThisWeek", 0));
        putGoal("{\"weeklyDeliveries\":null}").andExpect(status().isOk());
        verify(goals).set(9, null);
    }

    @Test
    void getReturnsTheGoal() throws Exception {
        when(auth.requireUser("s", "courier")).thenReturn(COURIER);
        when(goals.get(9)).thenReturn(Map.of("weeklyDeliveries", 20, "doneThisWeek", 14));
        mvc.perform(get("/courier/goal").cookie(new Cookie("foodie_session", "s")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.doneThisWeek").value(14));
    }

    @Test
    void otherRolesAreForbidden() throws Exception {
        when(auth.requireUser("s", "courier")).thenThrow(new ApiException(403, "Sem permissao"));
        mvc.perform(get("/courier/goal").cookie(new Cookie("foodie_session", "s"))).andExpect(status().isForbidden());
    }
}
