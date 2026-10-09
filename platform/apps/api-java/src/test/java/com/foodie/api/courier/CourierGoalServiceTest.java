package com.foodie.api.courier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class CourierGoalServiceTest {
    private JdbcTemplate jdbc;
    private CourierGoalService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new CourierGoalService(jdbc, Clock.fixed(Instant.parse("2026-10-07T17:00:00Z"), ZoneOffset.UTC));
    }

    private static Map<String, Object> rowOf(Integer goal, String timezone) {
        Map<String, Object> row = new HashMap<>();
        row.put("weekly_delivery_goal", goal);
        row.put("timezone", timezone);
        return row;
    }

    @Test
    void reportsGoalAndProgressForTheCurrentStoreWeek() {
        when(jdbc.queryForMap(startsWith("SELECT u.weekly_delivery_goal, r.timezone"), any(Object[].class)))
            .thenReturn(rowOf(20, "America/Fortaleza"));
        when(jdbc.queryForObject(startsWith("SELECT COUNT(DISTINCT o.id)"), eq(Long.class), any(Object[].class))).thenReturn(14L);

        Map<String, Object> goal = service.get(9);

        assertThat(goal).containsEntry("weeklyDeliveries", 20).containsEntry("doneThisWeek", 14L)
            .containsEntry("weekStart", "2026-10-05").containsEntry("weekEnd", "2026-10-11");
        verify(jdbc).queryForObject(startsWith("SELECT COUNT(DISTINCT o.id)"), eq(Long.class),
            eq(9L), eq(Timestamp.from(Instant.parse("2026-10-05T03:00:00Z"))), eq(Timestamp.from(Instant.parse("2026-10-12T03:00:00Z"))));
    }

    @Test
    void setValidatesTheLimitsAndAcceptsNullToRemove() {
        when(jdbc.queryForMap(startsWith("SELECT u.weekly_delivery_goal, r.timezone"), any(Object[].class)))
            .thenReturn(rowOf(null, null));
        when(jdbc.queryForObject(startsWith("SELECT COUNT(DISTINCT o.id)"), eq(Long.class), any(Object[].class))).thenReturn(0L);

        assertThatThrownBy(() -> service.set(9, 0)).isInstanceOf(ApiException.class).hasMessage("A meta deve ser de 1 a 200 entregas por semana");
        assertThatThrownBy(() -> service.set(9, 201)).isInstanceOf(ApiException.class);
        Map<String, Object> result = service.set(9, null);

        verify(jdbc).update("UPDATE users SET weekly_delivery_goal = ? WHERE id = ? AND role = 'courier'", null, 9L);
        assertThat(result).containsEntry("weeklyDeliveries", null).containsEntry("doneThisWeek", 0L);
    }
}
