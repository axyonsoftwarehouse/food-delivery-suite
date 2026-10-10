package com.foodie.api.courier;

import com.foodie.api.routing.HaversineRoutingProvider;
import java.text.Collator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Quadro dos entregadores da loja (parte D): status, distância em linha reta até a loja, ordem e sugestão.
 * Disponíveis pela distância, depois em entrega pela distância, depois sem sinal e fora do turno; empate pelo nome.
 */
public final class CourierBoard {
    public record Row(long id, String name, Instant shiftStartedAt, long activeDeliveries,
                      Double latitude, Double longitude, Instant locationAt) {}

    private record Entry(Row row, String status, boolean showLocation, Long distance) {}

    private static final List<String> GROUPS = List.of(
        CourierStatus.AVAILABLE, CourierStatus.DELIVERING, CourierStatus.NO_SIGNAL, CourierStatus.OFF_SHIFT);

    private CourierBoard() {}

    public static List<Map<String, Object>> build(List<Row> rows, Double storeLat, Double storeLng, Instant now) {
        List<Entry> entries = new ArrayList<>();
        for (Row row : rows) {
            String status = CourierStatus.of(row.shiftStartedAt() != null, row.activeDeliveries(), row.locationAt(), now);
            boolean show = (CourierStatus.AVAILABLE.equals(status) || CourierStatus.DELIVERING.equals(status))
                && row.latitude() != null && row.longitude() != null && CourierStatus.fresh(row.locationAt(), now);
            Long distance = show && storeLat != null && storeLng != null
                ? Math.round(HaversineRoutingProvider.distanceMeters(row.latitude(), row.longitude(), storeLat, storeLng))
                : null;
            entries.add(new Entry(row, status, show, distance));
        }
        Collator names = Collator.getInstance(Locale.forLanguageTag("pt-BR"));
        entries.sort(Comparator.comparingInt((Entry entry) -> GROUPS.indexOf(entry.status()))
            .thenComparingLong(entry -> entry.distance() == null ? Long.MAX_VALUE : entry.distance())
            .thenComparing(entry -> entry.row().name(), names));
        List<Map<String, Object>> result = new ArrayList<>();
        boolean suggested = false;
        for (Entry entry : entries) {
            Row row = entry.row();
            boolean suggest = !suggested && CourierStatus.AVAILABLE.equals(entry.status());
            suggested |= suggest;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.id());
            item.put("name", row.name());
            item.put("status", entry.status());
            item.put("activeDeliveries", row.activeDeliveries());
            item.put("shiftStartedAt", row.shiftStartedAt() == null ? null : row.shiftStartedAt().toString());
            item.put("latitude", entry.showLocation() ? row.latitude() : null);
            item.put("longitude", entry.showLocation() ? row.longitude() : null);
            item.put("locationUpdatedAt", entry.showLocation() ? row.locationAt().toString() : null);
            item.put("distanceMeters", entry.distance());
            item.put("suggested", suggest);
            result.add(item);
        }
        return result;
    }
}
