package com.foodie.api.routing;

public interface RoutingProvider {
    String provider();

    Route route(double fromLat, double fromLng, double toLat, double toLng);

    record Route(long distanceMeters, long durationSeconds, String provider) {}
}
