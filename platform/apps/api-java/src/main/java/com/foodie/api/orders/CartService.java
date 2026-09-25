package com.foodie.api.orders;

import com.foodie.api.ApiException;
import com.foodie.api.auth.User;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {
    private final JdbcTemplate jdbc;
    private final OrderService orders;

    public CartService(JdbcTemplate jdbc, OrderService orders) {
        this.jdbc = jdbc;
        this.orders = orders;
    }

    @Transactional
    public CartSnapshot get(User customer) {
        lock(customer.id());
        prune(customer.id());
        return snapshot(customer.id());
    }

    @Transactional
    public CartSnapshot change(User customer, long productId, int delta) {
        lock(customer.id());
        prune(customer.id());
        List<CartRow> current = rows(customer.id());
        CartRow existing = current.stream().filter(row -> row.productId() == productId).findFirst().orElse(null);
        if (delta > 0) {
            long restaurantId = availableRestaurant(productId);
            if (!current.isEmpty() && current.getFirst().restaurantId() != restaurantId) {
                throw new ApiException(400, "Um carrinho pode conter pratos de um restaurante por vez");
            }
            if (existing == null && current.size() >= 30) throw new ApiException(400, "O carrinho aceita até 30 pratos diferentes");
            if (existing != null && existing.quantity() >= 20) throw new ApiException(400, "Limite de 20 unidades por prato");
            if (existing == null) jdbc.update("INSERT INTO cart_items (user_id, product_id, quantity) VALUES (?, ?, 1)", customer.id(), productId);
            else jdbc.update("UPDATE cart_items SET quantity = quantity + 1 WHERE user_id = ? AND product_id = ?", customer.id(), productId);
        } else if (existing != null) {
            if (existing.quantity() == 1) jdbc.update("DELETE FROM cart_items WHERE user_id = ? AND product_id = ?", customer.id(), productId);
            else jdbc.update("UPDATE cart_items SET quantity = quantity - 1 WHERE user_id = ? AND product_id = ?", customer.id(), productId);
        }
        return snapshot(customer.id());
    }

    @Transactional
    public CartSnapshot importIfEmpty(User customer, List<CartController.ImportItem> items) {
        lock(customer.id());
        prune(customer.id());
        if (!rows(customer.id()).isEmpty()) return snapshot(customer.id());
        Set<Long> seen = new HashSet<>();
        Long restaurantId = null;
        for (var item : items) {
            if (!seen.add(item.productId())) continue;
            Long currentRestaurant = findAvailableRestaurant(item.productId());
            if (currentRestaurant == null || restaurantId != null && !restaurantId.equals(currentRestaurant)) continue;
            restaurantId = currentRestaurant;
            jdbc.update("INSERT INTO cart_items (user_id, product_id, quantity) VALUES (?, ?, ?)", customer.id(), item.productId(), item.quantity());
        }
        return snapshot(customer.id());
    }

    @Transactional
    public CartSnapshot clear(User customer) {
        lock(customer.id());
        jdbc.update("DELETE FROM cart_items WHERE user_id = ?", customer.id());
        return new CartSnapshot(List.of());
    }

    @Transactional
    public Map<String, Object> checkout(User customer, long addressId, long expectedTotalCents, String paymentMethod, Integer changeForCents) {
        lock(customer.id());
        prune(customer.id());
        List<CartRow> current = rows(customer.id());
        if (current.isEmpty()) throw new ApiException(400, "O carrinho está vazio");
        long restaurantId = current.getFirst().restaurantId();
        List<OrderController.Item> items = new ArrayList<>();
        for (CartRow row : current) items.add(new OrderController.Item(row.productId(), row.quantity()));
        Map<String, Object> order = orders.create(customer, new OrderController.OrderRequest(restaurantId, addressId, items, paymentMethod, changeForCents));
        if (((Number) order.get("totalCents")).longValue() != expectedTotalCents) {
            throw new ApiException(409, "O valor do pedido mudou. Atualize o carrinho antes de continuar");
        }
        jdbc.update("DELETE FROM cart_items WHERE user_id = ?", customer.id());
        return order;
    }

    private void lock(long customerId) {
        jdbc.query("SELECT id FROM users WHERE id = ? FOR UPDATE", rs -> { rs.next(); return null; }, customerId);
    }

    private void prune(long customerId) {
        jdbc.update("DELETE ci FROM cart_items ci JOIN products p ON p.id = ci.product_id JOIN restaurants r ON r.id = p.restaurant_id WHERE ci.user_id = ? AND (p.available = FALSE OR r.active = FALSE)", customerId);
    }

    private long availableRestaurant(long productId) {
        Long restaurantId = findAvailableRestaurant(productId);
        if (restaurantId == null) throw new ApiException(400, "Produto indisponível");
        return restaurantId;
    }

    private Long findAvailableRestaurant(long productId) {
        return jdbc.query(
            "SELECT p.restaurant_id FROM products p JOIN restaurants r ON r.id = p.restaurant_id WHERE p.id = ? AND p.available = TRUE AND r.active = TRUE",
            rs -> rs.next() ? rs.getLong(1) : null, productId
        );
    }

    private List<CartRow> rows(long customerId) {
        return jdbc.query(
            "SELECT ci.product_id, ci.quantity, p.restaurant_id, p.name, p.price_cents FROM cart_items ci JOIN products p ON p.id = ci.product_id WHERE ci.user_id = ? ORDER BY ci.updated_at, ci.product_id",
            (rs, row) -> new CartRow(rs.getLong("product_id"), rs.getInt("quantity"), rs.getLong("restaurant_id"), rs.getString("name"), rs.getInt("price_cents")), customerId
        );
    }

    private CartSnapshot snapshot(long customerId) {
        return new CartSnapshot(rows(customerId).stream().map(row -> new CartItem(row.productId(), row.quantity(), row.restaurantId(), row.name(), row.priceCents())).toList());
    }

    private record CartRow(long productId, int quantity, long restaurantId, String name, int priceCents) {}
    public record CartItem(long productId, int quantity, long restaurantId, String name, int priceCents) {}
    public record CartSnapshot(List<CartItem> items) {}
}
