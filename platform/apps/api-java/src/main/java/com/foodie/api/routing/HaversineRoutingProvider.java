package com.foodie.api.routing;

import org.springframework.stereotype.Component;

@Component
public class HaversineRoutingProvider implements RoutingProvider {
    private static final double EARTH_RADIUS_METERS = 6_371_000;
    private static final double URBAN_SPEED_METERS_PER_SECOND = 25_000 / 3600.0;

    @Override
    public String provider() {
        return "haversine";
    }

    @Override
    public Route route(double fromLat, double fromLng, double toLat, double toLng) {
        double meters = distanceMeters(fromLat, fromLng, toLat, toLng);
        long duration = Math.round(meters / URBAN_SPEED_METERS_PER_SECOND);
        return new Route(Math.round(meters), duration, provider());
    }

    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(a)));
    }
}
