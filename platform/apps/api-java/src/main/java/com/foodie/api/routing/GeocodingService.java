package com.foodie.api.routing;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class GeocodingService {
    private final RestClient client;
    private final String token;

    public GeocodingService(@Value("${app.mapbox.token:}") String token,
                            @Value("${app.mapbox.base-url:https://api.mapbox.com}") String baseUrl) {
        this.token = token;
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    public boolean configured() {
        return token != null && !token.isBlank();
    }

    public Optional<Coordinate> geocode(String query) {
        if (!configured() || query == null || query.isBlank()) return Optional.empty();
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        try {
            Map<String, Object> response = client.get()
                .uri(builder -> builder.path("/geocoding/v5/mapbox.places/" + encoded + ".json")
                    .queryParam("access_token", token).queryParam("country", "br").queryParam("limit", "1").build())
                .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {});
            Object features = response == null ? null : response.get("features");
            if (!(features instanceof List<?> list) || list.isEmpty() || !(list.getFirst() instanceof Map<?, ?> first)) return Optional.empty();
            Object center = first.get("center");
            if (!(center instanceof List<?> point) || point.size() < 2) return Optional.empty();
            double longitude = ((Number) point.get(0)).doubleValue();
            double latitude = ((Number) point.get(1)).doubleValue();
            return Optional.of(new Coordinate(latitude, longitude));
        } catch (RuntimeException error) {
            return Optional.empty();
        }
    }

    public record Coordinate(double latitude, double longitude) {}
}
