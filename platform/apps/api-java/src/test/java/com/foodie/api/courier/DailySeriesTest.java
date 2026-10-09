package com.foodie.api.courier;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DailySeriesTest {
    private static final ZoneId FORTALEZA = ZoneId.of("America/Fortaleza");

    @Test
    void fillsEveryDayWithZerosAndGroupsByLocalDate() {
        LocalDate today = LocalDate.of(2026, 10, 9);
        List<DailySeries.Row> rows = List.of(
            new DailySeries.Row(Instant.parse("2026-10-09T15:00:00Z"), 600, 100),   // 09/10 12:00 local
            new DailySeries.Row(Instant.parse("2026-10-09T02:00:00Z"), 500, 0),     // 08/10 23:00 local
            new DailySeries.Row(Instant.parse("2026-10-09T18:00:00Z"), 400, 50));   // 09/10 15:00 local

        List<Map<String, Object>> series = DailySeries.build(rows, today, 7, FORTALEZA);

        assertThat(series).hasSize(7);
        assertThat(series.getFirst()).containsEntry("date", "2026-10-03").containsEntry("count", 0L);
        assertThat(series.get(5)).containsEntry("date", "2026-10-08").containsEntry("deliveryFeeCents", 500L).containsEntry("count", 1L);
        assertThat(series.get(6)).containsEntry("date", "2026-10-09").containsEntry("deliveryFeeCents", 1000L)
            .containsEntry("tipCents", 150L).containsEntry("count", 2L);
    }

    @Test
    void ignoresRowsOutsideTheWindow() {
        LocalDate today = LocalDate.of(2026, 10, 9);
        List<DailySeries.Row> rows = List.of(new DailySeries.Row(Instant.parse("2026-09-01T12:00:00Z"), 999, 999));
        assertThat(DailySeries.build(rows, today, 7, FORTALEZA)).allSatisfy(day -> assertThat(day).containsEntry("count", 0L));
    }

    @Test
    void windowStartIsTheStartOfTheOldestLocalDay() {
        assertThat(DailySeries.windowStart(LocalDate.of(2026, 10, 9), 7, FORTALEZA)).isEqualTo(Instant.parse("2026-10-03T03:00:00Z"));
    }
}
