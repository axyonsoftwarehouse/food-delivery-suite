package com.foodie.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foodie.api.ApiException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FacebookIdentityServiceTest {
    private HttpServer graph;
    private String debugTokenBody;
    private final List<String> meQueries = new ArrayList<>();

    @BeforeEach
    void startGraph() throws IOException {
        graph = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        graph.createContext("/debug_token", exchange -> respond(exchange, debugTokenBody));
        graph.createContext("/me", exchange -> {
            meQueries.add(exchange.getRequestURI().getRawQuery());
            respond(exchange, "{\"id\":\"1\",\"name\":\"Ana\",\"email\":\"Ana@Example.com\"}");
        });
        graph.start();
    }

    @AfterEach
    void stopGraph() {
        graph.stop(0);
    }

    private FacebookIdentityService service() {
        return new FacebookIdentityService("our-app", "our-secret", "http://127.0.0.1:" + graph.getAddress().getPort());
    }

    @Test
    void disabledWithoutAppSecret() {
        FacebookIdentityService service = new FacebookIdentityService("our-app", "", "https://graph.facebook.com");
        assertThat(service.configured()).isFalse();
        assertThatThrownBy(() -> service.verify("token"))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).status()).isEqualTo(503));
    }

    @Test
    void acceptsTokenIssuedForThisApp() {
        debugTokenBody = "{\"data\":{\"app_id\":\"our-app\",\"is_valid\":true}}";

        FacebookIdentityService.Identity identity = service().verify("token");

        assertThat(identity.email()).isEqualTo("ana@example.com");
        assertThat(meQueries.getFirst()).contains("appsecret_proof=" + service().appSecretProof("token"));
    }

    @Test
    void rejectsTokenIssuedForAnotherApp() {
        debugTokenBody = "{\"data\":{\"app_id\":\"someone-else\",\"is_valid\":true}}";

        assertThatThrownBy(() -> service().verify("token"))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).status()).isEqualTo(401));
        assertThat(meQueries).isEmpty();
    }

    @Test
    void rejectsInvalidToken() {
        debugTokenBody = "{\"data\":{\"app_id\":\"our-app\",\"is_valid\":false}}";

        assertThatThrownBy(() -> service().verify("token"))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).status()).isEqualTo(401));
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
