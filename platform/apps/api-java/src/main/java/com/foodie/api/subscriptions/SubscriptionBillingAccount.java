package com.foodie.api.subscriptions;

import com.foodie.api.ApiException;
import com.foodie.api.payments.accounts.MerchantCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * A conta Mercado Pago que recebe a assinatura da loja (é a receita da Foodie). Credenciais por
 * ambiente, no mesmo espírito da antiga conta global dos pedidos; a conta oficial ainda está em
 * discussão (spec de 15/10/2026, §2.1). Sem a flag ou sem token, a cobrança responde 503 e o job não
 * cobra de verdade.
 */
@Component
public class SubscriptionBillingAccount {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionBillingAccount.class);

    private final boolean allowRealCharges;
    private final String accessToken;
    private final String publicKey;

    public SubscriptionBillingAccount(@Value("${app.subscriptions.allow-real-charges:false}") boolean allowRealCharges,
                                      @Value("${app.subscriptions.account-access-token:}") String accessToken,
                                      @Value("${app.subscriptions.account-public-key:}") String publicKey) {
        this.allowRealCharges = allowRealCharges;
        this.accessToken = accessToken;
        this.publicKey = publicKey;
        if (enabled()) {
            log.info("Cobrança real da assinatura LIGADA (conta da Foodie configurada)");
        } else {
            // Silêncio aqui já escondeu o staging sem cobrança nenhuma (V077/PR #92). O motivo vai para o log.
            log.warn("Cobrança real da assinatura DESLIGADA: {}", disabledReason());
        }
    }

    /** Motivo de a cobrança estar desligada; {@code null} quando ela está ligada. */
    public String disabledReason() {
        if (!allowRealCharges) return "SUBSCRIPTIONS_ALLOW_REAL_CHARGES não está true";
        if (accessToken == null || accessToken.isBlank()) return "SUBSCRIPTIONS_ACCOUNT_ACCESS_TOKEN não configurado";
        return null;
    }

    /** A cobrança real só acontece com a trava ligada e a conta configurada. */
    public boolean enabled() {
        return allowRealCharges && accessToken != null && !accessToken.isBlank();
    }

    /** Credenciais da conta da Foodie para o provedor; a conta de pedido de loja não entra aqui. */
    public MerchantCredentials credentials() {
        if (!enabled()) throw new ApiException(503, "Cobrança de assinatura não configurada");
        return new MerchantCredentials(0, 0, accessToken, publicKey, null);
    }

    /** Public key da conta da Foodie, usada pelo Brick do cartão no navegador. */
    public String publicKey() {
        return publicKey;
    }

    /** O cartão só é oferecido quando a cobrança real e a public key estão configuradas. */
    public boolean cardEnabled() {
        return enabled() && publicKey != null && !publicKey.isBlank();
    }
}
