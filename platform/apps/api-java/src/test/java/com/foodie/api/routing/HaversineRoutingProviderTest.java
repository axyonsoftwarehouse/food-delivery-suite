package com.foodie.api.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HaversineRoutingProviderTest {
    private final HaversineRoutingProvider provider = new HaversineRoutingProvider();

    @Test
    void computesGreatCircleDistance() {
        double meters = HaversineRoutingProvider.distanceMeters(-23.5505, -46.6333, -22.9068, -43.1729);
        assertTrue(meters > 350_000 && meters < 365_000, "esperado ~357km, obtido " + meters);
    }

    @Test
    void zeroForSamePointAndPositiveDuration() {
        assertEquals(0.0, HaversineRoutingProvider.distanceMeters(-3.7, -38.5, -3.7, -38.5), 0.001);
        RoutingProvider.Route route = provider.route(-3.7, -38.5, -3.75, -38.55);
        assertTrue(route.distanceMeters() > 0);
        assertTrue(route.durationSeconds() > 0);
        assertEquals("haversine", route.provider());
    }
}
