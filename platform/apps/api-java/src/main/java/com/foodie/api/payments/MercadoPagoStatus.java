package com.foodie.api.payments;

public final class MercadoPagoStatus {
    private MercadoPagoStatus() {}

    public static String normalize(String status) {
        if (status == null) return "pending";
        return switch (status) {
            case "approved" -> "paid";
            case "pending", "in_process", "authorized", "in_mediation" -> "pending";
            case "rejected" -> "rejected";
            case "cancelled" -> "cancelled";
            case "refunded", "charged_back" -> "refunded";
            case "expired" -> "expired";
            default -> "pending";
        };
    }
}
