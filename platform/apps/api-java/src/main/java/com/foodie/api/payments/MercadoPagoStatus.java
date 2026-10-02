package com.foodie.api.payments;

public final class MercadoPagoStatus {
    private MercadoPagoStatus() {}

    /**
     * Traduz o status do provedor para o status do Foodie. Os valores da API de Orders ({@code processed},
     * {@code action_required}, {@code failed}) convivem aqui com os da Payments API ({@code approved},
     * {@code in_process}), porque cobrança antiga ainda é consultada pelo caminho antigo.
     */
    public static String normalize(String status) {
        if (status == null) return "pending";
        return switch (status) {
            case "approved", "processed" -> "paid";
            case "pending", "in_process", "authorized", "in_mediation", "action_required", "waiting_transfer",
                 "waiting_payment", "created", "processing" -> "pending";
            case "rejected", "failed" -> "rejected";
            case "cancelled", "canceled", "expired" -> status.equals("expired") ? "expired" : "cancelled";
            case "refunded", "charged_back" -> "refunded";
            default -> "pending";
        };
    }
}
