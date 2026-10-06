package com.foodie.api.payments.accounts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.foodie.api.ApiException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Vinculação contra um servidor local que faz o papel do Mercado Pago. */
class MercadoPagoOAuthClientTest {
    private HttpServer server;
    private final AtomicReference<String> corpo = new AtomicReference<>();
    private final AtomicReference<String> caminho = new AtomicReference<>();
    private final AtomicReference<String> autorizacao = new AtomicReference<>();

    private MercadoPagoOAuthClient client(int status, String resposta) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", troca -> {
            caminho.set(troca.getRequestURI().getPath());
            autorizacao.set(troca.getRequestHeaders().getFirst("Authorization"));
            corpo.set(new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = resposta.getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(status, bytes.length);
            troca.getResponseBody().write(bytes);
            troca.close();
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        return new MercadoPagoOAuthClient("123", "segredo-app", "https://api.foodie.test/payments/mercadopago/oauth/callback", base, "https://auth.mercadopago.com.br");
    }

    @AfterEach
    void parar() {
        if (server != null) server.stop(0);
    }

    @Test
    void authorizationUrlCarriesStatePkceAndTheFixedRedirect() {
        MercadoPagoOAuthClient oauth = new MercadoPagoOAuthClient("123", "s", "https://api.foodie.test/payments/mercadopago/oauth/callback",
            "https://api.mercadopago.com", "https://auth.mercadopago.com.br");

        String url = oauth.authorizationUrl("estado-aleatorio", "desafio");

        assertEquals("https://auth.mercadopago.com.br/authorization?client_id=123&response_type=code&platform_id=mp"
            + "&state=estado-aleatorio&redirect_uri=https%3A%2F%2Fapi.foodie.test%2Fpayments%2Fmercadopago%2Foauth%2Fcallback"
            + "&code_challenge=desafio&code_challenge_method=S256", url);
    }

    @Test
    void exchangesTheCodeWithTheVerifier() throws Exception {
        MercadoPagoOAuthClient oauth = client(200, """
            {"access_token":"APP_USR-loja","token_type":"Bearer","expires_in":15552000,"scope":"offline_access read write",
             "user_id":3588446200,"refresh_token":"TG-loja","public_key":"APP_USR-pk-loja","live_mode":false}
            """);

        MercadoPagoOAuthClient.OAuthTokens tokens = oauth.exchangeCode("TG-codigo", "verificador");

        assertEquals("/oauth/token", caminho.get());
        assertTrue(corpo.get().contains("grant_type=authorization_code"));
        assertTrue(corpo.get().contains("code=TG-codigo"));
        assertTrue(corpo.get().contains("code_verifier=verificador"));
        assertTrue(corpo.get().contains("client_secret=segredo-app"));
        assertTrue(corpo.get().contains("redirect_uri=https%3A%2F%2Fapi.foodie.test%2Fpayments%2Fmercadopago%2Foauth%2Fcallback"));
        assertEquals("APP_USR-loja", tokens.accessToken());
        assertEquals("TG-loja", tokens.refreshToken());
        assertEquals("APP_USR-pk-loja", tokens.publicKey());
        assertEquals("3588446200", tokens.userId());
        assertEquals(15552000L, tokens.expiresInSeconds());
    }

    @Test
    void refusedExchangeIsA502WithTheReason() throws Exception {
        MercadoPagoOAuthClient oauth = client(400, "{\"message\":\"invalid_grant\"}");
        ApiException erro = assertThrows(ApiException.class, () -> oauth.exchangeCode("velho", "v"));
        assertEquals(502, erro.status());
        assertEquals("Mercado Pago recusou a vinculação: HTTP 400 - invalid_grant", erro.getMessage());
    }

    @Test
    void refreshReturnsEmptyWhenTheProviderRefuses() throws Exception {
        assertTrue(client(400, "{\"message\":\"invalid_grant\"}").refresh("TG-velho").isEmpty());
    }

    @Test
    void refreshSendsTheRefreshToken() throws Exception {
        MercadoPagoOAuthClient oauth = client(200, """
            {"access_token":"APP_USR-novo","expires_in":15552000,"user_id":3588446200,"refresh_token":"TG-novo","public_key":"APP_USR-pk-loja"}
            """);

        MercadoPagoOAuthClient.OAuthTokens tokens = oauth.refresh("TG-velho").orElseThrow();

        assertTrue(corpo.get().contains("grant_type=refresh_token"));
        assertTrue(corpo.get().contains("refresh_token=TG-velho"));
        assertEquals("APP_USR-novo", tokens.accessToken());
    }

    @Test
    void nicknameComesFromUsersMe() throws Exception {
        MercadoPagoOAuthClient oauth = client(200, "{\"id\":3588446200,\"nickname\":\"TESTUSER4062\",\"email\":\"x@y\"}");
        assertEquals("TESTUSER4062", oauth.nickname("APP_USR-loja"));
        assertEquals("/users/me", caminho.get());
        assertEquals("Bearer APP_USR-loja", autorizacao.get());
    }

    @Test
    void nicknameFailureIsNull() throws Exception {
        assertNull(client(500, "{}").nickname("APP_USR-loja"));
    }

    @Test
    void notConfiguredWithoutClientSecretOrRedirect() {
        assertFalse(new MercadoPagoOAuthClient("123", "", "https://x", "https://api.mercadopago.com", "https://auth.mercadopago.com.br").configured());
        assertFalse(new MercadoPagoOAuthClient("123", "s", "", "https://api.mercadopago.com", "https://auth.mercadopago.com.br").configured());
        assertTrue(new MercadoPagoOAuthClient("123", "s", "https://x", "https://api.mercadopago.com", "https://auth.mercadopago.com.br").configured());
    }
}
