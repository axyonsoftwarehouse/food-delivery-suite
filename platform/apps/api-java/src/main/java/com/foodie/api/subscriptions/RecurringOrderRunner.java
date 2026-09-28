package com.foodie.api.subscriptions;

import com.foodie.api.ApiException;
import com.foodie.api.auth.User;
import com.foodie.api.notifications.NotificationService;
import com.foodie.api.orders.OrderController;
import com.foodie.api.orders.OrderService;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RecurringOrderRunner {
    private static final Logger log = LoggerFactory.getLogger(RecurringOrderRunner.class);
    private final JdbcTemplate jdbc;
    private final OrderService orders;
    private final NotificationService notifications;
    private final ObjectProvider<PlatformTransactionManager> transactionManagers;

    public RecurringOrderRunner(JdbcTemplate jdbc, OrderService orders,
                                NotificationService notifications, ObjectProvider<PlatformTransactionManager> transactionManagers) {
        this.jdbc = jdbc;
        this.orders = orders;
        this.notifications = notifications;
        this.transactionManagers = transactionManagers;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void runDue() {
        TransactionTemplate transactions = new TransactionTemplate(transactionManagers.getObject());
        List<Long> due = jdbc.query("SELECT id FROM subscriptions WHERE status = 'active' AND next_run_at <= NOW() ORDER BY next_run_at, id LIMIT 50",
            (rs, row) -> rs.getLong(1));
        for (long id : due) {
            try {
                transactions.executeWithoutResult(status -> runOne(id));
            } catch (RuntimeException error) {
                log.warn("Falha ao gerar recorrência {}", id, error);
                try {
                    transactions.executeWithoutResult(status -> failOne(id, error));
                } catch (RuntimeException registrationError) {
                    log.error("Falha ao registrar erro da recorrência {}", id, registrationError);
                }
            }
        }
    }

    private void runOne(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT s.*, u.name AS customer_name, u.email AS customer_email FROM subscriptions s "
                + "JOIN users u ON u.id = s.customer_id WHERE s.id = ? FOR UPDATE", id);
        if (rows.isEmpty()) return;
        Map<String, Object> subscription = rows.getFirst();
        if (!"active".equals(subscription.get("status")) || subscription.get("next_run_at") == null) return;
        LocalDateTime scheduled = ((Timestamp) subscription.get("next_run_at")).toLocalDateTime();
        if (scheduled.isAfter(LocalDateTime.now())) return;
        Integer existing = jdbc.query("SELECT 1 FROM subscription_order_runs WHERE subscription_id = ? AND scheduled_for = ?",
            rs -> rs.next() ? 1 : null, id, Timestamp.valueOf(scheduled));
        int frequency = ((Number) subscription.get("frequency_days")).intValue();
        LocalDateTime next = scheduled.plusDays(frequency);
        if (!next.isAfter(LocalDateTime.now())) next = LocalDateTime.now().plusDays(frequency);
        if (existing != null) {
            jdbc.update("UPDATE subscriptions SET next_run_at = ? WHERE id = ?", Timestamp.valueOf(next), id);
            return;
        }
        Integer paused = jdbc.query("SELECT 1 FROM subscription_pauses WHERE subscription_id = ? AND starts_at <= DATE(?) AND ends_at >= DATE(?) LIMIT 1",
            rs -> rs.next() ? 1 : null, id, Timestamp.valueOf(scheduled), Timestamp.valueOf(scheduled));
        if (paused != null) {
            jdbc.update("INSERT INTO subscription_order_runs (subscription_id, scheduled_for, status, reason) VALUES (?, ?, 'skipped', ?)",
                id, Timestamp.valueOf(scheduled), "Pausa programada");
            jdbc.update("UPDATE subscriptions SET next_run_at = ?, last_error = NULL WHERE id = ?", Timestamp.valueOf(next), id);
            return;
        }
        if (subscription.get("address_id") == null) throw new IllegalStateException("Endereço não configurado");
        List<OrderController.Item> items = jdbc.query(
            "SELECT product_id, variation_id, quantity FROM subscription_items WHERE subscription_id = ? ORDER BY id",
            (rs, row) -> new OrderController.Item(rs.getLong("product_id"),
                rs.getObject("variation_id") == null ? null : rs.getLong("variation_id"), rs.getInt("quantity"), null), id);
        if (items.isEmpty()) throw new IllegalStateException("Assinatura sem itens");
        long customerId = ((Number) subscription.get("customer_id")).longValue();
        User customer = new User(customerId, (String) subscription.get("customer_name"),
            (String) subscription.get("customer_email"), "customer", null);
        Map<String, Object> order = orders.create(customer, new OrderController.OrderRequest(
            ((Number) subscription.get("restaurant_id")).longValue(),
            ((Number) subscription.get("address_id")).longValue(), items,
            (String) subscription.get("payment_method"), null, "on_delivery", null,
            null, "delivery", null, null, null));
        long orderId = ((Number) order.get("id")).longValue();
        jdbc.update("INSERT INTO subscription_order_runs (subscription_id, scheduled_for, order_id, status) VALUES (?, ?, ?, 'created')",
            id, Timestamp.valueOf(scheduled), orderId);
        jdbc.update("UPDATE subscriptions SET next_run_at = ?, last_error = NULL WHERE id = ?", Timestamp.valueOf(next), id);
        notifications.notifyUser(customerId, "recurring_order", "Pedido recorrente #" + orderId,
            "Seu pedido recorrente foi criado e aguarda aceite do restaurante.", orderId);
    }

    private void failOne(long id, RuntimeException error) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT customer_id, next_run_at, status FROM subscriptions WHERE id = ? FOR UPDATE", id);
        if (rows.isEmpty() || !"active".equals(rows.getFirst().get("status"))) return;
        Map<String, Object> row = rows.getFirst();
        if (row.get("next_run_at") == null) return;
        String reason = error instanceof ApiException || error instanceof IllegalStateException
            ? error.getMessage() : "Falha temporária ao criar pedido";
        if (reason == null || reason.isBlank()) reason = "Falha ao criar pedido";
        if (reason.length() > 255) reason = reason.substring(0, 255);
        jdbc.update("INSERT IGNORE INTO subscription_order_runs (subscription_id, scheduled_for, status, reason) VALUES (?, ?, 'failed', ?)",
            id, row.get("next_run_at"), reason);
        jdbc.update("UPDATE subscriptions SET status = 'paused', last_error = ? WHERE id = ?", reason, id);
        notifications.notifyUser(((Number) row.get("customer_id")).longValue(), "recurring_order_failed",
            "Pedido recorrente pausado", "Confira sua recorrência: " + reason, null);
    }
}
