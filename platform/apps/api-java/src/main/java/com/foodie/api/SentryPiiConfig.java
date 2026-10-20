package com.foodie.api;

import io.sentry.SentryOptions.BeforeSendCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SentryPiiConfig {
    @Bean
    BeforeSendCallback scrubSentryPii() {
        return (event, hint) -> {
            event.setUser(null);
            event.setRequest(null);
            event.setExtras(null);
            event.setBreadcrumbs(null);
            return event;
        };
    }
}
