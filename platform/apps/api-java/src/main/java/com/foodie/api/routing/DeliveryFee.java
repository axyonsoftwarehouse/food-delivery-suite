package com.foodie.api.routing;

public final class DeliveryFee {
    private DeliveryFee() {}

    public static long compute(long fixedFeeCents, Integer baseFeeCents, Integer perKmCents, Long distanceMeters) {
        if (perKmCents == null || perKmCents <= 0 || distanceMeters == null) return fixedFeeCents;
        long base = baseFeeCents == null ? fixedFeeCents : baseFeeCents;
        long km = (distanceMeters + 999) / 1000;
        return Math.addExact(base, Math.multiplyExact(km, perKmCents.longValue()));
    }
}
