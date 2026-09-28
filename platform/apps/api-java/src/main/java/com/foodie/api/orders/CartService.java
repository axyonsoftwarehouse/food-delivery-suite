package com.foodie.api.orders;

import com.foodie.api.ApiException;
import com.foodie.api.auth.Tokens;
import com.foodie.api.auth.User;
import com.foodie.api.catalog.AddonService;
import com.foodie.api.settings.SettingsCatalog;
import com.foodie.api.settings.SettingsService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final AddonService addonService;
    private final ObjectMapper json;
    private final SettingsService settings;
    private final CampaignService campaigns;

    public CartService(JdbcTemplate jdbc, OrderService orders, AddonService addonService, ObjectMapper json, SettingsService settings, CampaignService campaigns) {
        this.jdbc = jdbc;
        this.orders = orders;
        this.addonService = addonService;
        this.json = json;
        this.settings = settings;
        this.campaigns = campaigns;
    }

    @Transactional
    public CartSnapshot get(User customer) {
        lock(customer.id());
        prune(customer.id());
        return snapshot(customer.id());
    }

    @Transactional
    public Map<String, Object> campaign(User customer) {
        CartSnapshot current = get(customer);
        if (current.items().isEmpty()) return Map.of("discountCents", 0);
        CampaignService.Applied applied = campaigns.best(current.items().getFirst().restaurantId(),
            current.items().stream().map(item -> new CampaignService.Line(item.productId(),
                item.unitPriceCents() * item.quantity())).toList());
        if (applied == null) return Map.of("discountCents", 0);
        return Map.of("campaignId", applied.campaignId(), "name", applied.name(),
            "discountCents", applied.discountCents());
    }

    @Transactional
    public CartSnapshot change(User customer, long productId, long variationId, List<Long> addonIds, int delta) {
        lock(customer.id());
        prune(customer.id());
        String addonKey = AddonService.key(addonIds);
        List<CartRow> current = rows(customer.id());
        CartRow existing = current.stream()
            .filter(row -> row.productId() == productId && row.variationId() == variationId && row.addonKey().equals(addonKey))
            .findFirst().orElse(null);
        if (delta > 0) {
            long restaurantId = availableRestaurant(productId, variationId);
            addonService.validate(productId, variationId, AddonService.parseKey(addonKey));
            if (!current.isEmpty() && current.getFirst().restaurantId() != restaurantId) {
                throw new ApiException(400, "Um carrinho pode conter pratos de um restaurante por vez");
            }
            if (existing == null && current.size() >= 30) throw new ApiException(400, "O carrinho aceita até 30 itens diferentes");
            if (existing != null && existing.quantity() >= 20) throw new ApiException(400, "Limite de 20 unidades por item");
            if (existing == null) {
                jdbc.update("INSERT INTO cart_items (user_id, product_id, variation_id, addon_key, quantity) VALUES (?, ?, ?, ?, 1)", customer.id(), productId, variationId, addonKey);
            } else {
                jdbc.update("UPDATE cart_items SET quantity = quantity + 1 WHERE user_id = ? AND product_id = ? AND variation_id = ? AND addon_key = ?", customer.id(), productId, variationId, addonKey);
            }
        } else if (existing != null) {
            if (existing.quantity() == 1) {
                jdbc.update("DELETE FROM cart_items WHERE user_id = ? AND product_id = ? AND variation_id = ? AND addon_key = ?", customer.id(), productId, variationId, addonKey);
            } else {
                jdbc.update("UPDATE cart_items SET quantity = quantity - 1 WHERE user_id = ? AND product_id = ? AND variation_id = ? AND addon_key = ?", customer.id(), productId, variationId, addonKey);
            }
        }
        return snapshot(customer.id());
    }

    @Transactional
    public CartSnapshot importIfEmpty(User customer, List<CartController.ImportItem> items) {
        lock(customer.id());
        prune(customer.id());
        if (!rows(customer.id()).isEmpty()) return snapshot(customer.id());
        Set<String> seen = new HashSet<>();
        Long restaurantId = null;
        for (var item : items) {
            long variationId = item.variationId() == null ? 0 : item.variationId();
            String addonKey = AddonService.key(item.addonIds());
            if (!seen.add(item.productId() + ":" + variationId + ":" + addonKey)) continue;
            Long currentRestaurant = findAvailableRestaurant(item.productId(), variationId);
            if (currentRestaurant == null || restaurantId != null && !restaurantId.equals(currentRestaurant)) continue;
            try { addonService.validate(item.productId(), variationId, AddonService.parseKey(addonKey)); } catch (ApiException invalid) { continue; }
            restaurantId = currentRestaurant;
            jdbc.update("INSERT INTO cart_items (user_id, product_id, variation_id, addon_key, quantity) VALUES (?, ?, ?, ?, ?)", customer.id(), item.productId(), variationId, addonKey, item.quantity());
        }
        return snapshot(customer.id());
    }

    @Transactional
    public CartSnapshot clear(User customer) {
        lock(customer.id());
        jdbc.update("DELETE FROM cart_items WHERE user_id = ?", customer.id());
        return new CartSnapshot(List.of(), "");
    }

    @Transactional
    public Map<String, Object> checkout(User customer, Long addressId, long expectedTotalCents, String expectedVersion, String idempotencyKey, String paymentMethod, Integer changeForCents, String modality, String couponCode, String scheduledFor, String orderType, Long tableId, Integer partySize, Integer tipCents) {
        lock(customer.id());
        prune(customer.id());
        String key = idempotencyKey == null ? null : idempotencyKey.trim();
        if (key != null && !key.isEmpty()) {
            List<Map<String, Object>> saved = jdbc.queryForList("SELECT response FROM order_idempotency WHERE customer_id = ? AND idem_key = ?", customer.id(), key);
            if (!saved.isEmpty()) return readResponse((String) saved.getFirst().get("response"));
        }
        if (settings.maintenanceActive()) throw new ApiException(503, settings.maintenanceMessage());
        String type = orderType == null || orderType.isBlank() ? "delivery" : orderType;
        boolean typeEnabled = switch (type) {
            case "delivery" -> settings.bool(SettingsCatalog.ORDER_DELIVERY);
            case "take_away" -> settings.bool(SettingsCatalog.ORDER_TAKEAWAY);
            case "dine_in" -> settings.bool(SettingsCatalog.ORDER_DINE_IN);
            default -> false;
        };
        if (!typeEnabled) throw new ApiException(400, "Tipo de pedido indisponível no momento");
        CartSnapshot current = snapshot(customer.id());
        if (current.items().isEmpty()) throw new ApiException(400, "O carrinho está vazio");
        if (expectedVersion != null && !expectedVersion.isBlank() && !expectedVersion.equals(current.version())) {
            throw new ApiException(409, "O carrinho mudou. Atualize antes de continuar");
        }
        long restaurantId = current.items().getFirst().restaurantId();
        List<OrderController.Item> items = new ArrayList<>();
        for (CartItem item : current.items()) {
            items.add(new OrderController.Item(item.productId(), item.variationId() == 0 ? null : item.variationId(), item.quantity(), item.addonIds().isEmpty() ? null : item.addonIds()));
        }
        Map<String, Object> order = orders.create(customer, new OrderController.OrderRequest(restaurantId, addressId, items, paymentMethod, changeForCents, modality, couponCode, scheduledFor, orderType, tableId, partySize, tipCents));
        if (((Number) order.get("totalCents")).longValue() != expectedTotalCents) {
            throw new ApiException(409, "O valor do pedido mudou. Atualize o carrinho antes de continuar");
        }
        jdbc.update("DELETE FROM cart_items WHERE user_id = ?", customer.id());
        if (key != null && !key.isEmpty()) {
            try {
                jdbc.update("INSERT INTO order_idempotency (customer_id, idem_key, order_id, response) VALUES (?, ?, ?, ?)",
                    customer.id(), key, ((Number) order.get("id")).longValue(), json.writeValueAsString(order));
            } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
                throw new ApiException(500, "Falha ao registrar idempotência do pedido");
            }
        }
        return order;
    }

    private Map<String, Object> readResponse(String value) {
        try {
            return json.readValue(value, new TypeReference<Map<String, Object>>() {});
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new ApiException(500, "Falha ao recuperar o pedido");
        }
    }

    private void lock(long customerId) {
        jdbc.query("SELECT id FROM users WHERE id = ? FOR UPDATE", rs -> { rs.next(); return null; }, customerId);
    }

    private void prune(long customerId) {
        jdbc.update("DELETE ci FROM cart_items ci JOIN products p ON p.id = ci.product_id JOIN restaurants r ON r.id = p.restaurant_id WHERE ci.user_id = ? AND (p.available = FALSE OR r.active = FALSE)", customerId);
    }

    private long availableRestaurant(long productId, long variationId) {
        Long restaurantId = findAvailableRestaurant(productId, variationId);
        if (restaurantId == null) throw new ApiException(400, "Produto indisponível");
        return restaurantId;
    }

    private Long findAvailableRestaurant(long productId, long variationId) {
        Long restaurantId = jdbc.query(
            "SELECT p.restaurant_id FROM products p JOIN restaurants r ON r.id = p.restaurant_id WHERE p.id = ? AND p.available = TRUE AND r.active = TRUE",
            rs -> rs.next() ? rs.getLong(1) : null, productId
        );
        if (restaurantId == null) return null;
        if (variationId != 0) {
            Integer variation = jdbc.query(
                "SELECT 1 FROM product_variations WHERE id = ? AND product_id = ? AND available = TRUE",
                rs -> rs.next() ? 1 : null, variationId, productId
            );
            if (variation == null) return null;
        }
        return restaurantId;
    }

    private List<CartRow> rows(long customerId) {
        return jdbc.query(
            "SELECT ci.product_id, ci.variation_id, ci.addon_key, ci.quantity, p.restaurant_id, p.name, p.price_cents, "
                + "v.name AS variation_name, COALESCE(v.price_delta_cents, 0) AS variation_delta_cents "
                + "FROM cart_items ci JOIN products p ON p.id = ci.product_id "
                + "LEFT JOIN product_variations v ON v.id = ci.variation_id "
                + "WHERE ci.user_id = ? ORDER BY ci.updated_at, ci.product_id",
            (rs, row) -> new CartRow(rs.getLong("product_id"), rs.getLong("variation_id"), rs.getString("addon_key"), rs.getInt("quantity"),
                rs.getLong("restaurant_id"), rs.getString("name"), rs.getInt("price_cents"), rs.getString("variation_name"), rs.getInt("variation_delta_cents")), customerId
        );
    }

    private CartSnapshot snapshot(long customerId) {
        List<CartItem> items = new ArrayList<>();
        for (CartRow row : rows(customerId)) {
            List<Long> addonIds = AddonService.parseKey(row.addonKey());
            List<Map<String, Object>> addons;
            try {
                addons = addonService.validate(row.productId(), row.variationId(), addonIds);
            } catch (ApiException invalid) {
                jdbc.update("DELETE FROM cart_items WHERE user_id = ? AND product_id = ? AND variation_id = ? AND addon_key = ?",
                    customerId, row.productId(), row.variationId(), row.addonKey());
                continue;
            }
            long unit = row.priceCents() + row.variationDelta() + addonService.priceCents(addons);
            items.add(new CartItem(row.productId(), row.variationId(), row.quantity(), row.restaurantId(), row.name(), row.priceCents(),
                row.variationName(), unit, addonIds, addons.stream().map(addon -> (String) addon.get("name")).toList()));
        }
        StringBuilder canonical = new StringBuilder();
        for (CartItem item : items) {
            canonical.append(item.productId()).append(':').append(item.variationId()).append(':')
                .append(item.addonIds().stream().map(String::valueOf).reduce((a, b) -> a + "." + b).orElse("")).append(':')
                .append(item.quantity()).append(':').append(item.unitPriceCents()).append(';');
        }
        return new CartSnapshot(items, Tokens.hash(canonical.toString()).substring(0, 16));
    }

    private record CartRow(long productId, long variationId, String addonKey, int quantity, long restaurantId, String name, int priceCents, String variationName, int variationDelta) {}
    public record CartItem(long productId, long variationId, int quantity, long restaurantId, String name, int priceCents, String variationName, long unitPriceCents, List<Long> addonIds, List<String> addonNames) {}
    public record CartSnapshot(List<CartItem> items, String version) {}
}
