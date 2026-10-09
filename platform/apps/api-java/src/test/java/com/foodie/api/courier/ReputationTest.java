package com.foodie.api.courier;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReputationTest {
    @Test
    void hidesTheAverageBelowFiveReviews() {
        assertThat(Reputation.average(20, 4)).isNull();
        assertThat(Reputation.average(0, 0)).isNull();
    }

    @Test
    void showsTheAverageWithOneDecimalFromFiveReviews() {
        assertThat(Reputation.average(24, 5)).isEqualTo(4.8);
        assertThat(Reputation.average(110, 23)).isEqualTo(4.8);
        assertThat(Reputation.average(25, 5)).isEqualTo(5.0);
        assertThat(Reputation.average(13, 5)).isEqualTo(2.6);
    }
}
