package com.foodie.api.orders;

import com.foodie.api.ApiException;
import java.util.Set;

public final class OrderWorkflow {
    private OrderWorkflow() {}

    private static final Set<String> ACTIVE = Set.of("placed", "accepted", "ready", "assigned", "picked_up");

    public record Transition(String nextStatus, boolean requiresReason, boolean clearsCourier) {}

    public static Transition resolve(String current, String action, String role) {
        if (!allowed(current, action, role)) throw new ApiException(409, "Transição de pedido não permitida");
        return switch (action) {
            case "accept" -> new Transition("accepted", false, false);
            case "ready" -> new Transition("ready", false, false);
            case "assign" -> new Transition("assigned", false, false);
            case "unassign" -> new Transition("ready", false, true);
            case "pickup" -> new Transition("picked_up", false, false);
            case "deliver" -> new Transition("delivered", false, false);
            case "fail" -> new Transition("failed", true, false);
            case "reject" -> new Transition("rejected", true, false);
            case "cancel" -> new Transition("cancelled", true, false);
            default -> throw new ApiException(409, "Transição de pedido não permitida");
        };
    }

    private static boolean allowed(String current, String action, String role) {
        return switch (action) {
            case "accept" -> "restaurant".equals(role) && "placed".equals(current);
            case "ready" -> "restaurant".equals(role) && "accepted".equals(current);
            case "reject" -> "restaurant".equals(role) && "placed".equals(current);
            case "assign" -> "admin".equals(role) && ("ready".equals(current) || "assigned".equals(current));
            case "unassign" -> "admin".equals(role) && "assigned".equals(current);
            case "pickup" -> "courier".equals(role) && "assigned".equals(current);
            case "deliver" -> "courier".equals(role) && "picked_up".equals(current);
            case "fail" -> "courier".equals(role) && ("assigned".equals(current) || "picked_up".equals(current));
            case "cancel" -> ("customer".equals(role) && "placed".equals(current))
                || ("admin".equals(role) && ACTIVE.contains(current));
            default -> false;
        };
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
