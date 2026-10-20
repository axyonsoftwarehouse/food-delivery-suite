package com.foodie.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.sentry.Breadcrumb;
import io.sentry.SentryEvent;
import io.sentry.protocol.Request;
import io.sentry.protocol.User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SentryPiiConfigTest {
    private final SentryPiiConfig config = new SentryPiiConfig();

    @Test
    void removesPersonalDataBeforeSending() {
        SentryEvent event = new SentryEvent(new RuntimeException("falha"));
        event.setUser(new User());
        event.setRequest(new Request());
        event.setExtras(Map.of("email", "cliente@example.com"));
        event.setBreadcrumbs(List.of(new Breadcrumb()));

        SentryEvent scrubbed = config.scrubSentryPii().execute(event, null);

        assertThat(scrubbed).isNotNull();
        assertThat(scrubbed.getUser()).isNull();
        assertThat(scrubbed.getRequest()).isNull();
        assertThat(scrubbed.getExtras()).isNull();
        assertThat(scrubbed.getBreadcrumbs()).isNull();
    }
}
