package com.foodie.api.orders;

import com.foodie.api.ApiException;

/** Motivos fixos da falha de entrega (entregador, parte B). Não editáveis pela loja. */
public enum DeliveryFailureReason {
    CUSTOMER_ABSENT("customer_absent", "Cliente ausente"),
    ADDRESS_NOT_FOUND("address_not_found", "Endereço não encontrado"),
    CUSTOMER_REFUSED("customer_refused", "Cliente recusou o pedido"),
    NO_ANSWER("no_answer", "Não atende o telefone"),
    OTHER("other", "Outro");

    private final String code;
    private final String label;

    DeliveryFailureReason(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() { return code; }
    public String label() { return label; }

    public static DeliveryFailureReason fromCode(String code) {
        for (DeliveryFailureReason reason : values()) if (reason.code.equals(code)) return reason;
        throw new ApiException(400, "Motivo da falha inválido");
    }

    /** Texto do evento do pedido: o rótulo, mais a observação quando houver; "Outro" a exige. */
    public static String compose(DeliveryFailureReason reason, String note) {
        String trimmed = note == null ? "" : note.strip();
        if (reason == OTHER && trimmed.length() < 3) throw new ApiException(400, "Descreva o motivo da falha");
        return trimmed.isEmpty() ? reason.label : reason.label + ": " + trimmed;
    }
}
