package com.foodie.api.restaurant;

import com.foodie.api.ApiException;
import com.foodie.api.admin.ModuleAccessService;
import com.foodie.api.admin.DashboardService;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.permissions.PermissionService;
import com.foodie.api.permissions.Permissions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Financeiro e relatórios do negócio do lojista (Fatia A). */
@RestController
@RequestMapping("/restaurant/finance")
public class RestaurantFinanceController {
    private static final String COMPLETED = "('delivered','completed','served')";

    private final AuthService auth;
    private final PermissionService permissions;
    private final ModuleAccessService modules;
    private final JdbcTemplate jdbc;

    public RestaurantFinanceController(AuthService auth, PermissionService permissions, ModuleAccessService modules, JdbcTemplate jdbc) {
        this.auth = auth;
        this.permissions = permissions;
        this.modules = modules;
        this.jdbc = jdbc;
    }

    private record Range(String from, String to) {}

    private static Range range(String from, String to) {
        DashboardService.DateRange resolved = DashboardService.resolveRange(from, to);
        return new Range(resolved.from().toString(), resolved.to().toString());
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(@CookieValue(value = "foodie_session", required = false) String token,
                                       @RequestParam(required = false) String from,
                                       @RequestParam(required = false) String to) {
        User user = manager(token, Permissions.REPORTS_VIEW);
        long restaurantId = user.restaurantId();
        Range r = range(from, to);
        Map<String, Object> orders = jdbc.queryForMap(
            "SELECT COUNT(*) AS count, COALESCE(SUM(GREATEST(0, o.total_cents - o.delivery_fee_cents - COALESCE(o.tip_cents,0))),0) AS revenue "
                + "FROM orders o JOIN order_payments p ON p.order_id = o.id "
                + "WHERE o.restaurant_id = ? AND o.status IN " + COMPLETED + " AND p.status = 'paid' "
                + "AND o.created_at >= ? AND o.created_at < DATE_ADD(?, INTERVAL 1 DAY)",
            restaurantId, r.from(), r.to());
        long count = ((Number) orders.get("count")).longValue();
        long revenue = ((Number) orders.get("revenue")).longValue();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", r.from());
        result.put("to", r.to());
        result.put("salesCents", revenue);
        result.put("orders", count);
        result.put("revenueCents", revenue);
        result.put("averageTicketCents", count == 0 ? 0 : revenue / count);
        return result;
    }

    @GetMapping("/earnings")
    public Map<String, Object> earnings(@CookieValue(value = "foodie_session", required = false) String token,
                                        @RequestParam(required = false) String groupBy,
                                        @RequestParam(required = false) String from,
                                        @RequestParam(required = false) String to) {
        User user = manager(token, Permissions.REPORTS_VIEW);
        String format = switch (groupBy == null || groupBy.isBlank() ? "day" : groupBy.strip().toLowerCase(Locale.ROOT)) {
            case "day" -> "%Y-%m-%d";
            case "week" -> "%x-W%v";
            case "month" -> "%Y-%m";
            default -> throw new ApiException(400, "Agrupamento inválido");
        };
        Range r = range(from, to);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT DATE_FORMAT(o.created_at, ?) AS period, "
                + "SUM(GREATEST(0, o.total_cents - o.delivery_fee_cents - COALESCE(o.tip_cents,0))) AS saleCents, COUNT(*) AS orders "
                + "FROM orders o JOIN order_payments p ON p.order_id = o.id "
                + "WHERE o.restaurant_id = ? AND o.status IN " + COMPLETED + " AND p.status = 'paid' "
                + "AND o.created_at >= ? AND o.created_at < DATE_ADD(?, INTERVAL 1 DAY) "
                + "GROUP BY period ORDER BY period",
            format, user.restaurantId(), r.from(), r.to());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", r.from());
        result.put("to", r.to());
        result.put("buckets", rows);
        return result;
    }

    @GetMapping("/reports/orders")
    public List<Map<String, Object>> ordersReport(@CookieValue(value = "foodie_session", required = false) String token,
                                                  @RequestParam(required = false) String from,
                                                  @RequestParam(required = false) String to) {
        User user = manager(token, Permissions.REPORTS_VIEW);
        Range r = range(from, to);
        return jdbc.queryForList(
            "SELECT status, COUNT(*) AS count, COALESCE(SUM(total_cents),0) AS total_cents FROM orders "
                + "WHERE restaurant_id = ? AND created_at >= ? AND created_at < DATE_ADD(?, INTERVAL 1 DAY) GROUP BY status ORDER BY count DESC",
            user.restaurantId(), r.from(), r.to());
    }

    @GetMapping("/reports/products")
    public List<Map<String, Object>> productsReport(@CookieValue(value = "foodie_session", required = false) String token,
                                                    @RequestParam(required = false) String from,
                                                    @RequestParam(required = false) String to,
                                                    @RequestParam(required = false) Integer limit) {
        User user = manager(token, Permissions.REPORTS_VIEW);
        Range r = range(from, to);
        return jdbc.queryForList(
            "SELECT p.id, p.name, SUM(oi.quantity) AS quantity, SUM(oi.quantity * oi.unit_price_cents) AS revenue_cents "
                + "FROM order_items oi JOIN orders o ON o.id = oi.order_id JOIN products p ON p.id = oi.product_id "
                + "WHERE o.restaurant_id = ? AND o.created_at >= ? AND o.created_at < DATE_ADD(?, INTERVAL 1 DAY) "
                + "GROUP BY p.id, p.name ORDER BY revenue_cents DESC LIMIT ?",
            user.restaurantId(), r.from(), r.to(), limit == null ? 50 : Math.min(limit, 200));
    }

    @GetMapping("/reports/daily")
    public List<Map<String, Object>> dailyReport(@CookieValue(value = "foodie_session", required = false) String token,
                                                 @RequestParam(required = false) String from,
                                                 @RequestParam(required = false) String to) {
        User user = manager(token, Permissions.REPORTS_VIEW);
        Range r = range(from, to);
        return jdbc.queryForList(
            "SELECT DATE(created_at) AS day, COUNT(*) AS orders, COALESCE(SUM(CASE WHEN status IN " + COMPLETED + " THEN total_cents ELSE 0 END),0) AS revenue_cents "
                + "FROM orders WHERE restaurant_id = ? AND created_at >= ? AND created_at < DATE_ADD(?, INTERVAL 1 DAY) GROUP BY DATE(created_at) ORDER BY day DESC",
            user.restaurantId(), r.from(), r.to());
    }

    @GetMapping("/expenses")
    public Map<String, Object> expenseList(@CookieValue(value = "foodie_session", required = false) String token,
                                           @RequestParam(required = false) String from,
                                           @RequestParam(required = false) String to) {
        User user = manager(token, Permissions.PAYMENTS_MANAGE);
        Range r = range(from, to);
        List<Map<String, Object>> items = jdbc.queryForList(
            "SELECT id, category, description, amount_cents, incurred_at, created_at FROM expenses "
                + "WHERE restaurant_id = ? AND incurred_at >= ? AND incurred_at <= ? ORDER BY incurred_at DESC, id DESC LIMIT 300",
            user.restaurantId(), r.from(), r.to());
        Long total = jdbc.queryForObject(
            "SELECT COALESCE(SUM(amount_cents),0) FROM expenses WHERE restaurant_id = ? AND incurred_at >= ? AND incurred_at <= ?",
            Long.class, user.restaurantId(), r.from(), r.to());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("totalCents", total == null ? 0L : total);
        return result;
    }

    @PostMapping("/expenses")
    public ResponseEntity<Map<String, Object>> createExpense(@CookieValue(value = "foodie_session", required = false) String token,
                                                             @Valid @RequestBody ExpenseRequest body) {
        User user = manager(token, Permissions.PAYMENTS_MANAGE);
        jdbc.update("INSERT INTO expenses (restaurant_id, category, description, amount_cents, incurred_at, created_by) VALUES (?, ?, ?, ?, ?, ?)",
            user.restaurantId(), body.category().strip(), body.description() == null ? "" : body.description().strip(), body.amountCents(), body.incurredAt(), user.id());
        return ResponseEntity.status(201).body(Map.of("ok", true));
    }

    @DeleteMapping("/expenses/{id}")
    public Map<String, Boolean> deleteExpense(@CookieValue(value = "foodie_session", required = false) String token,
                                              @PathVariable @Positive long id) {
        User user = manager(token, Permissions.PAYMENTS_MANAGE);
        if (jdbc.update("DELETE FROM expenses WHERE id = ? AND restaurant_id = ?", id, user.restaurantId()) == 0) {
            throw new ApiException(404, "Despesa não encontrada");
        }
        return Map.of("ok", true);
    }

    private User manager(String token, String permission) {
        User user = auth.requireUser(token, "restaurant", "kitchen");
        if (user.restaurantId() == null) throw new ApiException(403, "Acesso não autorizado");
        permissions.require(user, permission);
        modules.require(user.restaurantId(), "finance");
        return user;
    }

    private static long number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? 0L : ((Number) value).longValue();
    }

    public record ExpenseRequest(@NotBlank @Size(min = 2, max = 60) String category,
                                 @Size(max = 255) String description,
                                 @Positive long amountCents,
                                 @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String incurredAt) {}
}
