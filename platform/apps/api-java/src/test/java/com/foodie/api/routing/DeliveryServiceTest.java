package com.foodie.api.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeliveryServiceTest {
    private final RoutingService routing = new RoutingService(new MapboxRoutingProvider("", "https://api.mapbox.com"), new HaversineRoutingProvider());
    private final DeliveryService service = new DeliveryService(routing);

    private static Map<String, Object> zone(Long base, Long perKm) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("delivery_fee_cents", 599);
        row.put("base_fee_cents", base);
        row.put("per_km_cents", perKm);
        return row;
    }

    private static Map<String, Object> restaurant() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("latitude", -3.73);
        row.put("longitude", -38.52);
        return row;
    }

    private static Map<String, Object> address(Double lat, Double lng) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("latitude", lat);
        row.put("longitude", lng);
        return row;
    }

    @Test
    void fixedFeeWhenZoneHasNoPerKmRule() {
        DeliveryService.Estimate estimate = service.estimate(zone(null, null), restaurant(), address(-3.75, -38.55));
        assertEquals(599, estimate.feeCents());
        assertEquals("fixed", estimate.feeMode());
        assertTrue(estimate.distanceMeters() != null && estimate.distanceMeters() > 0);
        assertEquals("haversine", estimate.provider());
    }

    @Test
    void noDistanceWithoutCoordinates() {
        DeliveryService.Estimate estimate = service.estimate(zone(300L, 100L), restaurant(), address(null, null));
        assertEquals(599, estimate.feeCents());
        assertEquals("fixed", estimate.feeMode());
        assertNull(estimate.distanceMeters());
    }

    @Test
    void distanceFeeAppliesBasePlusPerKm() {
        DeliveryService.Estimate estimate = service.estimate(zone(300L, 100L), restaurant(), address(-3.75, -38.55));
        assertEquals("distance", estimate.feeMode());
        long expected = 300 + ((estimate.distanceMeters() + 999) / 1000) * 100;
        assertEquals(expected, estimate.feeCents());
    }
}
