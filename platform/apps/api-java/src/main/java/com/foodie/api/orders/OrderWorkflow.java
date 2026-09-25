package com.foodie.api.orders;

import com.foodie.api.ApiException;

public final class OrderWorkflow {
    private OrderWorkflow() {}

    public static String nextStatus(String current, String action, String role) {
        String expectedFrom;
        String expectedRole;
        String next;
        switch (action) {
            case "accept" -> { expectedFrom = "placed"; expectedRole = "restaurant"; next = "accepted"; }
            case "ready" -> { expectedFrom = "accepted"; expectedRole = "restaurant"; next = "ready"; }
            case "assign" -> { expectedFrom = "ready"; expectedRole = "admin"; next = "assigned"; }
            case "pickup" -> { expectedFrom = "assigned"; expectedRole = "courier"; next = "picked_up"; }
            case "deliver" -> { expectedFrom = "picked_up"; expectedRole = "courier"; next = "delivered"; }
            default -> throw new ApiException(409, "Transição de pedido não permitida");
        }
        if (!expectedFrom.equals(current) || !expectedRole.equals(role)) {
            throw new ApiException(409, "Transição de pedido não permitida");
        }
        return next;
    }

    public static long total(long subtotal, long fee, long minimum) {
        if (subtotal < 0 || fee < 0 || minimum < 0) throw new ApiException(400, "Valores de entrega inválidos");
        if (subtotal < minimum) throw new ApiException(400, "Pedido abaixo do valor mínimo da zona");
        long total;
        try { total = Math.addExact(subtotal, fee); }
        catch (ArithmeticException error) { throw new ApiException(400, "Valor do pedido inválido"); }
        if (total > 100_000_000) throw new ApiException(400, "Valor do pedido inválido");
        return total;
    }
}
