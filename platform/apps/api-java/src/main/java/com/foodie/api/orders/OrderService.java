package com.foodie.api.orders;

import com.foodie.api.ApiException;
import com.foodie.api.auth.User;
import com.foodie.api.catalog.PostalCoverageService;
import com.foodie.api.hours.RestaurantHoursService;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final PostalCoverageService postalCoverage;
    private final RestaurantHoursService hours;
    private final PaymentService payments;

    public OrderService(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc, PostalCoverageService postalCoverage, RestaurantHoursService hours, PaymentService payments) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
        this.postalCoverage = postalCoverage;
        this.hours = hours;
        this.payments = payments;
    }

    @Transactional
    public Map<String, Object> create(User customer, OrderController.OrderRequest request) {
        Set<Long> distinctIds = new HashSet<>();
        for (var item : request.items()) if (!distinctIds.add(item.productId())) throw new ApiException(400, "Produto repetido no pedido");
        List<Map<String, Object>> addresses = jdbc.queryForList(
            "SELECT a.id, a.zone_id, a.postal_code, a.street, a.number, a.neighborhood, a.complement, z.city, z.state, z.delivery_fee_cents, z.minimum_order_cents FROM addresses a JOIN zones z ON z.id = a.zone_id AND z.active = TRUE WHERE a.id = ? AND a.user_id = ?",
            request.addressId(), customer.id()
        );
        if (addresses.isEmpty()) throw new ApiException(400, "Endereço não encontrado ou zona indisponível");
        Map<String, Object> address = addresses.getFirst();
        long zoneId = number(address, "zone_id");
        if (address.get("postal_code") == null) throw new ApiException(409, "Recadastre o endereço com CEP antes de pedir");
        postalCoverage.requireAddressZone((String) address.get("postal_code"), zoneId);
        List<Map<String, Object>> restaurants = jdbc.queryForList(
            "SELECT r.timezone FROM restaurants r JOIN restaurant_zones rz ON rz.restaurant_id = r.id WHERE r.id = ? AND r.active = TRUE AND rz.zone_id = ? FOR UPDATE",
            request.restaurantId(), zoneId
        );
        if (restaurants.isEmpty()) throw new ApiException(400, "Restaurante não atende este endereço ou está fechado");
        if (!hours.isOpen(request.restaurantId(), (String) restaurants.getFirst().get("timezone"))) {
            throw new ApiException(409, "Restaurante está fora do horário de funcionamento");
        }
        List<Map<String, Object>> rows = namedJdbc.queryForList(
            "SELECT id, name, price_cents FROM products WHERE restaurant_id = :restaurantId AND available = TRUE AND id IN (:ids) FOR UPDATE",
            new MapSqlParameterSource("restaurantId", request.restaurantId()).addValue("ids", distinctIds)
        );
        if (rows.size() != request.items().size()) throw new ApiException(400, "Há produtos indisponíveis");
        Map<Long, Map<String, Object>> products = new HashMap<>();
        for (Map<String, Object> row : rows) products.put(number(row, "id"), row);
        long subtotal = 0;
        try {
            for (var item : request.items()) {
                subtotal = Math.addExact(subtotal, Math.multiplyExact(number(products.get(item.productId()), "price_cents"), item.quantity()));
            }
        } catch (ArithmeticException error) {
            throw new ApiException(400, "Valor do pedido inválido");
        }
        long fee = number(address, "delivery_fee_cents");
        long total = OrderWorkflow.total(subtotal, fee, number(address, "minimum_order_cents"));
        long finalSubtotal = subtotal;
        String complement = (String) address.get("complement");
        String addressText = address.get("street") + ", " + address.get("number")
            + (complement == null || complement.isEmpty() ? "" : ", " + complement)
            + " • " + address.get("neighborhood") + " • " + address.get("city") + "/" + address.get("state") + " • CEP " + address.get("postal_code");
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO orders (customer_id, restaurant_id, zone_id, address_id, delivery_address_text, subtotal_cents, delivery_fee_cents, total_cents) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, customer.id());
            statement.setLong(2, request.restaurantId());
            statement.setLong(3, zoneId);
            statement.setLong(4, request.addressId());
            statement.setString(5, addressText);
            statement.setLong(6, finalSubtotal);
            statement.setLong(7, fee);
            statement.setLong(8, total);
            return statement;
        }, key);
        long orderId = key.getKey().longValue();
        for (var item : request.items()) {
            Map<String, Object> product = products.get(item.productId());
            jdbc.update("INSERT INTO order_items (order_id, product_id, name, quantity, unit_price_cents) VALUES (?, ?, ?, ?, ?)",
                orderId, item.productId(), product.get("name"), item.quantity(), number(product, "price_cents"));
        }
        jdbc.update("INSERT INTO order_events (order_id, actor_id, from_status, to_status) VALUES (?, ?, NULL, ?)", orderId, customer.id(), "placed");
        payments.create(orderId, request.paymentMethod(), total, request.changeForCents());
        return Map.of("id", orderId, "status", "placed", "subtotalCents", subtotal, "deliveryFeeCents", fee, "totalCents", total, "address", addressText, "paymentMethod", request.paymentMethod());
    }

    public List<Map<String, Object>> list(User user) {
        expireStale();
        String base = "SELECT o.id, o.status, o.subtotal_cents, o.delivery_fee_cents, o.total_cents, o.delivery_address_text, o.restaurant_id, o.courier_id, o.created_at, r.name AS restaurant_name, p.method AS payment_method, p.status AS payment_status, p.amount_due_cents AS payment_due_cents FROM orders o JOIN restaurants r ON r.id = o.restaurant_id LEFT JOIN order_payments p ON p.order_id = o.id ";
        return switch (user.role()) {
            case "customer" -> jdbc.queryForList(base + "WHERE o.customer_id = ? ORDER BY o.id DESC LIMIT 100", user.id());
            case "restaurant" -> jdbc.queryForList(base + "WHERE o.restaurant_id = ? ORDER BY o.id DESC LIMIT 100", user.restaurantId());
            case "courier" -> jdbc.queryForList(base + "WHERE o.courier_id = ? ORDER BY o.id DESC LIMIT 100", user.id());
            default -> jdbc.queryForList(base + "ORDER BY o.id DESC LIMIT 100");
        };
    }

    public Map<String, Object> detail(User user, long orderId) {
        expireStale();
        List<Map<String, Object>> orders = jdbc.queryForList("SELECT * FROM orders WHERE id = ?", orderId);
        if (orders.isEmpty()) throw new ApiException(404, "Pedido não encontrado");
        Map<String, Object> order = orders.getFirst();
        checkAccess(user, order);
        Map<String, Object> result = new LinkedHashMap<>(order);
        result.put("items", jdbc.queryForList("SELECT name, quantity, unit_price_cents FROM order_items WHERE order_id = ?", orderId));
        result.put("history", jdbc.queryForList("SELECT from_status, to_status, reason, created_at FROM order_events WHERE order_id = ? ORDER BY id", orderId));
        result.put("payment", payments.detail(orderId));
        return result;
    }

    @Transactional
    public Map<String, Object> changeStatus(User user, long orderId, String action, Long courierId, String reason) {
        List<Map<String, Object>> orders = jdbc.queryForList("SELECT id, customer_id, restaurant_id, courier_id, status FROM orders WHERE id = ? FOR UPDATE", orderId);
        if (orders.isEmpty()) throw new ApiException(404, "Pedido não encontrado");
        Map<String, Object> order = orders.getFirst();
        checkAccess(user, order);
        String current = (String) order.get("status");
        OrderWorkflow.Transition transition = OrderWorkflow.resolve(current, action, user.role());
        String trimmed = reason == null ? null : reason.strip();
        if (transition.requiresReason() && (trimmed == null || trimmed.length() < 3)) {
            throw new ApiException(400, "Informe o motivo (3 a 255 caracteres)");
        }
        String next = transition.nextStatus();
        if ("deliver".equals(action) && !"paid".equals(payments.status(orderId))) {
            throw new ApiException(409, "Confirme o recebimento do pagamento antes de concluir a entrega");
        }
        if ("assign".equals(action)) {
            if (courierId == null || courierId < 1) throw new ApiException(400, "Selecione um entregador");
            Integer courier = jdbc.query("SELECT 1 FROM users WHERE id = ? AND role = 'courier' AND suspended_at IS NULL AND courier_approved_at IS NOT NULL", rs -> rs.next() ? 1 : null, courierId);
            if (courier == null) throw new ApiException(400, "Entregador não aprovado ou indisponível");
            jdbc.update("UPDATE orders SET status = ?, courier_id = ? WHERE id = ?", next, courierId, orderId);
        } else if (transition.clearsCourier()) {
            jdbc.update("UPDATE orders SET status = ?, courier_id = NULL WHERE id = ?", next, orderId);
        } else {
            jdbc.update("UPDATE orders SET status = ? WHERE id = ?", next, orderId);
        }
        if (Set.of("rejected", "cancelled", "expired", "failed").contains(next)) payments.cancelPending(orderId);
        jdbc.update("INSERT INTO order_events (order_id, actor_id, from_status, to_status, reason) VALUES (?, ?, ?, ?, ?)", orderId, user.id(), current, next, trimmed);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", orderId);
        result.put("status", next);
        if ("assign".equals(action)) result.put("courierId", courierId);
        return result;
    }

    private void expireStale() {
        List<Long> stale = jdbc.query("SELECT id FROM orders WHERE status = 'placed' AND created_at < (NOW() - INTERVAL 15 MINUTE)", (rs, row) -> rs.getLong(1));
        for (Long id : stale) {
            if (jdbc.update("UPDATE orders SET status = 'expired' WHERE id = ? AND status = 'placed'", id) == 1) {
                payments.cancelPending(id);
                jdbc.update("INSERT INTO order_events (order_id, actor_id, from_status, to_status, reason) VALUES (?, NULL, 'placed', 'expired', ?)", id, "Sem aceite em 15 minutos");
            }
        }
    }

    private static void checkAccess(User user, Map<String, Object> order) {
        boolean denied = switch (user.role()) {
            case "customer" -> number(order, "customer_id") != user.id();
            case "restaurant" -> user.restaurantId() == null || number(order, "restaurant_id") != user.restaurantId();
            case "courier" -> order.get("courier_id") == null || number(order, "courier_id") != user.id();
            default -> false;
        };
        if (denied) throw new ApiException(403, "Acesso não autorizado");
    }

    private static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }
}
