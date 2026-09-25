package com.foodie.api.routing;

import com.foodie.api.ApiException;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class MapboxRoutingProvider implements RoutingProvider {
    private final RestClient client;
    private final String token;

    public MapboxRoutingProvider(@Value("${app.mapbox.token:}") String token,
                                 @Value("${app.mapbox.base-url:https://api.mapbox.com}") String baseUrl) {
        this.token = token;
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public String provider() {
        return "mapbox";
    }

    public boolean configured() {
        return token != null && !token.isBlank();
    }

    @Override
    public Route route(double fromLat, double fromLng, double toLat, double toLng) {
        if (!configured()) throw new ApiException(503, "Mapbox não configurado: defina MAPBOX_TOKEN");
        String path = "/directions/v5/mapbox/driving/" + fromLng + "," + fromLat + ";" + toLng + "," + toLat;
        try {
            Map<String, Object> response = client.get()
                .uri(builder -> builder.path(path).queryParam("access_token", token).queryParam("overview", "false").build())
                .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {});
            Object routes = response == null ? null : response.get("routes");
            if (!(routes instanceof List<?> list) || list.isEmpty() || !(list.getFirst() instanceof Map<?, ?> first)) {
                throw new ApiException(502, "Resposta inválida do Mapbox");
            }
            long distance = Math.round(((Number) first.get("distance")).doubleValue());
            long duration = Math.round(((Number) first.get("duration")).doubleValue());
            return new Route(distance, duration, provider());
        } catch (RestClientResponseException error) {
            throw new ApiException(502, "Mapbox recusou a rota (" + error.getStatusCode().value() + ")");
        }
    }
}
