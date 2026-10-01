package com.foodie.api.support;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminAuditRepository;
import com.foodie.api.auth.User;
import com.foodie.api.notifications.NotificationService;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ponto único das intervenções do modo suporte (E48): valida o motivo, confere a loja, executa a
 * alteração, grava a auditoria e avisa a loja, tudo na mesma transação; falha no aviso só é
 * registrada em log.
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
        notifySafely(restaurantId, "Suporte Foodie: " + summary, normalized, null);
        return result;
    }

    /** Ações de pedido já validam o próprio motivo em {@code OrderService}; aqui só entram na trilha da loja. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordOrderAction(User actor, long restaurantId, long orderId, String action, String reason) {
        String label = switch (action) {
            case "cancel" -> "cancelado";
            case "assign" -> "entregador atribuído";
            case "unassign" -> "entregador removido";
            default -> action;
        };
        String summary = "Pedido #" + orderId + " " + label;
        audit.insertSupport(actor.id(), actor.name(), restaurantId, "order." + action, "order", orderId, summary, reason);
        notifySafely(restaurantId, "Suporte Foodie: " + summary, reason == null ? "" : reason, orderId);
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

    private void notifySafely(long restaurantId, String title, String body, Long orderId) {
        try {
            notifications.notifyRestaurant(restaurantId, "support_action", truncate(title, 160), truncate(body, 500), orderId);
        } catch (TransientDataAccessException error) {
            throw error;
        } catch (Exception error) {
            log.warn("Não foi possível avisar a loja sobre a intervenção de suporte: {}", error.getMessage());
        }
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
