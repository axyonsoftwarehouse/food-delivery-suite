package com.foodie.api.admin;

import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Painel "Saúde das lojas" (Fatia D). Visão de observação: operação via Foodie, GMV, assinatura e
 * alertas — sem expor custos, estoque, despesas ou clientes da loja.
 */
@RestController
public class TenantHealthController {
    private static final String COMPLETED = "('delivered','completed','served')";
    private static final String CANCELED = "('cancelled','rejected','expired','failed')";

    private final AuthService auth;
    private final AdminPermissionService permissions;
    private final JdbcTemplate jdbc;

    public TenantHealthController(AuthService auth, AdminPermissionService permissions, JdbcTemplate jdbc) {
        this.auth = auth;
        this.permissions = permissions;
        this.jdbc = jdbc;
    }

    @GetMapping("/admin/tenants/health")
    public Map<String, Object> health(@CookieValue(value = "foodie_session", required = false) String token,
                                      @RequestParam(required = false) String from,
                                      @RequestParam(required = false) String to) {
        User user = auth.requireUser(token, "admin");
        permissions.require(user, AdminPermissions.REPORTS_VIEW);
        DashboardService.DateRange range = DashboardService.resolveRange(from, to);
        String f = range.from().toString();
        String t = range.to().toString();

        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT r.id, r.name, r.active, r.approval, "
                + "(SELECT COUNT(*) FROM orders o WHERE o.restaurant_id = r.id AND o.created_at >= ? AND o.created_at < DATE_ADD(?, INTERVAL 1 DAY)) AS orders, "
                + "(SELECT COUNT(*) FROM orders o WHERE o.restaurant_id = r.id AND o.status IN " + COMPLETED + " AND o.created_at >= ? AND o.created_at < DATE_ADD(?, INTERVAL 1 DAY)) AS completed, "
                + "(SELECT COALESCE(SUM(CASE WHEN o.status IN " + COMPLETED + " THEN o.total_cents ELSE 0 END),0) FROM orders o WHERE o.restaurant_id = r.id AND o.created_at >= ? AND o.created_at < DATE_ADD(?, INTERVAL 1 DAY)) AS gmv, "
                + "(SELECT COUNT(*) FROM orders o WHERE o.restaurant_id = r.id AND o.status IN " + CANCELED + " AND o.created_at >= ? AND o.created_at < DATE_ADD(?, INTERVAL 1 DAY)) AS canceled, "
                + "(SELECT MAX(o.created_at) FROM orders o WHERE o.restaurant_id = r.id) AS last_order_at, "
                + "(SELECT status FROM restaurant_subscriptions rs WHERE rs.restaurant_id = r.id ORDER BY rs.id DESC LIMIT 1) AS subscription_status "
                + "FROM restaurants r ORDER BY r.name",
            f, t, f, t, f, t, f, t);

        List<Map<String, Object>> entries = new ArrayList<>();
        long totalGmv = 0;
        long totalOrders = 0;
        for (Map<String, Object> row : rows) {
            long orders = number(row, "orders");
            long completed = number(row, "completed");
            long canceled = number(row, "canceled");
            long gmv = number(row, "gmv");
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", number(row, "id"));
            entry.put("name", row.get("name"));
            entry.put("active", Boolean.TRUE.equals(row.get("active")));
            entry.put("approval", row.get("approval"));
            entry.put("orders", orders);
            entry.put("gmvCents", gmv);
            entry.put("averageTicketCents", completed == 0 ? 0L : gmv / completed);
            entry.put("cancelRatePercent", orders == 0 ? 0 : Math.round(canceled * 1000.0 / orders) / 10.0);
            entry.put("lastOrderAt", row.get("last_order_at"));
            entry.put("subscriptionStatus", row.get("subscription_status"));
            entry.put("alert", alert(row.get("approval"), orders, canceled, row.get("last_order_at")));
            entries.add(entry);
            totalGmv += gmv;
            totalOrders += orders;
        }
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("restaurants", entries.size());
        totals.put("orders", totalOrders);
        totals.put("gmvCents", totalGmv);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", f);
        result.put("to", t);
        result.put("totals", totals);
        result.put("tenants", entries);
        return result;
    }

    private static String alert(Object approval, long orders, long canceled, Object lastOrderAt) {
        if (approval != null && !"approved".equals(String.valueOf(approval))) return "Cadastro " + approval;
        if (lastOrderAt == null) return "Sem pedidos ainda";
        if (orders == 0) return "Sem pedidos no período";
        if (orders > 0 && canceled * 100.0 / orders >= 30) return "Muitos cancelamentos";
        return null;
    }

    private static long number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? 0L : ((Number) value).longValue();
    }
}
