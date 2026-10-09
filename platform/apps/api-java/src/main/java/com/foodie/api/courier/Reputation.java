package com.foodie.api.courier;

/** Nota do entregador (parte C): a média só aparece com avaliações suficientes para não enganar. */
public final class Reputation {
    public static final int MIN_REVIEWS_TO_SHOW = 5;

    private Reputation() {}

    public static Double average(long sum, long count) {
        if (count < MIN_REVIEWS_TO_SHOW) return null;
        return Math.round(sum * 10.0 / count) / 10.0;
    }
}
