package com.foodie.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Origin/Referer nas escritas com cookie: fecha CSRF sem quebrar mobile, curl e webhooks. */
class OriginCheckFilterTest {
    private final OriginCheckFilter filter = new OriginCheckFilter(
        "https://cliente.example.com", "https://admin.example.com");

    @Test
    void acceptsAnAllowedStorefrontOrigin() throws Exception {
        MockHttpServletRequest request = write("/orders");
        request.setCookies(new Cookie("foodie_session", "token"));
        request.addHeader("Origin", "https://cliente.example.com");

        assertNotNull(run(request));
    }

    @Test
    void acceptsAnAllowedAdminOriginWithDefaultPort() throws Exception {
        MockHttpServletRequest request = write("/orders");
        request.setCookies(new Cookie("foodie_session", "token"));
        request.addHeader("Origin", "https://admin.example.com:443");

        assertNotNull(run(request));
    }

    @Test
    void rejectsAnUnknownOriginOnACookieWrite() throws Exception {
        MockHttpServletRequest request = write("/orders");
        request.setCookies(new Cookie("foodie_session", "token"));
        request.addHeader("Origin", "https://evil.example.com");

        MockHttpServletResponse response = run(request);

        assertEquals(403, response.getStatus());
    }

    @Test
    void rejectsACrossSiteLoginAttemptEvenWithoutACookie() throws Exception {
        MockHttpServletRequest request = write("/auth/login");
        request.addHeader("Origin", "https://evil.example.com");

        MockHttpServletResponse response = run(request);

        assertEquals(403, response.getStatus());
    }

    @Test
    void acceptsACookieWriteWithoutOriginForMobileAndCurl() throws Exception {
        MockHttpServletRequest request = write("/orders");
        request.setCookies(new Cookie("foodie_session", "token"));

        assertNotNull(run(request));
    }

    @Test
    void acceptsBearerCallsWithoutCookieEvenWithAnOriginHeader() throws Exception {
        MockHttpServletRequest request = write("/orders");
        request.addHeader("Authorization", "Bearer token");
        request.addHeader("Origin", "https://evil.example.com");

        assertNotNull(run(request));
    }

    @Test
    void doesNotBlockWebhooks() throws Exception {
        MockHttpServletRequest request = write("/webhooks/mercadopago");
        request.addHeader("Origin", "https://evil.example.com");

        assertNotNull(run(request));
    }

    @Test
    void doesNotBlockReads() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
        request.setCookies(new Cookie("foodie_session", "token"));
        request.addHeader("Origin", "https://evil.example.com");

        assertNotNull(run(request));
    }

    @Test
    void acceptsAnAllowedRefererWhenOriginIsMissing() throws Exception {
        MockHttpServletRequest request = write("/orders");
        request.setCookies(new Cookie("foodie_session", "token"));
        request.addHeader("Referer", "https://admin.example.com/painel/pedidos");

        assertNotNull(run(request));
    }

    private MockHttpServletResponse run(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        if (response.getStatus() == 200) assertNotNull(chain.getRequest());
        else assertNull(chain.getRequest());
        return response;
    }

    private static MockHttpServletRequest write(String path) {
        return new MockHttpServletRequest("POST", path);
    }
}
