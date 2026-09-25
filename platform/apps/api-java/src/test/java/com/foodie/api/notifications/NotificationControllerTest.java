package com.foodie.api.notifications;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;

    @MockitoBean
    private NotificationService notifications;

    @MockitoBean
    private JdbcTemplate jdbc;

    @MockitoBean
    private PushSender pushSender;

    @Test
    void inboxReturnsUnreadForAuthenticatedUser() throws Exception {
        when(auth.requireUser("session")).thenReturn(new User(7, "Cliente", "cliente@demo.local", "customer", null));
        when(notifications.inbox(7L)).thenReturn(Map.of("unread", 2L, "items", List.of()));

        mvc.perform(get("/notifications").cookie(new jakarta.servlet.http.Cookie("foodie_session", "session")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unread").value(2));
    }

    @Test
    void pushKeyReportsEnabled() throws Exception {
        when(pushSender.publicKey()).thenReturn("chave-publica");
        when(pushSender.configured()).thenReturn(true);

        mvc.perform(get("/notifications/push/key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.publicKey").value("chave-publica"));
    }
}
