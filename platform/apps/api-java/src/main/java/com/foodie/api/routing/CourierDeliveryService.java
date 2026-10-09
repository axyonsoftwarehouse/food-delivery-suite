package com.foodie.api.routing;

import com.foodie.api.ApiException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Entregas do próprio entregador (área do entregador, parte A). Os telefones de contato do cliente e da loja
 * só saem pelas entregas em andamento: quando a entrega termina, o número deixa de aparecer.
 */
@Service
public class CourierDeliveryService {
    private static final String ACTIVE = "SELECT o.id, o.status, o.created_at, o.distance_meters, o.delivery_address_text, o.contact_phone, o.total_cents, "
        + "c.name AS customer_name, a.complement, a.latitude AS customer_latitude, a.longitude AS customer_longitude, "
        + "r.name AS restaurant_name, r.address_text AS restaurant_address, r.latitude AS restaurant_latitude, r.longitude AS restaurant_longitude, "
        + "r.phone AS restaurant_phone, p.method AS payment_method, p.modality AS payment_modality, p.status AS payment_status, "
        + "p.amount_due_cents, p.change_for_cents "
        + "FROM orders o JOIN restaurants r ON r.id = o.restaurant_id LEFT JOIN users c ON c.id = o.customer_id "
        + "LEFT JOIN addresses a ON a.id = o.address_id LEFT JOIN order_payments p ON p.order_id = o.id "
        + "WHERE o.courier_id = ? AND o.status IN ('assigned','picked_up') ORDER BY (o.status = 'picked_up') DESC, o.id";

    private final JdbcTemplate jdbc;

    public CourierDeliveryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> active(long courierId) {
        List<Map<String, Object>> rows = jdbc.queryForList(ACTIVE, courierId);
        return rows.stream().map(row -> {
            Map<String, Object> delivery = new LinkedHashMap<>(row);
            delivery.put("items", jdbc.queryForList(
                "SELECT name, variation_name, quantity FROM order_items WHERE order_id = ? ORDER BY id", ((Number) row.get("id")).longValue()));
            return delivery;
        }).toList();
    }

    public List<Map<String, Object>> history(long courierId, String period) {
        String since = switch (period == null ? "today" : period) {
            case "today" -> "CURDATE()";
            case "week" -> "(CURDATE() - INTERVAL 6 DAY)";
            default -> throw new ApiException(400, "Período inválido");
        };
        return jdbc.queryForList(
            "SELECT o.id, o.status, o.created_at, r.name AS restaurant_name, o.delivery_address_text, o.delivery_fee_cents, o.tip_cents "
                + "FROM orders o JOIN restaurants r ON r.id = o.restaurant_id "
                + "WHERE o.courier_id = ? AND o.status NOT IN ('assigned','picked_up') AND o.created_at >= " + since + " ORDER BY o.id DESC LIMIT 200",
            courierId);
    }

    /** Dados do Perfil: a loja à qual o entregador está ligado e o veículo cadastrado. */
    public Map<String, Object> profile(long courierId) {
        return jdbc.queryForList(
            "SELECT u.name, u.email, r.name AS restaurant_name, cp.vehicle_type, cp.vehicle_plate FROM users u "
                + "LEFT JOIN restaurants r ON r.id = u.restaurant_id LEFT JOIN courier_profiles cp ON cp.user_id = u.id WHERE u.id = ?", courierId)
            .stream().findFirst().orElseThrow(() -> new ApiException(404, "Entregador não encontrado"));
    }

    public boolean hasActiveDelivery(long courierId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM orders WHERE courier_id = ? AND status IN ('assigned','picked_up')", Integer.class, courierId);
        return count != null && count > 0;
    }
}
