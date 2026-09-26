package com.foodie.api.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaymentGatewayRegistryTest {
    private static PaymentGateway gateway(String provider) {
        PaymentGateway gateway = mock(PaymentGateway.class);
        when(gateway.provider()).thenReturn(provider);
        return gateway;
    }

    @Test
    void resolvesDefaultWhenProviderOmitted() {
        PaymentGatewayRegistry registry = new PaymentGatewayRegistry(List.of(gateway("mercadopago"), gateway("stripe")), "stripe");
        assertThat(registry.resolve(null).provider()).isEqualTo("stripe");
        assertThat(registry.resolve("  ").provider()).isEqualTo("stripe");
    }

    @Test
    void resolvesExplicitProviderAndLists() {
        PaymentGatewayRegistry registry = new PaymentGatewayRegistry(List.of(gateway("mercadopago"), gateway("stripe")), "mercadopago");
        assertThat(registry.resolve("stripe").provider()).isEqualTo("stripe");
        assertThat(registry.providers()).containsExactly("mercadopago", "stripe");
        assertThat(registry.defaultProvider()).isEqualTo("mercadopago");
    }

    @Test
    void rejectsUnknownProvider() {
        PaymentGatewayRegistry registry = new PaymentGatewayRegistry(List.of(gateway("mercadopago")), "mercadopago");
        assertThatThrownBy(() -> registry.resolve("paypal"))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).status()).isEqualTo(400));
    }
}
