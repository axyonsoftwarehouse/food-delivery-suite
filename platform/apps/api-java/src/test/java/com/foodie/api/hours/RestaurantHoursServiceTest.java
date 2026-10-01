package com.foodie.api.hours;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class RestaurantHoursServiceTest {
    private JdbcTemplate jdbc;
    private RestaurantHoursService hours;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        hours = new RestaurantHoursService(jdbc);
    }

    @SuppressWarnings("unchecked")
    private void pausedUntil(String... until) {
        when(jdbc.query(contains("support_paused_until"), any(RowMapper.class), eq(7L))).thenReturn(List.of(until));
    }

    @Test
    void activePauseClosesTheStoreEvenWithoutSchedule() {
        pausedUntil("2099-01-01T00:00:00Z");
        assertThat(hours.isOpen(7L, null)).isFalse();
    }

    @Test
    void withoutPauseAndWithoutTimezoneTheStoreStaysOpen() {
        pausedUntil();
        assertThat(hours.isOpen(7L, null)).isTrue();
    }

    @Test
    void scheduledOrderInsideThePauseIsRefused() {
        pausedUntil("2099-01-01T00:00:00Z");
        assertThatThrownBy(() -> hours.requireOpenAt(7L, "America/Fortaleza", LocalDateTime.of(2098, 12, 31, 20, 0)))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("status", 409);
    }

    @Test
    void resumeWithoutActivePauseIsConflict() {
        pausedUntil();
        assertThatThrownBy(() -> hours.resume(7L))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("status", 409);
    }
}
