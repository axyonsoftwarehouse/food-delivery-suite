package com.foodie.api.courier;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class CourierStatusTest {
    private static final Instant NOW = Instant.parse("2026-10-10T15:00:00Z");

    @Test
    void deliveringWinsEvenOffShift() {
        assertThat(CourierStatus.of(false, 2, null, NOW)).isEqualTo("delivering");
        assertThat(CourierStatus.of(true, 1, NOW, NOW)).isEqualTo("delivering");
    }

    @Test
    void onShiftWithAPositionOfUpToTwoMinutesIsAvailable() {
        assertThat(CourierStatus.of(true, 0, NOW.minusSeconds(120), NOW)).isEqualTo("available");
    }

    @Test
    void onShiftWithAnOldOrMissingPositionHasNoSignal() {
        assertThat(CourierStatus.of(true, 0, NOW.minusSeconds(121), NOW)).isEqualTo("no_signal");
        assertThat(CourierStatus.of(true, 0, null, NOW)).isEqualTo("no_signal");
    }

    @Test
    void withoutShiftAndWithoutDeliveryIsOffShift() {
        assertThat(CourierStatus.of(false, 0, NOW, NOW)).isEqualTo("off_shift");
    }
}
