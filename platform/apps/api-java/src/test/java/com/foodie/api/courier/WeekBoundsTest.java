package com.foodie.api.courier;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class WeekBoundsTest {
    private static final ZoneId FORTALEZA = ZoneId.of("America/Fortaleza"); // UTC-3, sem horário de verão

    @Test
    void weekRunsMondayToSundayInTheStoreZone() {
        // quarta 2026-10-07 14:00 em Fortaleza
        WeekBounds week = WeekBounds.of(Instant.parse("2026-10-07T17:00:00Z"), FORTALEZA);
        assertThat(week.weekStart()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(week.weekEnd()).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(week.from()).isEqualTo(Instant.parse("2026-10-05T03:00:00Z"));
        assertThat(week.to()).isEqualTo(Instant.parse("2026-10-12T03:00:00Z"));
    }

    @Test
    void mondayJustAfterLocalMidnightStartsANewWeekEvenIfUtcIsStillSunday() {
        // segunda 2026-10-12 00:30 em Fortaleza = 03:30Z de segunda: já é a semana nova
        WeekBounds week = WeekBounds.of(Instant.parse("2026-10-12T03:30:00Z"), FORTALEZA);
        assertThat(week.weekStart()).isEqualTo(LocalDate.of(2026, 10, 12));
        // domingo 23:30 em Fortaleza = segunda 02:30Z: ainda é a semana anterior
        WeekBounds before = WeekBounds.of(Instant.parse("2026-10-12T02:30:00Z"), FORTALEZA);
        assertThat(before.weekStart()).isEqualTo(LocalDate.of(2026, 10, 5));
    }
}
