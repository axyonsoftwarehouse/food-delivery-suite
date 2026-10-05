package com.foodie.api.orders;

import com.foodie.api.ApiException;
import com.foodie.api.auth.User;
import com.foodie.api.catalog.AddonService;
import com.foodie.api.catalog.PostalCoverageService;
import com.foodie.api.finance.LedgerService;
import com.foodie.api.hours.RestaurantHoursService;
import com.foodie.api.notifications.NotificationService;
import com.foodie.api.rewards.RewardsService;
import com.foodie.api.routing.DeliveryService;
import com.foodie.api.support.SupportActionService;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final DeliveryService delivery;
    private final NotificationService notifications;
    private final AddonService addonService;
    private final CouponService couponService;
    private final CampaignService campaignService;
    private final LedgerService ledger;
    private final RewardsService rewards;
    private final SupportActionService support;

    public OrderService(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc, PostalCoverageService postalCoverage, RestaurantHoursService hours, PaymentService payments, DeliveryService delivery, NotificationService notifications, AddonService addonService, CouponService couponService, CampaignService campaignService, LedgerService ledger, RewardsService rewards, SupportActionService support) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
        this.postalCoverage = postalCoverage;
        this.hours = hours;
        this.payments = payments;
        this.delivery = delivery;
        this.notifications = notifications;
        this.addonService = addonService;
        this.couponService = couponService;
        this.campaignService = campaignService;
        this.ledger = ledger;
        this.rewards = rewards;
        this.support = support;
    }

    @Transactional
    public Map<String, Object> create(User customer, OrderController.OrderRequest request) {
        String orderType = request.orderType() == null || request.orderType().isBlank() ? "delivery" : request.orderType();
        if (!Set.of("delivery", "take_away", "dine_in").contains(orderType)) throw new ApiException(400, "Tipo de pedido inválido");
        boolean deliveryOrder = "delivery".equals(orderType);
        Set<Long> distinctIds = new HashSet<>();
        Set<String> distinctLines = new HashSet<>();
        for (var item : request.items()) {
            long variationId = item.variationId() == null ? 0 : item.variationId();
            String addonKey = AddonService.key(item.addonIds());
            if (!distinctLines.add(item.productId() + ":" + variationId + ":" + addonKey)) throw new ApiException(400, "Item repetido no pedido");
            distinctIds.add(item.productId());
        }
        Map<String, Object> address = null;
        long zoneId;
        List<Map<String, Object>> restaurants;
        if (deliveryOrder) {
            List<Map<String, Object>> addresses = jdbc.queryForList(
                "SELECT a.id, a.zone_id, a.postal_code, a.street, a.number, a.neighborhood, a.complement, a.latitude, a.longitude, z.city, z.state, z.delivery_fee_cents, z.base_fee_cents, z.per_km_cents, z.minimum_order_cents FROM addresses a JOIN zones z ON z.id = a.zone_id AND z.active = TRUE WHERE a.id = ? AND a.user_id = ?",
                request.addressId(), customer.id()
            );
            if (addresses.isEmpty()) throw new ApiException(400, "Endereço não encontrado ou zona indisponível");
            address = addresses.getFirst();
            zoneId = number(address, "zone_id");
            if (address.get("postal_code") == null) throw new ApiException(409, "Recadastre o endereço com CEP antes de pedir");
            postalCoverage.requireAddressZone((String) address.get("postal_code"), zoneId);
            restaurants = jdbc.queryForList(
                "SELECT r.timezone, r.latitude, r.longitude, r.service_fee_percent FROM restaurants r JOIN restaurant_zones rz ON rz.restaurant_id = r.id WHERE r.id = ? AND r.active = TRUE AND rz.zone_id = ? FOR UPDATE",
                request.restaurantId(), zoneId
            );
            if (restaurants.isEmpty()) throw new ApiException(400, "Restaurante não atende este endereço ou está fechado");
        } else {
            zoneId = 0;
            restaurants = jdbc.queryForList(
                "SELECT r.timezone, r.latitude, r.longitude, r.service_fee_percent FROM restaurants r WHERE r.id = ? AND r.active = TRUE FOR UPDATE",
                request.restaurantId()
            );
            if (restaurants.isEmpty()) throw new ApiException(400, "Restaurante indisponível");
        }
        String timezone = (String) restaurants.getFirst().get("timezone");
        double serviceFeePercent = "dine_in".equals(orderType) ? ((Number) restaurants.getFirst().get("service_fee_percent")).doubleValue() : 0;
        LocalDateTime scheduledAt = null;
        if (request.scheduledFor() != null && !request.scheduledFor().isBlank()) {
            try {
                scheduledAt = LocalDateTime.parse(request.scheduledFor().trim());
            } catch (java.time.format.DateTimeParseException error) {
                throw new ApiException(400, "Data de agendamento inválida");
            }
            LocalDateTime now = LocalDateTime.now();
            if (!scheduledAt.isAfter(now.plusMinutes(15))) throw new ApiException(400, "Agende com pelo menos 15 minutos de antecedência");
            if (scheduledAt.isAfter(now.plusDays(7))) throw new ApiException(400, "O agendamento é limitado a 7 dias");
            hours.requireOpenAt(request.restaurantId(), timezone, scheduledAt);
        } else if (!hours.isOpen(request.restaurantId(), timezone)) {
            throw new ApiException(409, "Restaurante está fora do horário de funcionamento");
        }
        List<Map<String, Object>> rows = namedJdbc.queryForList(
            "SELECT id, name, price_cents, stock FROM products WHERE restaurant_id = :restaurantId AND available = TRUE AND id IN (:ids) "
                + "AND (available_from IS NULL OR available_until IS NULL OR CURTIME() BETWEEN available_from AND available_until) FOR UPDATE",
            new MapSqlParameterSource("restaurantId", request.restaurantId()).addValue("ids", distinctIds)
        );
        if (rows.size() != distinctIds.size()) throw new ApiException(400, "Há produtos indisponíveis");
        Map<Long, Map<String, Object>> products = new HashMap<>();
        Map<Long, Integer> needed = new HashMap<>();
        for (Map<String, Object> row : rows) products.put(number(row, "id"), row);
        for (var item : request.items()) needed.merge(item.productId(), item.quantity(), Integer::sum);
        for (Map.Entry<Long, Integer> entry : needed.entrySet()) {
            Object stock = products.get(entry.getKey()).get("stock");
            if (stock != null && ((Number) stock).intValue() < entry.getValue()) {
                throw new ApiException(409, "Estoque insuficiente para " + products.get(entry.getKey()).get("name"));
            }
        }
        Set<Long> variationIds = new HashSet<>();
        for (var item : request.items()) if (item.variationId() != null) variationIds.add(item.variationId());
        Map<Long, Map<String, Object>> variations = new HashMap<>();
        if (!variationIds.isEmpty()) {
            List<Map<String, Object>> variationRows = namedJdbc.queryForList(
                "SELECT v.id, v.product_id, v.name, v.price_delta_cents FROM product_variations v WHERE v.available = TRUE AND v.id IN (:ids)",
                new MapSqlParameterSource("ids", variationIds)
            );
            for (Map<String, Object> row : variationRows) variations.put(number(row, "id"), row);
            if (variations.size() != variationIds.size()) throw new ApiException(400, "Há variações indisponíveis");
        }
        Map<String, Long> unitPrices = new HashMap<>();
        Map<String, List<Map<String, Object>>> addonsByLine = new HashMap<>();
        long subtotal = 0;
        try {
            for (var item : request.items()) {
                long variationId = item.variationId() == null ? 0 : item.variationId();
                String addonKey = AddonService.key(item.addonIds());
                String lineKey = item.productId() + ":" + variationId + ":" + addonKey;
                long unit = number(products.get(item.productId()), "price_cents");
                if (variationId != 0) {
                    Map<String, Object> variation = variations.get(variationId);
                    if (variation == null || number(variation, "product_id") != item.productId()) {
                        throw new ApiException(400, "Variação não pertence ao produto");
                    }
                    unit = Math.addExact(unit, number(variation, "price_delta_cents"));
                }
                List<Map<String, Object>> addons = addonService.validate(item.productId(), variationId, item.addonIds());
                unit = Math.addExact(unit, addonService.priceCents(addons));
                addonsByLine.put(lineKey, addons);
                unitPrices.put(lineKey, unit);
                subtotal = Math.addExact(subtotal, Math.multiplyExact(unit, item.quantity()));
            }
        } catch (ArithmeticException error) {
            throw new ApiException(400, "Valor do pedido inválido");
        }
        DeliveryService.Estimate estimate = deliveryOrder ? delivery.estimate(address, restaurants.getFirst(), address) : null;
        long fee = estimate == null ? 0 : estimate.feeCents();
        CouponService.Applied coupon = null;
        if (request.couponCode() != null && !request.couponCode().isBlank()) {
            coupon = couponService.validate(request.couponCode(), request.restaurantId(), subtotal);
        }
        CampaignService.Applied campaign = campaignService.best(request.restaurantId(), request.items().stream()
            .map(item -> new CampaignService.Line(item.productId(),
                unitPrices.get(item.productId() + ":" + (item.variationId() == null ? 0 : item.variationId()) + ":" + AddonService.key(item.addonIds())) * item.quantity()))
            .toList());
        long campaignDiscount = campaign == null ? 0 : campaign.discountCents();
        long discount = Math.min(subtotal, campaignDiscount + (coupon == null ? 0 : coupon.discountCents()));
        long minimum = deliveryOrder ? number(address, "minimum_order_cents") : 0;
        long total = Math.max(0, OrderWorkflow.total(subtotal, fee, minimum) - discount);
        long serviceFee = serviceFeePercent <= 0 ? 0 : Math.round(total * serviceFeePercent / 100.0);
        long tip = request.tipCents() == null ? 0 : Math.max(0, Math.min(request.tipCents(), 100_000));
        long finalTotal = total + serviceFee;
        long payable = finalTotal + tip;
        long finalSubtotal = subtotal;
        Long distanceMeters = estimate == null ? null : estimate.distanceMeters();
        Long durationSeconds = estimate == null ? null : estimate.durationSeconds();
        String addressText;
        if (deliveryOrder) {
            String complement = (String) address.get("complement");
            addressText = address.get("street") + ", " + address.get("number")
                + (complement == null || complement.isEmpty() ? "" : ", " + complement)
                + " • " + address.get("neighborhood") + " • " + address.get("city") + "/" + address.get("state") + " • CEP " + address.get("postal_code");
        } else {
            addressText = "dine_in".equals(orderType) ? "Consumo no local" : "Retirada no local";
        }

        final Long tableIdForOrder;
        final Integer partySizeForOrder;
        final Long sessionIdForOrder;
        if ("dine_in".equals(orderType)) {
            if (request.tableId() == null) throw new ApiException(400, "Selecione a mesa para consumo no local");
            List<Map<String, Object>> tables = jdbc.queryForList(
                "SELECT id, capacity FROM restaurant_tables WHERE id = ? AND restaurant_id = ? AND active = TRUE",
                request.tableId(), request.restaurantId()
            );
            if (tables.isEmpty()) throw new ApiException(400, "Mesa indisponível");
            int capacity = ((Number) tables.getFirst().get("capacity")).intValue();
            int party = request.partySize() == null ? 1 : request.partySize();
            if (party < 1 || party > capacity) throw new ApiException(400, "Número de pessoas acima da capacidade da mesa");
            tableIdForOrder = request.tableId();
            partySizeForOrder = party;
            List<Long> sessions = jdbc.query(
                "SELECT id FROM table_sessions WHERE table_id = ? AND restaurant_id = ? AND status = 'open' ORDER BY id DESC LIMIT 1 FOR UPDATE",
                (rs, row) -> rs.getLong(1), request.tableId(), request.restaurantId()
            );
            if (sessions.isEmpty()) {
                GeneratedKeyHolder sessionKey = new GeneratedKeyHolder();
                jdbc.update(connection -> {
                    PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO table_sessions (restaurant_id, table_id) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS);
                    statement.setLong(1, request.restaurantId());
                    statement.setLong(2, request.tableId());
                    return statement;
                }, sessionKey);
                sessionIdForOrder = sessionKey.getKey().longValue();
            } else {
                sessionIdForOrder = sessions.getFirst();
            }
        } else {
            tableIdForOrder = null;
            partySizeForOrder = null;
            sessionIdForOrder = null;
        }
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        final CouponService.Applied appliedCoupon = coupon;
        final LocalDateTime orderScheduledAt = scheduledAt;
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO orders (customer_id, restaurant_id, zone_id, address_id, delivery_address_text, subtotal_cents, delivery_fee_cents, discount_cents, coupon_code, total_cents, distance_meters, duration_seconds, scheduled_at, order_type, table_id, party_size, service_fee_cents, table_session_id, tip_cents) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, customer.id());
            statement.setLong(2, request.restaurantId());
            if (deliveryOrder) statement.setLong(3, zoneId); else statement.setNull(3, java.sql.Types.BIGINT);
            if (deliveryOrder) statement.setLong(4, request.addressId()); else statement.setNull(4, java.sql.Types.BIGINT);
            statement.setString(5, addressText);
            statement.setLong(6, finalSubtotal);
            statement.setLong(7, fee);
            statement.setLong(8, discount);
            if (appliedCoupon == null) statement.setNull(9, java.sql.Types.VARCHAR); else statement.setString(9, appliedCoupon.code());
            statement.setLong(10, payable);
            if (distanceMeters == null) statement.setNull(11, java.sql.Types.INTEGER); else statement.setLong(11, distanceMeters);
            if (durationSeconds == null) statement.setNull(12, java.sql.Types.INTEGER); else statement.setLong(12, durationSeconds);
            if (orderScheduledAt == null) statement.setNull(13, java.sql.Types.TIMESTAMP); else statement.setObject(13, orderScheduledAt);
            statement.setString(14, orderType);
            if (tableIdForOrder == null) statement.setNull(15, java.sql.Types.BIGINT); else statement.setLong(15, tableIdForOrder);
            if (partySizeForOrder == null) statement.setNull(16, java.sql.Types.INTEGER); else statement.setInt(16, partySizeForOrder);
            statement.setLong(17, serviceFee);
            if (sessionIdForOrder == null) statement.setNull(18, java.sql.Types.BIGINT); else statement.setLong(18, sessionIdForOrder);
            statement.setLong(19, tip);
            return statement;
        }, key);
        long orderId = key.getKey().longValue();
        if (campaign != null) jdbc.update("UPDATE orders SET campaign_id = ?, campaign_name = ?, campaign_discount_cents = ? WHERE id = ?",
            campaign.campaignId(), campaign.name(), campaignDiscount, orderId);
        for (var item : request.items()) {
            long variationId = item.variationId() == null ? 0 : item.variationId();
            String lineKey = item.productId() + ":" + variationId + ":" + AddonService.key(item.addonIds());
            Map<String, Object> product = products.get(item.productId());
            String variationName = variationId == 0 ? null : (String) variations.get(variationId).get("name");
            GeneratedKeyHolder itemKey = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO order_items (order_id, product_id, variation_id, name, variation_name, quantity, unit_price_cents) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
                );
                statement.setLong(1, orderId);
                statement.setLong(2, item.productId());
                if (variationId == 0) statement.setNull(3, java.sql.Types.BIGINT); else statement.setLong(3, variationId);
                statement.setString(4, (String) product.get("name"));
                if (variationName == null) statement.setNull(5, java.sql.Types.VARCHAR); else statement.setString(5, variationName);
                statement.setInt(6, item.quantity());
                statement.setLong(7, unitPrices.get(lineKey));
                return statement;
            }, itemKey);
            long orderItemId = itemKey.getKey().longValue();
            for (Map<String, Object> addon : addonsByLine.getOrDefault(lineKey, List.of())) {
                jdbc.update("INSERT INTO order_item_addons (order_item_id, addon_id, name, price_cents) VALUES (?, ?, ?, ?)",
                    orderItemId, ((Number) addon.get("id")).longValue(), (String) addon.get("name"), ((Number) addon.get("price_cents")).longValue());
            }
            if (product.get("stock") != null) {
                jdbc.update("UPDATE products SET stock = stock - ? WHERE id = ?", item.quantity(), item.productId());
            }
        }
        jdbc.update("INSERT INTO order_events (order_id, actor_id, from_status, to_status) VALUES (?, ?, NULL, ?)", orderId, customer.id(), "placed");
        payments.create(orderId, request.paymentMethod(), request.modality(), payable, request.changeForCents());
        notifications.notifyRestaurant(request.restaurantId(), "order_placed", "Novo pedido #" + orderId, "Aguardando aceite do restaurante.", orderId);
        if (coupon != null) couponService.consume(coupon.couponId());
        Map<String, Object> created = new LinkedHashMap<>();
        created.put("id", orderId);
        created.put("status", "placed");
        created.put("orderType", orderType);
        created.put("subtotalCents", subtotal);
        created.put("deliveryFeeCents", fee);
        created.put("discountCents", discount);
        if (campaign != null) {
            created.put("campaignId", campaign.campaignId());
            created.put("campaignName", campaign.name());
            created.put("campaignDiscountCents", campaignDiscount);
        }
        if (coupon != null)         created.put("couponCode", coupon.code());
        created.put("totalCents", payable);
        if (serviceFee > 0) created.put("serviceFeeCents", serviceFee);
        if (tip > 0) created.put("tipCents", tip);
        if (sessionIdForOrder != null) created.put("tableSessionId", sessionIdForOrder);
        if (orderScheduledAt != null) created.put("scheduledAt", orderScheduledAt.toString());
        created.put("address", addressText);
        created.put("paymentMethod", request.paymentMethod());
        created.put("distanceMeters", distanceMeters);
        created.put("durationSeconds", durationSeconds);
        // Retirada e consumo no local não têm estimativa de entrega (`estimate` é nulo nessas
        // modalidades): sem esta guarda o checkout dessas duas modalidades respondia 500.
        if (estimate != null) created.put("feeMode", estimate.feeMode());
        return created;
    }

    private static final String ORDER_BASE = "SELECT o.id, o.status, o.order_type, o.table_id, t.number AS table_number, o.party_size, o.subtotal_cents, o.delivery_fee_cents, o.discount_cents, o.total_cents, o.delivery_address_text, o.restaurant_id, o.courier_id, o.scheduled_at, o.created_at, r.name AS restaurant_name, p.method AS payment_method, p.status AS payment_status, p.amount_due_cents AS payment_due_cents FROM orders o JOIN restaurants r ON r.id = o.restaurant_id LEFT JOIN order_payments p ON p.order_id = o.id LEFT JOIN restaurant_tables t ON t.id = o.table_id ";
    private static final String TERMINAL = "'delivered','rejected','cancelled','expired','failed','completed'";
    private static final int ACTIVE_LIMIT = 200;
    private static final int HISTORY_LIMIT = 100;

    public List<Map<String, Object>> list(User user) {
        expireStale();
        Scope scope = scope(user);
        List<Map<String, Object>> active = jdbc.queryForList(ORDER_BASE + "WHERE " + scope.where() + " AND o.status NOT IN (" + TERMINAL + ") ORDER BY o.id DESC LIMIT " + ACTIVE_LIMIT, scope.args().toArray());
        List<Map<String, Object>> terminal = jdbc.queryForList(ORDER_BASE + "WHERE " + scope.where() + " AND o.status IN (" + TERMINAL + ") ORDER BY o.id DESC LIMIT " + HISTORY_LIMIT, scope.args().toArray());
        List<Map<String, Object>> merged = new ArrayList<>(active);
        merged.addAll(terminal);
        merged.sort((a, b) -> Long.compare(((Number) b.get("id")).longValue(), ((Number) a.get("id")).longValue()));
        return merged;
    }

    public Map<String, Object> history(User user, String status, Long after, int limit) {
        expireStale();
        Scope scope = scope(user);
        StringBuilder sql = new StringBuilder(ORDER_BASE + "WHERE " + scope.where() + " AND o.status IN (" + TERMINAL + ")");
        List<Object> args = new ArrayList<>(scope.args());
        if (status != null && !status.isBlank()) {
            if (!TERMINAL.contains("'" + status + "'")) throw new ApiException(400, "Filtro de status inválido");
            sql.append(" AND o.status = ?");
            args.add(status);
        }
        if (after != null) {
            sql.append(" AND o.id < ?");
            args.add(after);
        }
        sql.append(" ORDER BY o.id DESC LIMIT ").append(limit + 1);
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        boolean hasMore = rows.size() > limit;
        List<Map<String, Object>> items = hasMore ? rows.subList(0, limit) : rows;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("nextCursor", hasMore ? ((Number) items.getLast().get("id")).longValue() : null);
        return result;
    }

    private record Scope(String where, List<Object> args) {}

    private static Scope scope(User user) {
        return switch (user.role()) {
            case "customer" -> new Scope("o.customer_id = ?", List.of(user.id()));
            case "restaurant", "kitchen" -> new Scope("o.restaurant_id = ?", List.of(user.restaurantId() == null ? -1L : user.restaurantId()));
            case "courier" -> new Scope("o.courier_id = ?", List.of(user.id()));
            default -> new Scope("1 = 1", List.of());
        };
    }

    public Map<String, Object> detail(User user, long orderId) {
        expireStale();
        List<Map<String, Object>> orders = jdbc.queryForList(
            "SELECT o.*, r.name AS restaurant_name FROM orders o JOIN restaurants r ON r.id = o.restaurant_id WHERE o.id = ?", orderId);
        if (orders.isEmpty()) throw new ApiException(404, "Pedido não encontrado");
        Map<String, Object> order = orders.getFirst();
        checkAccess(user, order);
        Map<String, Object> result = new LinkedHashMap<>(order);
        result.put("items", jdbc.queryForList(
            "SELECT oi.id, oi.name, oi.variation_name, oi.quantity, oi.unit_price_cents, "
                + "(SELECT GROUP_CONCAT(oia.name SEPARATOR ', ') FROM order_item_addons oia WHERE oia.order_item_id = oi.id) AS addons "
                + "FROM order_items oi WHERE oi.order_id = ?", orderId));
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
        if ("admin".equals(user.role()) && Set.of("cancel", "assign", "unassign").contains(action)) {
            support.recordOrderAction(user, number(order, "restaurant_id"), orderId, action, trimmed);
        }
        notifyTransition(order, orderId, next, courierId);
        ledger.postOrder(orderId);
        rewards.onOrderCompleted(orderId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", orderId);
        result.put("status", next);
        if ("assign".equals(action)) result.put("courierId", courierId);
        return result;
    }

    private void notifyTransition(Map<String, Object> order, long orderId, String next, Long courierId) {
        long customerId = number(order, "customer_id");
        switch (next) {
            case "accepted" -> notifications.notifyUser(customerId, "order_accepted", "Pedido #" + orderId + " aceito", "O restaurante aceitou seu pedido.", orderId);
            case "ready" -> notifications.notifyUser(customerId, "order_ready", "Pedido #" + orderId + " pronto", "Seu pedido está pronto para entrega.", orderId);
            case "assigned" -> {
                if (courierId != null) notifications.notifyUser(courierId, "order_assigned", "Nova entrega #" + orderId, "Um pedido foi atribuído a você.", orderId);
            }
            case "picked_up" -> notifications.notifyUser(customerId, "order_picked_up", "Pedido #" + orderId + " saiu para entrega", "O entregador está a caminho.", orderId);
            case "delivered" -> notifications.notifyUser(customerId, "order_delivered", "Pedido #" + orderId + " entregue", "Bom apetite!", orderId);
            default -> { }
        }
    }

    private void expireStale() {        List<Long> stale = jdbc.query("SELECT id FROM orders WHERE status = 'placed' AND created_at < (NOW() - INTERVAL 15 MINUTE) AND (scheduled_at IS NULL OR scheduled_at <= NOW())", (rs, row) -> rs.getLong(1));
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
            case "restaurant", "kitchen" -> user.restaurantId() == null || number(order, "restaurant_id") != user.restaurantId();
            case "courier" -> order.get("courier_id") == null || number(order, "courier_id") != user.id();
            default -> false;
        };
        if (denied) throw new ApiException(403, "Acesso não autorizado");
    }

    private static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }
}
