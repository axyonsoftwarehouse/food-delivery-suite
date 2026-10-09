package com.foodie.api.orders;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeliveryCodeTest {
    @Test
    void generatesFourDigitsKeepingLeadingZeros() {
        for (int i = 0; i < 500; i++) assertThat(DeliveryCode.generate()).matches("\\d{4}");
    }

    @Test
    void matchesOnlyTheExactCode() {
        assertThat(DeliveryCode.matches("0427", "0427")).isTrue();
        assertThat(DeliveryCode.matches("0427", "0428")).isFalse();
        assertThat(DeliveryCode.matches("0427", "427")).isFalse();
        assertThat(DeliveryCode.matches("0427", null)).isFalse();
        assertThat(DeliveryCode.matches(null, "0427")).isFalse();
    }

    @Test
    void attemptsLeftNeverGoesBelowZero() {
        assertThat(DeliveryCode.attemptsLeft(0)).isEqualTo(5);
        assertThat(DeliveryCode.attemptsLeft(4)).isEqualTo(1);
        assertThat(DeliveryCode.attemptsLeft(5)).isZero();
        assertThat(DeliveryCode.attemptsLeft(9)).isZero();
    }
}
