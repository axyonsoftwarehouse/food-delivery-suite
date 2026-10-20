package com.foodie.api.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SentryServiceTest {
    @Test
    void reportsNotConfiguredWithoutToken() {
        SentryService service = new SentryService(
            "https://sentry.io/api/0",
            "axyon-software-house",
            "foodie-api, foodie-web",
            "",
            60);

        SentryService.Summary summary = service.summary();

        assertThat(summary.configured()).isFalse();
        assertThat(summary.available()).isFalse();
        assertThat(summary.projects()).extracting(SentryService.ProjectSummary::slug)
            .containsExactly("foodie-api", "foodie-web");
        assertThat(summary.totalIssues()).isZero();
        assertThat(summary.totalEvents()).isZero();
    }
}
