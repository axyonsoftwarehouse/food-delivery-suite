package com.foodie.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class MobileSessionFilterTest {
    private final MobileSessionFilter filter = new MobileSessionFilter();

    @Test
    void bearerTokenIsExposedAsSessionCookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + "a".repeat(64));
        AtomicReference<HttpServletRequest> seen = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), capture(seen));

        assertThat(sessionCookie(seen.get())).isEqualTo("a".repeat(64));
    }

    @Test
    void existingCookieWinsOverBearerHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("foodie_session", "cookie-token"));
        request.addHeader("Authorization", "Bearer bearer-token");
        AtomicReference<HttpServletRequest> seen = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), capture(seen));

        assertThat(sessionCookie(seen.get())).isEqualTo("cookie-token");
        assertThat(seen.get().getCookies()).hasSize(1);
    }

    @Test
    void requestsWithoutBearerKeepOriginalCookiesUntouched() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        AtomicReference<HttpServletRequest> seen = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), capture(seen));

        assertThat(seen.get()).isSameAs(request);
        assertThat(seen.get().getCookies()).isNull();
    }

    private static FilterChain capture(AtomicReference<HttpServletRequest> seen) {
        return (servletRequest, servletResponse) -> seen.set((HttpServletRequest) servletRequest);
    }

    private static String sessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        return cookies == null ? null : cookies[0].getValue();
    }
}
