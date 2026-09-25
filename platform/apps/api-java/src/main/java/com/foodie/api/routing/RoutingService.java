package com.foodie.api.routing;

import com.foodie.api.ApiException;
import org.springframework.stereotype.Service;

@Service
public class RoutingService {
    private final MapboxRoutingProvider mapbox;
    private final HaversineRoutingProvider haversine;

    public RoutingService(MapboxRoutingProvider mapbox, HaversineRoutingProvider haversine) {
        this.mapbox = mapbox;
        this.haversine = haversine;
    }

    public boolean mapboxEnabled() {
        return mapbox.configured();
    }

    public RoutingProvider.Route route(double fromLat, double fromLng, double toLat, double toLng) {
        if (mapbox.configured()) {
            try { return mapbox.route(fromLat, fromLng, toLat, toLng); }
            catch (ApiException error) { /* cai para Haversine se o provedor falhar */ }
        }
        return haversine.route(fromLat, fromLng, toLat, toLng);
    }
}
