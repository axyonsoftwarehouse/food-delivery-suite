package com.foodie.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foodie.api.ApiException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DashboardServiceTest {
    @Test
    void defaultsTo30DaysEndingAtGivenDate() {
        DashboardService.DateRange range = DashboardService.resolveRange(null, "2026-09-27");
        assertThat(range.from()).isEqualTo(LocalDate.of(2026, 8, 29));
        assertThat(range.to()).isEqualTo(LocalDate.of(2026, 9, 27));
    }

    @Test
    void acceptsExplicitRange() {
        DashboardService.DateRange range = DashboardService.resolveRange("2026-09-01", "2026-09-10");
        assertThat(range.from()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(range.to()).isEqualTo(LocalDate.of(2026, 9, 10));
    }

    @Test
    void rejectsInvertedRange() {
        assertThatThrownBy(() -> DashboardService.resolveRange("2026-09-10", "2026-09-01"))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).status()).isEqualTo(400));
    }

    @Test
    void rejectsInvalidDate() {
        assertThatThrownBy(() -> DashboardService.resolveRange("ontem", "2026-09-10"))
            .isInstanceOf(ApiException.class);
    }
}
