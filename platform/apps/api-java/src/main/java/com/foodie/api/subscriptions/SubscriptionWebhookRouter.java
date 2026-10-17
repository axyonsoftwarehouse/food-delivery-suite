package com.foodie.api.subscriptions;

import com.foodie.api.payments.PaymentGateway;
import com.foodie.api.ApiException;
import com.foodie.api.payments.PaymentGatewayRegistry;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Roteia a notificação do provedor para a assinatura antes do fluxo de pedidos (spec de 15/10/2026,
 * §6). A assinatura só reconhece a notificação pela cobrança que ela mesma criou (`external_id`); o que
 * não for dela volta vazio e segue o caminho de pedidos. A assinatura do webhook é conferida uma vez,
 * no controller, antes de chegar aqui.
 */
@Service
public class SubscriptionWebhookRouter {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionWebhookRouter.class);

    private final SubscriptionChargeRepository charges;
    private final SubscriptionBillingService billing;
    private final SubscriptionBillingAccount account;
    private final PaymentGatewayRegistry gateways;

    public SubscriptionWebhookRouter(SubscriptionChargeRepository charges, SubscriptionBillingService billing,
                                     SubscriptionBillingAccount account, PaymentGatewayRegistry gateways) {
        this.charges = charges;
        this.billing = billing;
        this.account = account;
        this.gateways = gateways;
    }

    /** Trata a notificação se ela for de uma assinatura; vazio quando não é (o controller segue para pedidos). */
    public Optional<Map<String, Object>> route(String provider, String chargeId, String providerUserId) {
        if (chargeId == null || chargeId.isBlank()) return Optional.empty();
        Optional<SubscriptionChargeRepository.Charge> found = charges.findByExternalId(provider, chargeId);
        if (found.isEmpty()) {
            // Webhook antecipado só existe enquanto há reserva sem external_id. Pedido conhecido nem consulta.
            if (!account.enabled() || charges.isOrderPayment(provider, chargeId) || !charges.hasUnlinkedReservation()) {
                return Optional.empty();
            }
            PaymentGateway.Charge candidate;
            try {
                candidate = gateways.resolve(provider).fetch(account.credentials(), chargeId);
            } catch (RuntimeException error) {
                // Não bloqueia o webhook do pedido: se for a assinatura, o runner reenvia a reserva com a
                // mesma chave de idempotência e recupera a cobrança no provedor.
                log.warn("Webhook {} não classificado pela conta da assinatura; segue para pedidos ({})", chargeId, error.getMessage());
                return Optional.empty();
            }
            if (candidate == null || candidate.externalReference() == null || !candidate.externalReference().matches("sub-[1-9][0-9]*")) return Optional.empty();
            long reservedId;
            try { reservedId = Long.parseLong(candidate.externalReference().substring(4)); }
            catch (NumberFormatException invalid) { throw new ApiException(409, "Referência da assinatura inválida"); }
            return Optional.of(billing.recoverWebhook(provider, chargeId, candidate, reservedId));
        }
        SubscriptionChargeRepository.Charge charge = found.get();
        // Reenvio de uma cobrança já resolvida: responde e não consulta o provedor de novo.
        if ("paid".equals(charge.status()) || "refunded".equals(charge.status())) {
            return Optional.of(Map.of("ok", true, "already", charge.status()));
        }
        // Cobrança conhecida não pode perder a confirmação: não-2xx pede reenvio ao provedor.
        if (!account.enabled()) {
            throw new ApiException(503, "Cobrança de assinatura não configurada para consultar o pagamento");
        }
        PaymentGateway.Charge fetched;
        try {
            fetched = gateways.resolve(provider).fetch(account.credentials(), chargeId);
        } catch (RuntimeException error) {
            log.warn("Webhook de assinatura {} precisa de reenvio: a cobrança não pôde ser consultada ({})", chargeId, error.getMessage());
            throw new ApiException(503, "Não foi possível consultar o pagamento da assinatura. Reenvie a notificação");
        }
        if (fetched == null) throw new ApiException(503, "O provedor não retornou o pagamento da assinatura");
        if ("paid".equals(fetched.status())) return Optional.of(billing.applyPaid(provider, chargeId, fetched));
        if ("expired".equals(fetched.status()) || "cancelled".equals(fetched.status())) {
            charges.markStatus(charge.id(), fetched.status(), fetched.rawStatus());
            return Optional.of(Map.of("ok", true, "status", fetched.status()));
        }
        return Optional.of(Map.of("ok", true, "already", "pending"));
    }
}
