package com.foodie.api.admin;

import com.foodie.api.ApiException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Relatórios de ganhos e operacionais do admin (E05/E06). */
@Service
public class ReportsService {
    private static final String COMPLETED = "('delivered','completed','served')";

    private final JdbcTemplate jdbc;

    public ReportsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private record Range(String from, String to) {}

    private static Range range(String from, String to) {
        DashboardService.DateRange resolved = DashboardService.resolveRange(from, to);
        return new Range(resolved.from().toString(), resolved.to().toString());
    }

    public Map<String, Object> earnings(String scope, String groupBy, String from, String to) {
        String party = party(scope);
        String format = switch (groupBy == null || groupBy.isBlank() ? "day" : groupBy.strip().toLowerCase(Locale.ROOT)) {
            case "day" -> "%Y-%m-%d";
            case "week" -> "%x-W%v";
            case "month" -> "%Y-%m";
            default -> throw new ApiException(400, "Agrupamento inválido");
        };
        Range range = range(from, to);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT DATE_FORMAT(created_at, ?) AS period, kind, SUM(amount_cents) AS total "
                + "FROM ledger_entries WHERE party = ? AND created_at >= ? AND created_at < DATE_ADD(?, INTERVAL 1 DAY) "
                + "GROUP BY period, kind ORDER BY period", format, party, range.from(), range.to());

        Map<String, Map<String, Object>> buckets = new LinkedHashMap<>();
        Map<String, Long> totals = emptyTotals();
        for (Map<String, Object> row : rows) {
            String period = String.valueOf(row.get("period"));
            String kind = String.valueOf(row.get("kind"));
            long total = ((Number) row.get("total")).longValue();
            Map<String, Object> bucket = buckets.computeIfAbsent(period, key -> {
                Map<String, Object> created = new LinkedHashMap<>();
                created.put("period", period);
                created.putAll(emptyTotals());
                return created;
            });
            bucket.put(kind + "Cents", total);
            bucket.put("netCents", ((Number) bucket.get("netCents")).longValue() + total);
            totals.merge(kind + "Cents", total, Long::sum);
            totals.merge("netCents", total, Long::sum);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scope", party);
        result.put("groupBy", groupBy == null || groupBy.isBlank() ? "day" : groupBy);
        result.put("from", range.from());
        result.put("to", range.to());
        result.put("totals", totals);
        result.put("buckets", new ArrayList<>(buckets.values()));
        return result;
    }

    public String earningsCsv(String scope, String from, String to) {
        String party = party(scope);
        Range range = range(from, to);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT created_at, order_id, kind, amount_cents, description FROM ledger_entries "
                + "WHERE party = ? AND created_at >= ? AND created_at < DATE_ADD(?, INTERVAL 1 DAY) ORDER BY id DESC",
            party, range.from(), range.to());
        StringBuilder csv = new StringBuilder("data,pedido,tipo,valor_reais,descricao\n");
        for (Map<String, Object> row : rows) {
            csv.append(row.get("created_at")).append(',')
                .append(row.get("order_id") == null ? "" : row.get("order_id")).append(',')
                .append(csv(row.get("kind"))).append(',')
                .append(String.format(Locale.ROOT, "%.2f", ((Number) row.get("amount_cents")).longValue() / 100.0)).append(',')
                .append(csv(row.get("description"))).append('\n');
        }
        return csv.toString();
    }

    public Map<String, Object> orders(String from, String to) {
        Range range = range(from, to);
        List<Map<String, Object>> byStatus = jdbc.queryForList(
            "SELECT status, COUNT(*) AS count, COALESCE(SUM(total_cents),0) AS total_cents FROM orders "
                + "WHERE created_at >= ? AND created_at < DATE_ADD(?, INTERVAL 1 DAY) GROUP BY status ORDER BY count DESC",
            range.from(), range.to());
        Map<String, Object> totals = jdbc.queryForMap(
            "SELECT COUNT(*) AS count, COALESCE(SUM(total_cents),0) AS total_cents, "
                + "COALESCE(SUM(CASE WHEN status IN " + COMPLETED + " THEN total_cents ELSE 0 END),0) AS revenue_cents "
                + "FROM orders WHERE created_at >= ? AND created_at < DATE_ADD(?, INTERVAL 1 DAY)",
            range.from(), range.to());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", range.from());
        result.put("to", range.to());
        result.put("totals", totals);
        result.put("byStatus", byStatus);
        return result;
    }

    public List<Map<String, Object>> products(String from, String to, int limit) {
        Range range = range(from, to);
        return jdbc.queryForList(
            "SELECT p.id, p.name, SUM(oi.quantity) AS quantity, SUM(oi.quantity * oi.unit_price_cents) AS revenue_cents "
                + "FROM order_items oi JOIN orders o ON o.id = oi.order_id JOIN products p ON p.id = oi.product_id "
                + "WHERE o.created_at >= ? AND o.created_at < DATE_ADD(?, INTERVAL 1 DAY) "
                + "GROUP BY p.id, p.name ORDER BY revenue_cents DESC LIMIT ?",
            range.from(), range.to(), limit);
    }

    public List<Map<String, Object>> zones(String from, String to, int limit) {
        Range range = range(from, to);
        return jdbc.queryForList(
            "SELECT z.id, z.name, COUNT(*) AS orders, COALESCE(SUM(CASE WHEN o.status IN " + COMPLETED + " THEN o.total_cents ELSE 0 END),0) AS revenue_cents "
                + "FROM orders o JOIN zones z ON z.id = o.zone_id WHERE o.zone_id IS NOT NULL "
                + "AND o.created_at >= ? AND o.created_at < DATE_ADD(?, INTERVAL 1 DAY) "
                + "GROUP BY z.id, z.name ORDER BY orders DESC LIMIT ?",
            range.from(), range.to(), limit);
    }

    public List<Map<String, Object>> daily(String from, String to) {
        Range range = range(from, to);
        return jdbc.queryForList(
            "SELECT DATE(created_at) AS day, COUNT(*) AS orders, COALESCE(SUM(CASE WHEN status IN " + COMPLETED + " THEN total_cents ELSE 0 END),0) AS revenue_cents "
                + "FROM orders WHERE created_at >= ? AND created_at < DATE_ADD(?, INTERVAL 1 DAY) GROUP BY DATE(created_at) ORDER BY day DESC",
            range.from(), range.to());
    }

    public List<Map<String, Object>> customers(String from, String to, int limit) {
        Range range = range(from, to);
        return jdbc.queryForList(
            "SELECT u.id, u.name, COUNT(*) AS orders, COALESCE(SUM(o.total_cents),0) AS spend_cents "
                + "FROM orders o JOIN users u ON u.id = o.customer_id "
                + "WHERE o.created_at >= ? AND o.created_at < DATE_ADD(?, INTERVAL 1 DAY) "
                + "GROUP BY u.id, u.name ORDER BY spend_cents DESC LIMIT ?",
            range.from(), range.to(), limit);
    }

    public String reportCsv(String report, String from, String to) {
        return switch (report == null ? "" : report.strip()) {
            case "earnings" -> earningsCsv("admin", from, to);
            case "orders" -> ordersCsv(from, to);
            case "products" -> productsCsv(from, to);
            case "customers" -> customersCsv(from, to);
            default -> throw new ApiException(400, "Relatório inválido");
        };
    }

    private String ordersCsv(String from, String to) {
        Range range = range(from, to);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, status, order_type, total_cents, created_at FROM orders "
                + "WHERE created_at >= ? AND created_at < DATE_ADD(?, INTERVAL 1 DAY) ORDER BY id DESC",
            range.from(), range.to());
        StringBuilder csv = new StringBuilder("pedido,status,tipo,total_reais,criado_em\n");
        for (Map<String, Object> row : rows) {
            csv.append(row.get("id")).append(',')
                .append(csv(row.get("status"))).append(',')
                .append(csv(row.get("order_type"))).append(',')
                .append(String.format(Locale.ROOT, "%.2f", ((Number) row.get("total_cents")).longValue() / 100.0)).append(',')
                .append(row.get("created_at")).append('\n');
        }
        return csv.toString();
    }

    private String productsCsv(String from, String to) {
        StringBuilder csv = new StringBuilder("produto,quantidade,receita_reais\n");
        for (Map<String, Object> row : products(from, to, 500)) {
            csv.append(csv(row.get("name"))).append(',')
                .append(row.get("quantity")).append(',')
                .append(String.format(Locale.ROOT, "%.2f", ((Number) row.get("revenue_cents")).longValue() / 100.0)).append('\n');
        }
        return csv.toString();
    }

    private String customersCsv(String from, String to) {
        StringBuilder csv = new StringBuilder("cliente,pedidos,gasto_reais\n");
        for (Map<String, Object> row : customers(from, to, 500)) {
            csv.append(csv(row.get("name"))).append(',')
                .append(row.get("orders")).append(',')
                .append(String.format(Locale.ROOT, "%.2f", ((Number) row.get("spend_cents")).longValue() / 100.0)).append('\n');
        }
        return csv.toString();
    }

    private static String party(String scope) {
        String value = scope == null || scope.isBlank() ? "admin" : scope.strip().toLowerCase(Locale.ROOT);
        if (!List.of("admin", "restaurant", "courier").contains(value)) throw new ApiException(400, "Escopo inválido");
        return value;
    }

    private static Map<String, Long> emptyTotals() {
        Map<String, Long> totals = new LinkedHashMap<>();
        totals.put("saleCents", 0L);
        totals.put("commissionCents", 0L);
        totals.put("delivery_feeCents", 0L);
        totals.put("tipCents", 0L);
        totals.put("refundCents", 0L);
        totals.put("payoutCents", 0L);
        totals.put("adjustmentCents", 0L);
        totals.put("netCents", 0L);
        return totals;
    }

    private static String csv(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }
}
