package com.foodie.api.subscriptions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.api.auth.User;
import com.foodie.api.notifications.NotificationService;
import com.foodie.api.orders.OrderController;
import com.foodie.api.orders.OrderService;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class RecurringOrderRunnerTest {
    @Test
    @SuppressWarnings("unchecked")
    void createsOneOrderForDueOccurrenceAndRecordsItsRun() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        OrderService orders = mock(OrderService.class);
        NotificationService notifications = mock(NotificationService.class);
        ObjectProvider<PlatformTransactionManager> providers = mock(ObjectProvider.class);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(providers.getObject()).thenReturn(manager);
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        Timestamp due = Timestamp.valueOf(LocalDateTime.now().minusMinutes(1));
        when(jdbc.query(startsWith("SELECT id FROM subscriptions"), any(RowMapper.class))).thenReturn(List.of(7L));
        when(jdbc.queryForList(startsWith("SELECT s.*"), eq(7L))).thenReturn(List.of(Map.of(
            "id", 7L, "status", "active", "next_run_at", due, "frequency_days", 7,
            "customer_id", 3L, "customer_name", "Cliente", "customer_email", "cliente@example.test",
            "restaurant_id", 9L, "address_id", 12L, "payment_method", "pix")));
        when(jdbc.query(startsWith("SELECT 1 FROM subscription_order_runs"),
            (ResultSetExtractor<Integer>) any(ResultSetExtractor.class), any(), any())).thenReturn(null);
        when(jdbc.query(startsWith("SELECT 1 FROM subscription_pauses"),
            (ResultSetExtractor<Integer>) any(ResultSetExtractor.class), any(), any(), any())).thenReturn(null);
        when(jdbc.query(startsWith("SELECT product_id"), any(RowMapper.class), eq(7L)))
            .thenReturn(List.of(new OrderController.Item(4L, null, 2, null)));
        when(orders.create(any(User.class), any(OrderController.OrderRequest.class)))
            .thenReturn(Map.of("id", 18L));

        new RecurringOrderRunner(jdbc, orders, notifications, providers).runDue();

        verify(orders, times(1)).create(any(User.class), any(OrderController.OrderRequest.class));
        verify(jdbc).update(startsWith("INSERT INTO subscription_order_runs"), eq(7L), eq(due), eq(18L));
        verify(notifications).notifyUser(eq(3L), eq("recurring_order"), anyString(), anyString(), eq(18L));
    }
}
