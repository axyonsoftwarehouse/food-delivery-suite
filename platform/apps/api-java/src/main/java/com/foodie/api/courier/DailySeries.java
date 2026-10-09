package com.foodie.api.courier;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Série diária de ganhos (parte C): um item por dia, com zeros, agrupado pela data local da loja. */
public final class DailySeries {
    public record Row(Instant at, long feeCents, long tipCents) {}

    private DailySeries() {}

    public static Instant windowStart(LocalDate today, int days, ZoneId zone) {
        return today.minusDays(days - 1L).atStartOfDay(zone).toInstant();
    }

    public static List<Map<String, Object>> build(List<Row> rows, LocalDate today, int days, ZoneId zone) {
        long[][] buckets = new long[days][3];
        for (Row row : rows) {
            long offset = java.time.temporal.ChronoUnit.DAYS.between(today.minusDays(days - 1L), row.at().atZone(zone).toLocalDate());
            if (offset < 0 || offset >= days) continue;
            buckets[(int) offset][0] += row.feeCents();
            buckets[(int) offset][1] += row.tipCents();
            buckets[(int) offset][2] += 1;
        }
        List<Map<String, Object>> series = new ArrayList<>();
        for (int index = 0; index < days; index++) {
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", today.minusDays(days - 1L - index).toString());
            day.put("deliveryFeeCents", buckets[index][0]);
            day.put("tipCents", buckets[index][1]);
            day.put("count", buckets[index][2]);
            series.add(day);
        }
        return series;
    }
}
