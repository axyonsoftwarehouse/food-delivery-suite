package com.foodie.api.subscriptions;

import com.foodie.api.payments.PaymentGateway;
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
        if (found.isEmpty()) return Optional.empty();
        SubscriptionChargeRepository.Charge charge = found.get();
        // Reenvio de uma cobrança já resolvida: responde e não consulta o provedor de novo.
        if ("paid".equals(charge.status())) return Optional.of(Map.of("ok", true, "already", "paid"));
        // Cobrança de assinatura sem conta configurada: não dá para consultar — responde 200 e ignora.
        if (!account.enabled()) {
            log.warn("Webhook de assinatura {} ignorado: cobrança de assinatura não configurada", chargeId);
            return Optional.of(Map.of("ok", true, "ignored", true));
        }
        PaymentGateway.Charge fetched;
        try {
            fetched = gateways.resolve(provider).fetch(account.credentials(), chargeId);
        } catch (RuntimeException error) {
            log.warn("Webhook de assinatura {} ignorado: a cobrança não pôde ser consultada ({})", chargeId, error.getMessage());
            return Optional.of(Map.of("ok", true, "ignored", true));
        }
        if (fetched == null) return Optional.of(Map.of("ok", true, "ignored", true));
        if ("paid".equals(fetched.status())) return Optional.of(billing.applyPaid(provider, chargeId, fetched));
        if ("expired".equals(fetched.status()) || "cancelled".equals(fetched.status())) {
            charges.markStatus(charge.id(), fetched.status(), fetched.rawStatus());
            return Optional.of(Map.of("ok", true, "status", fetched.status()));
        }
        return Optional.of(Map.of("ok", true, "already", "pending"));
    }
}
