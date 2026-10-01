package com.foodie.api.support;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminAuditRepository;
import com.foodie.api.auth.User;
import com.foodie.api.notifications.NotificationService;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Ponto único das intervenções do modo suporte (E48): valida o motivo, confere a loja, executa a
 * alteração, grava a auditoria na mesma transação e avisa a loja depois do commit.
 */
@Service
public class SupportActionService {
    private static final Logger log = LoggerFactory.getLogger(SupportActionService.class);
    public static final int MIN_REASON = 10;
    public static final int MAX_REASON = 500;

    private final JdbcTemplate jdbc;
    private final AdminAuditRepository audit;
    private final NotificationService notifications;

    public SupportActionService(JdbcTemplate jdbc, AdminAuditRepository audit, NotificationService notifications) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.notifications = notifications;
    }

    @Transactional
    public <T> T act(User actor, long restaurantId, String action, String entity, Long entityId, String summary,
                     String reason, Supplier<T> change) {
        String normalized = normalizeReason(reason);
        requireRestaurant(restaurantId);
        T result = change.get();
        audit.insertSupport(actor.id(), actor.name(), restaurantId, action, entity, entityId, summary, normalized);
        afterCommit(() -> notifications.notifyRestaurant(restaurantId, "support_action", "Suporte Foodie: " + summary, normalized, null));
        return result;
    }

    /** Ações de pedido já validam o próprio motivo em {@code OrderService}; aqui só entram na trilha da loja. */
    public void recordOrderAction(User actor, long restaurantId, long orderId, String action, String reason) {
        String summary = "Pedido #" + orderId + ": " + action;
        audit.insertSupport(actor.id(), actor.name(), restaurantId, "order." + action, "order", orderId, summary, reason);
        afterCommit(() -> notifications.notifyRestaurant(restaurantId, "support_action", "Suporte Foodie: " + summary,
            reason == null ? "" : reason, orderId));
    }

    public static String normalizeReason(String reason) {
        String trimmed = reason == null ? "" : reason.strip();
        if (trimmed.length() < MIN_REASON || trimmed.length() > MAX_REASON) {
            throw new ApiException(400, "Informe o motivo da intervenção (10 a 500 caracteres)");
        }
        return trimmed;
    }

    public void requireRestaurant(long restaurantId) {
        Integer exists = jdbc.query("SELECT 1 FROM restaurants WHERE id = ?", rs -> rs.next() ? 1 : null, restaurantId);
        if (exists == null) throw new ApiException(404, "Restaurante não encontrado");
    }

    private void afterCommit(Runnable task) {
        Runnable safe = () -> {
            try {
                task.run();
            } catch (Exception error) {
                log.warn("Não foi possível avisar a loja sobre a intervenção de suporte: {}", error.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safe.run();
                }
            });
        } else {
            safe.run();
        }
    }
}
