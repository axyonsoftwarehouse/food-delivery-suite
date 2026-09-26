package com.foodie.api.payments;

import com.foodie.api.ApiException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Descobre os provedores de pagamento disponíveis e resolve o provedor a usar. */
@Service
public class PaymentGatewayRegistry {
    private final Map<String, PaymentGateway> gateways;
    private final String defaultProvider;

    public PaymentGatewayRegistry(List<PaymentGateway> gateways,
                                  @Value("${app.payments.default-provider:mercadopago}") String defaultProvider) {
        this.gateways = gateways.stream().collect(Collectors.toUnmodifiableMap(PaymentGateway::provider, gateway -> gateway));
        this.defaultProvider = defaultProvider;
    }

    public PaymentGateway resolve(String provider) {
        String key = provider == null || provider.isBlank() ? defaultProvider : provider;
        PaymentGateway gateway = gateways.get(key);
        if (gateway == null) throw new ApiException(400, "Provedor de pagamento não disponível");
        return gateway;
    }

    public List<String> providers() {
        return gateways.keySet().stream().sorted().toList();
    }

    public String defaultProvider() {
        return defaultProvider;
    }
}
