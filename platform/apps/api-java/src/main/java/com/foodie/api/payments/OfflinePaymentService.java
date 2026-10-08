package com.foodie.api.payments;

import com.foodie.api.ApiException;
import com.foodie.api.auth.User;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Métodos de pagamento manuais (offline) com envio e verificação de comprovante. */
@Service
public class OfflinePaymentService {
    private static final Set<String> CLOSED_ORDER = Set.of("delivered", "rejected", "cancelled", "expired", "failed");
    private static final java.util.regex.Pattern SLUG = java.util.regex.Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    private final JdbcTemplate jdbc;

    public OfflinePaymentService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> methods(boolean onlyActive) {
        String sql = "SELECT id, name, slug, instructions, requires_proof, active FROM offline_payment_methods";
        return onlyActive
            ? jdbc.queryForList(sql + " WHERE active = TRUE ORDER BY name")
            : jdbc.queryForList(sql + " ORDER BY name");
    }

    @Transactional
    public Map<String, Object> createMethod(String name, String slug, String instructions, boolean requiresProof, boolean active) {
        String cleanName = name == null ? "" : name.strip();
        if (cleanName.length() < 2) throw new ApiException(400, "Nome do método inválido");
        String cleanSlug = slug == null ? "" : slug.strip().toLowerCase(java.util.Locale.ROOT);
        if (!SLUG.matcher(cleanSlug).matches()) throw new ApiException(400, "Identificador inválido (use letras minúsculas e hífen)");
        Integer exists = jdbc.query("SELECT 1 FROM offline_payment_methods WHERE slug = ?", rs -> rs.next() ? 1 : null, cleanSlug);
        if (exists != null) throw new ApiException(409, "Já existe um método com este identificador");
        jdbc.update("INSERT INTO offline_payment_methods (name, slug, instructions, requires_proof, active) VALUES (?, ?, ?, ?, ?)",
            cleanName, cleanSlug, clean(instructions), requiresProof, active);
        return methodBySlug(cleanSlug);
    }

    @Transactional
    public Map<String, Object> updateMethod(long id, String name, String instructions, Boolean requiresProof, Boolean active) {
        Map<String, Object> current = methodById(id);
        String cleanName = name == null ? (String) current.get("name") : name.strip();
        if (cleanName.length() < 2) throw new ApiException(400, "Nome do método inválido");
        String cleanInstructions = instructions == null ? (String) current.get("instructions") : clean(instructions);
        boolean proof = requiresProof == null ? truthy(current.get("requires_proof")) : requiresProof;
        boolean isActive = active == null ? truthy(current.get("active")) : active;
        jdbc.update("UPDATE offline_payment_methods SET name = ?, instructions = ?, requires_proof = ?, active = ? WHERE id = ?",
            cleanName, cleanInstructions, proof, isActive, id);
        return methodById(id);
    }

    @Transactional
    public Map<String, Object> submitProof(User actor, long orderId, long methodId, String proofUrl, String note) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT p.status, p.modality, p.external_id, o.customer_id, o.status AS order_status FROM order_payments p JOIN orders o ON o.id = p.order_id WHERE p.order_id = ? FOR UPDATE",
            orderId
        );
        if (rows.isEmpty()) throw new ApiException(404, "Pagamento não encontrado");
        Map<String, Object> row = rows.getFirst();
        if (!"admin".equals(actor.role()) && number(row, "customer_id") != actor.id()) throw new ApiException(403, "Acesso não autorizado");
        if (CLOSED_ORDER.contains(String.valueOf(row.get("order_status")))) throw new ApiException(409, "Este pedido não aceita mais pagamento");
        String status = String.valueOf(row.get("status"));
        if ("paid".equals(status) || "refunded".equals(status)) throw new ApiException(409, "Este pedido já está pago");
        // Cobrança online ainda pagável: trocar para comprovante permitiria pagar duas vezes (revisão de 08/10/2026).
        if ("online".equals(row.get("modality")) && "pending".equals(status) && row.get("external_id") != null) {
            throw new ApiException(409, "Há uma cobrança online em aberto para este pedido. Pague por ela ou espere ela expirar.");
        }

        List<Map<String, Object>> methods = jdbc.queryForList(
            "SELECT id, slug, requires_proof FROM offline_payment_methods WHERE id = ? AND active = TRUE", methodId);
        if (methods.isEmpty()) throw new ApiException(400, "Método de pagamento indisponível");
        Map<String, Object> method = methods.getFirst();

        String url = clean(proofUrl);
        if (url != null && !url.matches("https?://\\S+")) throw new ApiException(400, "O comprovante deve ser um link http(s)");
        if (truthy(method.get("requires_proof")) && url == null) throw new ApiException(400, "Envie o comprovante para este método");

        jdbc.update("UPDATE order_payments SET modality = 'offline', method = 'offline', provider = ?, offline_method_id = ?, "
                + "proof_url = ?, proof_note = ?, submitted_at = NOW(), rejection_reason = NULL, status = 'pending' WHERE order_id = ?",
            method.get("slug"), methodId, url, clean(note), orderId);
        return statusResult(orderId, "pending", "Comprovante recebido");
    }

    @Transactional
    public Map<String, Object> verify(User actor, long orderId, boolean approve, String note) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT modality, status, amount_due_cents, o.restaurant_id FROM order_payments p JOIN orders o ON o.id = p.order_id WHERE p.order_id = ? FOR UPDATE",
            orderId
        );
        if (rows.isEmpty()) throw new ApiException(404, "Pagamento não encontrado");
        Map<String, Object> row = rows.getFirst();
        if ("restaurant".equals(actor.role())) {
            Long restaurantId = actor.restaurantId();
            if (restaurantId == null || number(row, "restaurant_id") != restaurantId) throw new ApiException(403, "Acesso não autorizado");
        } else if (!"admin".equals(actor.role())) {
            throw new ApiException(403, "Acesso não autorizado");
        }
        if (!"offline".equals(String.valueOf(row.get("modality")))) throw new ApiException(409, "Este pedido não usa pagamento manual");
        String status = String.valueOf(row.get("status"));
        if (!"pending".equals(status) && !"rejected".equals(status)) throw new ApiException(409, "Pagamento não está aguardando verificação");

        String trimmed = clean(note);
        if (approve) {
            jdbc.update("UPDATE order_payments SET status = 'paid', amount_received_cents = amount_due_cents, change_cents = 0, "
                    + "confirmed_by = ?, confirmed_at = NOW(), note = ?, rejection_reason = NULL WHERE order_id = ?",
                actor.id(), trimmed, orderId);
            return statusResult(orderId, "paid", "Pagamento confirmado");
        }
        if (trimmed == null || trimmed.length() < 3) throw new ApiException(400, "Informe o motivo da recusa (3 a 255 caracteres)");
        jdbc.update("UPDATE order_payments SET status = 'rejected', rejection_reason = ?, confirmed_by = ?, confirmed_at = NOW() WHERE order_id = ?",
            trimmed, actor.id(), orderId);
        return statusResult(orderId, "rejected", "Comprovante recusado");
    }

    private Map<String, Object> methodById(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, name, slug, instructions, requires_proof, active FROM offline_payment_methods WHERE id = ?", id);
        if (rows.isEmpty()) throw new ApiException(404, "Método não encontrado");
        return rows.getFirst();
    }

    private Map<String, Object> methodBySlug(String slug) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, name, slug, instructions, requires_proof, active FROM offline_payment_methods WHERE slug = ?", slug);
        return rows.getFirst();
    }

    private static Map<String, Object> statusResult(long orderId, String status, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", orderId);
        result.put("status", status);
        result.put("message", message);
        return result;
    }

    private static String clean(String value) {
        if (value == null) return null;
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean flag) return flag;
        if (value instanceof Number number) return number.intValue() != 0;
        return "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }

    private static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }
}
