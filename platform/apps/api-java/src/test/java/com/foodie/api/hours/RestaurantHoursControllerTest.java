package com.foodie.api.hours;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.permissions.PermissionService;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RestaurantHoursController.class)
class RestaurantHoursControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;

    @MockitoBean
    private RestaurantHoursService hours;

    @MockitoBean
    private PermissionService permissions;

    @Test
    void restaurantSeesOnlyItsSchedule() throws Exception {
        when(auth.requireUser("session", "restaurant")).thenReturn(new User(5, "Cozinha", "cozinha@demo.local", "restaurant", 7L));
        Map<String, Object> interval = new LinkedHashMap<>();
        interval.put("id", 1L);
        interval.put("dayOfWeek", 1);
        interval.put("opensAt", "08:00");
        interval.put("closesAt", "18:00");
        interval.put("overnight", false);
        when(hours.timezone(7L)).thenReturn("America/Fortaleza");
        when(hours.list(7L)).thenReturn(List.of(interval));

        mvc.perform(get("/restaurant/hours").cookie(new jakarta.servlet.http.Cookie("foodie_session", "session")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timezone").value("America/Fortaleza"))
            .andExpect(jsonPath("$.hours[0].opensAt").value("08:00"))
            .andExpect(jsonPath("$.hours[0].overnight").value(false));
    }

    @Test
    void restaurantCannotRemoveAnotherRestaurantInterval() throws Exception {
        when(auth.requireUser("session", "restaurant")).thenReturn(new User(5, "Cozinha", "cozinha@demo.local", "restaurant", 7L));
        doThrow(new ApiException(404, "Horário não encontrado")).when(hours).remove(7L, 99L);

        mvc.perform(delete("/restaurant/hours/99").cookie(new jakarta.servlet.http.Cookie("foodie_session", "session")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("Horário não encontrado"));
    }

    @Test
    void adminRegistersOvernightInterval() throws Exception {
        when(auth.requireUser("session", "admin")).thenReturn(new User(1, "Admin", "admin@demo.local", "admin", null));
        when(hours.add(7L, 5, LocalTime.of(18, 0), LocalTime.of(2, 0))).thenReturn(4L);

        mvc.perform(post("/admin/restaurants/7/hours")
                .cookie(new jakarta.servlet.http.Cookie("foodie_session", "session"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dayOfWeek\":5,\"opensAt\":\"18:00\",\"closesAt\":\"02:00\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(4))
            .andExpect(jsonPath("$.overnight").value(true));
    }

    @Test
    void rejectsEqualOpeningAndClosing() throws Exception {
        when(auth.requireUser("session", "admin")).thenReturn(new User(1, "Admin", "admin@demo.local", "admin", null));
        when(hours.add(7L, 5, LocalTime.of(18, 0), LocalTime.of(18, 0))).thenThrow(new ApiException(400, "A abertura e o fechamento não podem ser iguais"));

        mvc.perform(post("/admin/restaurants/7/hours")
                .cookie(new jakarta.servlet.http.Cookie("foodie_session", "session"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dayOfWeek\":5,\"opensAt\":\"18:00\",\"closesAt\":\"18:00\"}"))
            .andExpect(status().isBadRequest());
    }
}
