package com.foodie.api.admin;

import com.foodie.api.auth.User;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Registra e consulta a trilha administrativa (E01). Falha ao registrar não deve derrubar a
 * operação que já foi concluída, por isso o registro é tolerante a erro.
 */
@Service
public class AdminAuditService {
    private static final Logger log = LoggerFactory.getLogger(AdminAuditService.class);
    private static final int MAX_LIMIT = 200;

    private final AdminAuditRepository audit;

    public AdminAuditService(AdminAuditRepository audit) {
        this.audit = audit;
    }

    public void record(User actor, String action, String entity, Long entityId, String summary) {
        try {
            audit.insert(actor == null ? null : actor.id(), actor == null ? null : actor.name(), action, entity, entityId, summary);
        } catch (Exception error) {
            log.warn("Não foi possível registrar a trilha administrativa ({}/{}): {}", action, entity, error.getMessage());
        }
    }

    public List<AdminAuditRepository.Entry> list(String entity, Long actorUserId, String from, String to, Long beforeId, Integer limit) {
        int size = limit == null ? 50 : Math.max(1, Math.min(MAX_LIMIT, limit));
        return audit.list(normalize(entity), actorUserId, normalize(from), normalize(to), beforeId, size);
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
