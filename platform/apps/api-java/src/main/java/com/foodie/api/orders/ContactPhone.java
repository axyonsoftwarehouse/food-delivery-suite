package com.foodie.api.orders;

import com.foodie.api.ApiException;

/** Telefone de contato (pedido de entrega e loja): só dígitos, com DDD, 10 ou 11 dígitos. */
public final class ContactPhone {
    private ContactPhone() {}

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() < 10 || digits.length() > 11 || digits.charAt(0) == '0' || !raw.matches("[\\d\\s()+.-]+")) {
            throw new ApiException(400, "Telefone inválido: use DDD + número (10 ou 11 dígitos)");
        }
        return digits;
    }

    /** Entrega exige telefone para o entregador falar com quem recebe; nos outros tipos ele é ignorado. */
    public static String requireForDelivery(String orderType, String raw) {
        boolean delivery = orderType == null || orderType.isBlank() || "delivery".equals(orderType);
        if (!delivery) return null;
        String phone = normalize(raw);
        if (phone == null) throw new ApiException(400, "Informe um telefone de contato para a entrega");
        return phone;
    }
}
