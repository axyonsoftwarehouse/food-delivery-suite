package com.foodie.api.courier;

import java.time.Duration;
import java.time.Instant;

/** Status do entregador para a loja (parte D): turno aberto, entregas em mãos e idade da última posição. */
public final class CourierStatus {
    public static final String AVAILABLE = "available";
    public static final String DELIVERING = "delivering";
    public static final String NO_SIGNAL = "no_signal";
    public static final String OFF_SHIFT = "off_shift";
    /** Posição mais velha que isto não é mostrada como atual: vira "Sem sinal". */
    public static final Duration FRESH = Duration.ofMinutes(2);

    private CourierStatus() {}

    public static boolean fresh(Instant locationAt, Instant now) {
        return locationAt != null && !locationAt.isBefore(now.minus(FRESH));
    }

    public static String of(boolean onShift, long activeDeliveries, Instant locationAt, Instant now) {
        if (activeDeliveries > 0) return DELIVERING;
        if (!onShift) return OFF_SHIFT;
        return fresh(locationAt, now) ? AVAILABLE : NO_SIGNAL;
    }
}
