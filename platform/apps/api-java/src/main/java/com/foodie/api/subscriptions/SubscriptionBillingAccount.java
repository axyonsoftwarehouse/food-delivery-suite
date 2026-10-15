package com.foodie.api.subscriptions;

import com.foodie.api.ApiException;
import com.foodie.api.payments.accounts.MerchantCredentials;
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
    private final boolean allowRealCharges;
    private final String accessToken;
    private final String publicKey;

    public SubscriptionBillingAccount(@Value("${app.subscriptions.allow-real-charges:false}") boolean allowRealCharges,
                                      @Value("${app.subscriptions.account-access-token:}") String accessToken,
                                      @Value("${app.subscriptions.account-public-key:}") String publicKey) {
        this.allowRealCharges = allowRealCharges;
        this.accessToken = accessToken;
        this.publicKey = publicKey;
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
}
