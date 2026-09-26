package com.foodie.api.notifications;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class FcmDispatcherTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final FcmSender sender = mock(FcmSender.class);
    private final FcmDispatcher dispatcher = new FcmDispatcher(jdbc, sender);

    @Test
    void doesNothingWhenNotConfigured() {
        when(sender.configured()).thenReturn(false);

        dispatcher.dispatch();

        verify(jdbc, never()).queryForList(anyString());
    }

    @Test
    void marksDeliverySent() {
        when(sender.configured()).thenReturn(true);
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of(
            "delivery_id", 10L, "attempts", 0, "title", "Novo pedido", "body", "Pedido #1", "order_id", 1L, "token", "fcm-token")));

        dispatcher.dispatch();

        verify(sender).send(eq("fcm-token"), eq("Novo pedido"), eq("Pedido #1"), anyMap());
        verify(jdbc).update(contains("status = 'sent'"), eq(10L));
    }

    @Test
    void removesInvalidToken() {
        when(sender.configured()).thenReturn(true);
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of(
            "delivery_id", 11L, "attempts", 0, "title", "t", "body", "b", "order_id", 1L, "token", "stale-token")));
        doThrow(new FcmSender.InvalidFcmTokenException("invalid"))
            .when(sender).send(eq("stale-token"), anyString(), anyString(), anyMap());

        dispatcher.dispatch();

        verify(jdbc).update(contains("DELETE FROM device_tokens"), eq("stale-token"));
    }
}
