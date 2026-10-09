package com.foodie.api.orders;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/** Código de confirmação de entrega (entregador, parte B): 4 dígitos que só o cliente vê. */
public final class DeliveryCode {
    public static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private DeliveryCode() {}

    public static String generate() {
        return String.format("%04d", RANDOM.nextInt(10_000));
    }

    public static boolean matches(String expected, String given) {
        if (expected == null || given == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
    }

    public static int attemptsLeft(int attempts) {
        return Math.max(0, MAX_ATTEMPTS - attempts);
    }
}
