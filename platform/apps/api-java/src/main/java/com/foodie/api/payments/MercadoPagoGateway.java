package com.foodie.api.payments;

import com.foodie.api.ApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Mercado Pago — Checkout Transparente pela <b>API de Orders</b> ({@code POST /v1/orders}).
 *
 * <p>Esta é a integração da aplicação do Foodie (produto "Checkout Transparente via Orders"). A cobrança
 * antiga ({@code POST /v1/payments}, a "Payments API") é a integração <i>legacy</i> e não é atendida com
 * as credenciais desta aplicação: ela responde {@code 401 Unauthorized use of live credentials} tanto no
 * Pix quanto no cartão — medido em 02/10/2026 com a credencial de teste do painel, enquanto
 * {@code GET /v1/payment_methods} e {@code POST /checkout/preferences} respondem. Pela mesma razão a
 * preferência do Checkout Pro saiu daqui: o produto desta aplicação não é o Pro.
 *
 * <p>O dinheiro anda por aqui, mas <b>os dados do cartão não</b>: o número e o código de segurança viram
 * um {@code token} no navegador (MercadoPago.js + public key) e só o token chega a este serviço.
 *
 * <p>A notificação de mudança de status <b>não</b> se configura nesta classe: a API de Orders recusa
 * {@code notification_url} no corpo da requisição. A URL é registrada no painel do Mercado Pago
 * (Webhooks &gt; Configurar notificações &gt; evento "Order (Mercado Pago)") e chega ao nosso
 * {@code /webhooks/mercadopago}.
 */
@Service
public class MercadoPagoGateway implements PaymentGateway {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MercadoPagoGateway.class);
    private final RestClient client;
    private final String accessToken;
    private final String webhookSecret;

    public MercadoPagoGateway(@Value("${app.mercadopago.access-token:}") String accessToken,
                              @Value("${app.mercadopago.base-url:https://api.mercadopago.com}") String baseUrl,
                              @Value("${app.mercadopago.webhook-secret:}") String webhookSecret) {
        this.accessToken = accessToken;
        this.webhookSecret = webhookSecret;
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public String provider() {
        return "mercadopago";
    }

    @Override
    public Charge create(ChargeRequest request) {
        requireConfigured();
        try {
            return createOrder(request, request.idempotencyKey());
        } catch (RestClientResponseException error) {
            // O provedor guarda a chave de idempotência: se a tentativa anterior falhou (um 4xx), o mesmo
            // valor passa a ser recusado com "X-Idempotency-Key already used" e o pedido ficaria **sem
            // cobrança possível** até a chave expirar. Só nesse caso a segunda tentativa usa chave nova —
            // o que é seguro, porque um 409 de chave usada significa que a tentativa anterior não criou
            // ordem nenhuma (se tivesse criado, o provedor repetiria a resposta, não recusaria).
            if (chaveJaUsada(error)) {
                try {
                    return createOrder(request, request.idempotencyKey() + "-r2");
                } catch (RestClientResponseException segunda) {
                    throw new ApiException(502, "Mercado Pago recusou a cobrança: " + providerMessage(segunda));
                }
            }
            throw new ApiException(502, "Mercado Pago recusou a cobrança: " + providerMessage(error));
        }
    }

    /** O provedor avisou que aquela chave de idempotência já foi usada (e a tentativa anterior falhou). */
    static boolean chaveJaUsada(RestClientResponseException error) {
        if (error.getStatusCode().value() != 409) return false;
        String corpo = error.getResponseBodyAsString();
        return corpo != null && corpo.toLowerCase(java.util.Locale.ROOT).contains("idempotency");
    }

    /**
     * Cria a order com a cobrança dentro ({@code transactions.payments}), no formato da documentação.
     * Pix: {@code payment_method {id: pix, type: bank_transfer}} com o QR na resposta. Cartão:
     * {@code payment_method {id: <bandeira>, type: credit_card, token, installments}}, com o token que o
     * navegador gerou.
     */
    private Charge createOrder(ChargeRequest request, String idempotencyKey) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("type", "online");
        corpo.put("processing_mode", "automatic");
        corpo.put("total_amount", dinheiro(request.amountCents()));
        corpo.put("external_reference", String.valueOf(request.orderId()));

        Map<String, Object> pagador = new LinkedHashMap<>();
        pagador.put("email", request.payerEmail());
        // O Mercado Pago usa o primeiro nome do pagador no resultado predefinido dos testes (APRO, OTHE…).
        if (request.payerFirstName() != null && !request.payerFirstName().isBlank()) {
            pagador.put("first_name", request.payerFirstName());
        }
        corpo.put("payer", pagador);

        Map<String, Object> meio = new LinkedHashMap<>();
        if ("card".equals(request.method())) {
            if (request.paymentMethodId() != null && !request.paymentMethodId().isBlank()) meio.put("id", request.paymentMethodId());
            meio.put("type", "credit_card");
            meio.put("token", request.cardToken());
            if (request.installments() != null) meio.put("installments", request.installments());
        } else {
            meio.put("id", "pix");
            meio.put("type", "bank_transfer");
        }
        Map<String, Object> pagamento = new LinkedHashMap<>();
        pagamento.put("amount", dinheiro(request.amountCents()));
        pagamento.put("payment_method", meio);
        corpo.put("transactions", Map.of("payments", List.of(pagamento)));

        // Sem `notification_url`: a API de Orders recusa campo extra no corpo
        // (`400 unsupported_properties: additionalProperties '$.notification_url' not allowed`).
        // A notificação de orders se configura no painel do Mercado Pago (Webhooks > Configurar
        // notificações > evento "Order (Mercado Pago)"), apontando para o nosso /webhooks/mercadopago.
        return fromOrder(post("/v1/orders", corpo, idempotencyKey));
    }

    /**
     * Consulta a order. Cobranças criadas antes da mudança para a API de Orders guardaram um id de
     * pagamento — para essas, o caminho antigo continua valendo, senão a conciliação de um pedido
     * antigo quebraria.
     */
    @Override
    public Charge fetch(String externalId) {
        requireConfigured();
        try {
            return fromOrder(get("/v1/orders/" + externalId));
        } catch (RestClientResponseException naoEhOrder) {
            try {
                return fromPayment(get("/v1/payments/" + externalId));
            } catch (RestClientResponseException error) {
                throw new ApiException(502, "Mercado Pago recusou a consulta: " + providerMessage(error));
            }
        }
    }

    /**
     * Primeira mensagem útil que o provedor devolveu. Sem isto o atendimento (e quem está na tela) recebe
     * só "recusou a cobrança (400)" e não descobre que o motivo era, por exemplo, o email do pagador.
     */
    static String providerMessage(RestClientResponseException error) {
        String situacao = "HTTP " + error.getStatusCode().value();
        String corpo = error.getResponseBodyAsString();
        if (corpo == null || corpo.isBlank()) return situacao;
        try {
            Map<String, Object> resposta = new ObjectMapper().readValue(corpo, new TypeReference<Map<String, Object>>() {});
            Object mensagem = resposta.get("message");
            if (mensagem != null && !String.valueOf(mensagem).isBlank()) return situacao + " - " + mensagem;
            if (resposta.get("cause") instanceof List<?> causas && !causas.isEmpty() && causas.getFirst() instanceof Map<?, ?> primeira) {
                Object descricao = primeira.get("description");
                if (descricao != null && !String.valueOf(descricao).isBlank()) return situacao + " - " + descricao;
            }
            // A API de Orders erra em `errors[]` (não em `message`/`cause`). Sem isto, um campo recusado
            // virava só "HTTP 400" e o motivo — que estava no corpo — ficava invisível.
            if (resposta.get("errors") instanceof List<?> erros && !erros.isEmpty() && erros.getFirst() instanceof Map<?, ?> erro) {
                String texto = str(erro.get("message"));
                String detalhe = erro.get("details") instanceof List<?> detalhes && !detalhes.isEmpty() ? String.valueOf(detalhes.getFirst()) : null;
                if (texto != null && !texto.isBlank()) {
                    return situacao + " - " + texto + (detalhe == null || detalhe.isBlank() ? "" : " (" + detalhe + ")");
                }
            }
            return situacao;
        } catch (Exception ignorado) {
            return situacao;
        }
    }

    public boolean configured() {
        return accessToken != null && !accessToken.isBlank();
    }

    /**
     * A notificação do Mercado Pago: no Checkout Transparente via Orders o tópico é {@code order} e o id
     * que interessa é o da order; o tópico {@code payment} continua aceito (é o que chega quando a
     * notificação é de um pagamento).
     */
    @Override
    public java.util.Optional<String> webhookChargeId(WebhookRequest request) {
        Map<String, Object> body = request.body();
        String type = firstString(body, "type", "topic");
        if (type != null && !"order".equals(type) && !"payment".equals(type)) return java.util.Optional.empty();
        Object data = body.get("data");
        if (data instanceof Map<?, ?> map && map.get("id") != null) return java.util.Optional.of(String.valueOf(map.get("id")));
        return java.util.Optional.empty();
    }

    @Override
    public boolean verifyWebhook(WebhookRequest request) {
        String dataId = webhookChargeId(request).orElse(null);
        String requestId = header(request.headers(), "x-request-id");
        String ts = null;
        String v1 = null;
        String signature = header(request.headers(), "x-signature");
        if (signature != null) {
            for (String part : signature.split(",")) {
                String[] pair = part.split("=", 2);
                if (pair.length != 2) continue;
                if ("ts".equals(pair[0].trim())) ts = pair[1].trim();
                else if ("v1".equals(pair[0].trim())) v1 = pair[1].trim();
            }
        }
        boolean valida = WebhookVerifier.verify(webhookSecret, dataId, requestId, ts, v1);
        if (!valida) {
            // Notificação recusada é evento de operação: sem isto só se vê "401" e não se sabe se o
            // provedor mudou o formato, se a chave está trocada ou se faltou um header. O manifesto tem
            // apenas ids e horário — o segredo nunca entra no log.
            logger.warn("Webhook do Mercado Pago recusado (401): assinatura={} request-id={} manifesto={}",
                v1 == null ? "ausente" : "presente",
                requestId == null ? "AUSENTE" : "presente",
                WebhookVerifier.manifest(dataId, requestId, ts));
        }
        return valida;
    }

    private static String firstString(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            Object value = body.get(key);
            if (value != null) return String.valueOf(value);
        }
        return null;
    }

    private static String header(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) return entry.getValue();
        }
        return null;
    }

    private void requireConfigured() {
        if (!configured()) throw new ApiException(503, "Pagamento online não configurado: defina MERCADOPAGO_ACCESS_TOKEN");
    }

    /**
     * Converte a order na cobrança do Foodie. Quando existe pagamento dentro da order, é o status dele que
     * vale (o da order é mais grosso); {@code status_detail} fica em {@code raw_status}, que é o que
     * explica a recusa para quem está na tela. O id guardado é o da <b>order</b>, que é o que a
     * notificação e a consulta usam.
     */
    private Charge fromOrder(Map<String, Object> order) {
        Map<String, Object> pagamento = primeiroPagamento(order);
        String status = pagamento != null ? str(pagamento.get("status")) : str(order.get("status"));
        String detalhe = pagamento != null ? str(pagamento.get("status_detail")) : str(order.get("status_detail"));
        long centavos = pagamento != null && pagamento.get("amount") != null
            ? amountCents(pagamento.get("amount"))
            : amountCents(order.get("total_amount"));
        Map<String, Object> meio = pagamento == null ? null : nested(pagamento, "payment_method");
        return new Charge(str(order.get("id")), str(order.get("external_reference")), centavos,
            MercadoPagoStatus.normalize(status), textoOu(detalhe, status),
            meio == null ? null : str(meio.get("qr_code")), meio == null ? null : str(meio.get("qr_code_base64")),
            meio == null ? null : str(meio.get("ticket_url")), instant(order.get("date_of_expiration")));
    }

    /** Caminho antigo (Payments API), só para consultar cobrança criada antes desta mudança. */
    private Charge fromPayment(Map<String, Object> payment) {
        String status = str(payment.get("status"));
        return new Charge(str(payment.get("id")), str(payment.get("external_reference")), amountCents(payment.get("transaction_amount")),
            MercadoPagoStatus.normalize(status), textoOu(str(payment.get("status_detail")), status),
            null, null, null, instant(payment.get("date_of_expiration")));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> primeiroPagamento(Map<String, Object> order) {
        Object transacoes = order.get("transactions");
        if (!(transacoes instanceof Map<?, ?> mapa)) return null;
        Object pagamentos = mapa.get("payments");
        if (pagamentos instanceof List<?> lista && !lista.isEmpty() && lista.getFirst() instanceof Map<?, ?> primeiro) {
            return (Map<String, Object>) primeiro;
        }
        return null;
    }

    private Map<String, Object> get(String path) {
        return client.get().uri(path).header("Authorization", "Bearer " + accessToken)
            .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    private Map<String, Object> post(String path, Map<String, Object> body, String idempotencyKey) {
        return client.post().uri(path)
            .header("Authorization", "Bearer " + accessToken)
            .header("X-Idempotency-Key", idempotencyKey == null || idempotencyKey.isBlank() ? UUID.randomUUID().toString() : idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nested(Map<String, Object> source, String... path) {
        Object current = source;
        for (String key : path) {
            if (!(current instanceof Map<?, ?> map)) return null;
            current = map.get(key);
        }
        return current instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private static String textoOu(String preferido, String alternativa) {
        return preferido == null || preferido.isBlank() ? alternativa : preferido;
    }

    /** O Mercado Pago espera o valor como texto com duas casas ("50.00"). */
    static String dinheiro(long centavos) {
        return String.format(java.util.Locale.ROOT, "%.2f", centavos / 100.0);
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static long amountCents(Object value) {
        if (value instanceof Number number) return Math.round(number.doubleValue() * 100);
        if (value instanceof String text) {
            try { return Math.round(Double.parseDouble(text) * 100); } catch (NumberFormatException ignored) { return 0; }
        }
        return 0;
    }

    private static Instant instant(Object value) {
        if (value == null) return null;
        try { return OffsetDateTime.parse(String.valueOf(value)).toInstant(); } catch (RuntimeException error) { return null; }
    }
}
