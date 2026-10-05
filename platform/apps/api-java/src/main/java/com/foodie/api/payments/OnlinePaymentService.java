package com.foodie.api.payments;

import com.foodie.api.ApiException;
import com.foodie.api.auth.User;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnlinePaymentService {
    private static final Set<String> METHODS = Set.of("pix", "card");
    /** Valores aceitos pela coluna status de order_payments. */
    private static final Set<String> STATUSES = Set.of("pending", "paid", "cancelled", "refunded", "rejected", "expired");
    private static final Set<String> CLOSED_ORDER = Set.of("delivered", "rejected", "cancelled", "expired", "failed");
    /** TLDs reservados (RFC 6761/2606) e de rede interna: o Mercado Pago recusa como email do pagador. */
    private static final Set<String> TLDS_RESERVADOS = Set.of("local", "localhost", "test", "invalid", "example", "internal", "lan", "home");

    private final JdbcTemplate jdbc;
    private final PaymentGatewayRegistry gateways;
    private final boolean allowDirectOnlineCharges;

    public OnlinePaymentService(JdbcTemplate jdbc, PaymentGatewayRegistry gateways,
                                @Value("${app.payments.allow-direct-online-charges:false}") boolean allowDirectOnlineCharges) {
        this.jdbc = jdbc;
        this.gateways = gateways;
        this.allowDirectOnlineCharges = allowDirectOnlineCharges;
    }

    /**
     * Intenção de cobrança online. {@code cardToken} vem do navegador (checkout transparente) e é o
     * único dado do cartão que chega até aqui; {@code installments}, {@code docType} e {@code docNumber}
     * só se aplicam a cartão.
     */
    public record Intent(String method, String provider, String cardToken, Integer installments, String docType, String docNumber, String paymentMethodId) {
        /** Cartão sem a bandeira (o formulário manda a bandeira quando o Mercado Pago a informa). */
        public Intent(String method, String provider, String cardToken, Integer installments, String docType, String docNumber) {
            this(method, provider, cardToken, installments, docType, docNumber, null);
        }

        public static Intent of(String method, String provider) {
            return new Intent(method, provider, null, null, null, null, null);
        }
    }

    /** A cobrança online direta está ligada nesta instalação? É o que o checkout precisa saber para oferecer. */
    public boolean directChargesAllowed() {
        return allowDirectOnlineCharges;
    }

    @Transactional
    public Map<String, Object> startIntent(User actor, long orderId, String method, String provider) {
        return startIntent(actor, orderId, Intent.of(method, provider));
    }

    @Transactional
    public Map<String, Object> startIntent(User actor, long orderId, Intent intent) {
        String method = intent.method();
        String provider = intent.provider();
        if (method == null || !METHODS.contains(method)) throw new ApiException(400, "Forma de pagamento online inválida");
        List<Map<String, Object>> orders = jdbc.queryForList("SELECT id, customer_id, total_cents, status FROM orders WHERE id = ? FOR UPDATE", orderId);
        if (orders.isEmpty()) throw new ApiException(404, "Pedido não encontrado");
        Map<String, Object> order = orders.getFirst();
        boolean owner = ((Number) order.get("customer_id")).longValue() == actor.id();
        if (!owner && !"admin".equals(actor.role())) throw new ApiException(403, "Acesso não autorizado");
        if (CLOSED_ORDER.contains((String) order.get("status"))) throw new ApiException(409, "Este pedido não aceita mais pagamento");

        List<Map<String, Object>> payments = jdbc.queryForList(
            "SELECT id, status, external_id, qr_code, ticket_url, amount_due_cents FROM order_payments WHERE order_id = ? FOR UPDATE", orderId);
        if (payments.isEmpty()) throw new ApiException(409, "Pagamento do pedido não encontrado");
        Map<String, Object> payment = payments.getFirst();
        String paymentStatus = (String) payment.get("status");
        if ("paid".equals(paymentStatus)) throw new ApiException(409, "Este pedido já está pago");
        if ("pending".equals(paymentStatus) && (payment.get("qr_code") != null || payment.get("ticket_url") != null)) {
            return detail(orderId);
        }
        if (!allowDirectOnlineCharges) {
            throw new ApiException(409, "Novas cobranças online exigem recebimento direto pelo restaurante");
        }
        // Cobrança já criada e sem QR nem link (provedor não devolveu): devolve o que está guardado,
        // em vez de cobrar de novo.
        if (payment.get("external_id") != null) return detail(orderId);

        long due = ((Number) payment.get("amount_due_cents")).longValue();
        String payerEmail = jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, ((Number) order.get("customer_id")).longValue());
        String payerFirstName = primeiroNome(jdbc.queryForObject("SELECT name FROM users WHERE id = ?", String.class, ((Number) order.get("customer_id")).longValue()));
        if (!emailValido(payerEmail)) {
            throw new ApiException(400, "O Mercado Pago recusa o email do cliente para cobrança online (endereço de demonstração ou inválido): "
                + (payerEmail == null || payerEmail.isBlank() ? "sem email cadastrado" : payerEmail));
        }
        String idempotencyKey = "order-" + orderId + "-" + payment.get("id");

        if ("card".equals(method)) {
            if (intent.cardToken() == null || intent.cardToken().isBlank()) {
                throw new ApiException(400, "Pagamento com cartão exige o token do cartão — o formulário do checkout gera esse token no navegador");
            }
            if (intent.docType() == null || intent.docNumber() == null || intent.docNumber().isBlank()) {
                throw new ApiException(400, "Pagamento com cartão exige o CPF (ou CNPJ) do titular");
            }
        }

        PaymentGateway gateway = gateways.resolve(provider);
        PaymentGateway.Charge charge = gateway.create(new PaymentGateway.ChargeRequest(
            orderId, due, method, "Pedido #" + orderId, payerEmail, idempotencyKey,
            intent.cardToken(), intent.installments(), intent.docType(), intent.docNumber(),
            intent.paymentMethodId(), payerFirstName));

        String status = charge.status() == null || !STATUSES.contains(charge.status()) ? "pending" : charge.status();
        // Cartão pode voltar aprovado na própria cobrança. Aí o webhook que chega depois encontra "pago" e
        // não faz nada — então a conferência de valor e o confirmed_at do webhook têm de acontecer aqui.
        String note = null;
        if ("paid".equals(status) && charge.amountCents() != due) {
            status = "rejected";
            note = "Valor divergente: provedor " + charge.amountCents() + " vs pedido " + due;
        }
        jdbc.update("UPDATE order_payments SET provider = ?, method = ?, external_id = ?, idempotency_key = ?, status = ?, raw_status = ?,"
                + " qr_code = ?, qr_code_base64 = ?, ticket_url = ?, expires_at = ?, note = ?,"
                + " confirmed_at = IF(? = 'paid', NOW(), confirmed_at) WHERE order_id = ?",
            gateway.provider(), method, charge.externalId(), idempotencyKey, status, charge.rawStatus(),
            charge.qrCode(), charge.qrCodeBase64(), charge.ticketUrl(),
            charge.expiresAt() == null ? null : Timestamp.from(charge.expiresAt()), note, status, orderId);
        return detail(orderId);
    }

    @Transactional
    public Map<String, Object> handleWebhook(String provider, String paymentId) {
        PaymentGateway.Charge charge;
        try {
            charge = gateways.resolve(provider).fetch(paymentId);
        } catch (ApiException naoEncontrado) {
            // Notificação sobre algo que não é nosso — o próprio painel do Mercado Pago testa o webhook
            // com um pedido fictício ("123456"). Responder erro faria o provedor reenviar para sempre e
            // marcaria a integração como quebrada na tela dele: aqui a resposta certa é 200, ignorando.
            if (naoEncontrado.status() == 404) return Map.of("ok", true, "ignored", true);
            throw naoEncontrado;
        }
        if (charge.externalReference() == null) return Map.of("ok", true, "ignored", true);
        long orderId;
        try { orderId = Long.parseLong(charge.externalReference()); } catch (NumberFormatException error) { return Map.of("ok", true, "ignored", true); }

        List<Map<String, Object>> rows = jdbc.queryForList("SELECT status, amount_due_cents FROM order_payments WHERE order_id = ? FOR UPDATE", orderId);
        if (rows.isEmpty()) return Map.of("ok", true, "ignored", true);
        Map<String, Object> row = rows.getFirst();
        String current = (String) row.get("status");
        if ("paid".equals(current) || "refunded".equals(current)) return Map.of("ok", true, "already", current);

        long due = ((Number) row.get("amount_due_cents")).longValue();
        String next = charge.status();
        String note = null;
        if ("paid".equals(next) && charge.amountCents() != due) {
            next = "rejected";
            note = "Valor divergente: provedor " + charge.amountCents() + " vs pedido " + due;
        }
        jdbc.update("UPDATE order_payments SET status = ?, raw_status = ?, external_id = ?, note = ?, confirmed_at = IF(? = 'paid', NOW(), confirmed_at) WHERE order_id = ?",
            next, charge.rawStatus(), charge.externalId(), note, next, orderId);
        return Map.of("ok", true, "orderId", orderId, "status", next);
    }

    /**
     * O Mercado Pago recusa o email do pagador quando o domínio não é entregável — o caso concreto do
     * dado de demonstração: {@code cliente@demo.local} voltou "payer.email must be a valid email".
     * Barrar antes evita um 502 sem explicação na tela do cliente.
     */
    /** Primeiro nome do pagador — o Mercado Pago usa esse campo nos resultados predefinidos de teste. */
    static String primeiroNome(String nome) {
        if (nome == null) return null;
        String limpo = nome.trim();
        if (limpo.isEmpty()) return null;
        int espaco = limpo.indexOf(' ');
        return espaco > 0 ? limpo.substring(0, espaco) : limpo;
    }

    static boolean emailValido(String email) {
        if (email == null || email.isBlank()) return false;
        String[] partes = email.trim().split("@", -1);
        if (partes.length != 2 || partes[0].isBlank() || partes[1].isBlank()) return false;
        String dominio = partes[1].toLowerCase(java.util.Locale.ROOT);
        if (!dominio.contains(".") || dominio.startsWith(".") || dominio.endsWith(".")) return false;
        String tld = dominio.substring(dominio.lastIndexOf('.') + 1);
        return !TLDS_RESERVADOS.contains(tld);
    }

    public Map<String, Object> detail(long orderId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, order_id, modality, provider, method, status, raw_status, amount_due_cents, amount_received_cents, change_cents, qr_code, qr_code_base64, ticket_url, external_id, expires_at, note, confirmed_at "
                + "FROM order_payments WHERE order_id = ?", orderId);
        if (rows.isEmpty()) return null;
        Map<String, Object> value = new LinkedHashMap<>(rows.getFirst());
        value.remove("qr_code_base64");
        value.put("has_qr_image", rows.getFirst().get("qr_code_base64") != null);
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("id", value.get("id"));
        image.put("qr_code", value.get("qr_code"));
        image.put("qr_code_base64", rows.getFirst().get("qr_code_base64"));
        image.put("ticket_url", value.get("ticket_url"));
        value.put("image", image);
        return value;
    }
}
