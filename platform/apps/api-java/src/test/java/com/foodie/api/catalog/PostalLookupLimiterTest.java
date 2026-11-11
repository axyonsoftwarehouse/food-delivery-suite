package com.foodie.api.catalog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class PostalLookupLimiterTest {
    private static class MovableClock extends Clock {
        long millis = 1_000_000;
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
    }

    @Test
    void blocksAfterTheLimitAndReleasesAfterTheWindow() {
        MovableClock clock = new MovableClock();
        PostalLookupLimiter limiter = new PostalLookupLimiter(clock);
        for (int i = 0; i < PostalLookupLimiter.LIMIT; i++) assertTrue(limiter.allow("1.1.1.1"));
        assertFalse(limiter.allow("1.1.1.1"));
        assertTrue(limiter.allow("2.2.2.2"), "outro IP tem a própria cota");
        clock.millis += PostalLookupLimiter.WINDOW_MILLIS;
        assertTrue(limiter.allow("1.1.1.1"));
    }

    @Test
    void unknownIpIsNotLimited() {
        PostalLookupLimiter limiter = new PostalLookupLimiter();
        for (int i = 0; i < PostalLookupLimiter.LIMIT * 2; i++) assertTrue(limiter.allow(null));
    }
}
