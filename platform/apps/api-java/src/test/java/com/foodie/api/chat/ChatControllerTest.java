package com.foodie.api.chat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ChatController.class)
class ChatControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;

    @MockitoBean
    private JdbcTemplate jdbc;

    @MockitoBean
    private ChatPolicy policy;

    @Test
    void conversationsRequireLogin() throws Exception {
        when(auth.requireUser(null)).thenThrow(new ApiException(401, "Faça login para continuar"));
        mvc.perform(get("/chat/conversations")).andExpect(status().isUnauthorized());
    }

    @Test
    void unreadCount() throws Exception {
        when(auth.requireUser("s")).thenReturn(new User(7, "Cliente", "cliente@demo.local", "customer", null));
        when(jdbc.queryForObject(anyString(), any(Class.class), any(Object[].class))).thenReturn(3L);
        mvc.perform(get("/chat/unread").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unread").value(3));
    }

    @Test
    void latestPageKeepsChronologicalOrderAndMarksOnlyReturnedIncomingMessages() throws Exception {
        when(auth.requireUser("s")).thenReturn(new User(7, "Cliente", "c@demo.local", "customer", null));
        when(jdbc.queryForList(argThat(sql -> sql.contains("ORDER BY id DESC LIMIT 200")),
                eq(7L), eq(9L), eq(9L), eq(7L), eq(null), eq(null)))
            .thenReturn(java.util.stream.LongStream.iterate(205, id -> id - 1).limit(200)
                .mapToObj(id -> Map.<String, Object>of("id", id, "from_user_id", id % 2 == 0 ? 9L : 7L,
                    "to_user_id", id % 2 == 0 ? 7L : 9L, "body", "Mensagem " + id)).toList());

        mvc.perform(get("/chat/messages").param("withUser", "9").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(200))
            .andExpect(jsonPath("$[0].id").value(6))
            .andExpect(jsonPath("$[199].id").value(205));
        var args = org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(argThat(sql -> sql.contains("AND id IN (") && sql.contains("to_user_id = ?")), args.capture());
        org.junit.jupiter.api.Assertions.assertEquals(101, args.getValue().length);
        org.junit.jupiter.api.Assertions.assertEquals(7L, args.getValue()[0]);
        org.junit.jupiter.api.Assertions.assertEquals(6L, args.getValue()[1]);
        org.junit.jupiter.api.Assertions.assertEquals(204L, args.getValue()[100]);
    }

    @Test
    void previousPageUsesAnExclusiveCursorAndDoesNotReadTheNewerMessages() throws Exception {
        when(auth.requireUser("s")).thenReturn(new User(7, "Cliente", "c@demo.local", "customer", null));
        when(jdbc.queryForList(argThat(sql -> sql.contains("id < ?")), eq(7L), eq(9L), eq(9L), eq(7L), eq(6L), eq(6L)))
            .thenReturn(List.of(Map.of("id", 5L, "to_user_id", 7L, "body", "Antiga")));
        mvc.perform(get("/chat/messages").param("withUser", "9").param("beforeId", "6")
                .cookie(new jakarta.servlet.http.Cookie("foodie_session", "s")))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(5));
        verify(jdbc).update("UPDATE chat_messages SET read_at = NOW() WHERE to_user_id = ? AND read_at IS NULL AND id IN (?)", 7L, 5L);
    }

    @Test
    void emptyPageDoesNotMarkAnyMessagesRead() throws Exception {
        when(auth.requireUser("s")).thenReturn(new User(7, "Cliente", "c@demo.local", "customer", null));
        mvc.perform(get("/chat/messages").param("withUser", "9").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
