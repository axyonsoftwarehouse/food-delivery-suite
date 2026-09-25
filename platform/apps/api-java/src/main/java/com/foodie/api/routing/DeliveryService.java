package com.foodie.api.routing;

import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DeliveryService {
    private final RoutingService routing;

    public DeliveryService(RoutingService routing) {
        this.routing = routing;
    }

    public record Estimate(long fixedFeeCents, Long distanceMeters, Long durationSeconds, long feeCents, String provider, String feeMode) {}

    public Estimate estimate(Map<String, Object> zone, Map<String, Object> restaurant, Map<String, Object> address) {
        long fixed = number(zone, "delivery_fee_cents");
        Integer base = intOrNull(zone, "base_fee_cents");
        Integer perKm = intOrNull(zone, "per_km_cents");
        Double fromLat = decimal(restaurant, "latitude");
        Double fromLng = decimal(restaurant, "longitude");
        Double toLat = decimal(address, "latitude");
        Double toLng = decimal(address, "longitude");

        Long distance = null;
        Long duration = null;
        String provider = null;
        if (fromLat != null && fromLng != null && toLat != null && toLng != null) {
            RoutingProvider.Route route = routing.route(fromLat, fromLng, toLat, toLng);
            distance = route.distanceMeters();
            duration = route.durationSeconds();
            provider = route.provider();
        }
        long fee = DeliveryFee.compute(fixed, base, perKm, distance);
        String mode = perKm != null && perKm > 0 && distance != null ? "distance" : "fixed";
        return new Estimate(fixed, distance, duration, fee, provider, mode);
    }

    private static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private static Integer intOrNull(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : ((Number) value).intValue();
    }

    private static Double decimal(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : ((Number) value).doubleValue();
    }
}
