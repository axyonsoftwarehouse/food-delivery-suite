package com.foodie.api.finance;

import com.foodie.api.auth.User;

/** Parte do razão: admin e restaurante para histórico; entregador para repasses atuais. */
public record Party(String party, Long id) {
    public static Party of(User user) {
        return switch (user.role()) {
            case "admin" -> new Party("admin", null);
            case "restaurant" -> user.restaurantId() == null ? null : new Party("restaurant", user.restaurantId());
            case "courier" -> new Party("courier", user.id());
            case "customer" -> new Party("customer", user.id());
            default -> null;
        };
    }
}
