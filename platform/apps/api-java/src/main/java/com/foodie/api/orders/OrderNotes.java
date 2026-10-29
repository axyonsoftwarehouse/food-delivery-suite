package com.foodie.api.orders;

/**
 * Observações do cliente. A do item entra na chave da linha (carrinho e pedido), então precisa de uma forma
 * única: sem espaços nas pontas, espaços repetidos viram um, e vazio vira "". A do pedido vazia vira null.
 */
final class OrderNotes {
    static final int ITEM_MAX = 140;
    static final int ORDER_MAX = 300;

    private OrderNotes() {}

    static String item(String raw) {
        String note = clean(raw);
        return note.length() > ITEM_MAX ? note.substring(0, ITEM_MAX) : note;
    }

    static String order(String raw) {
        String note = clean(raw);
        if (note.isEmpty()) return null;
        return note.length() > ORDER_MAX ? note.substring(0, ORDER_MAX) : note;
    }

    private static String clean(String raw) {
        return raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
    }
}
