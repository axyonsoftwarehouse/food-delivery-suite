package com.foodie.api.subscriptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.foodie.api.ApiException;
import org.junit.jupiter.api.Test;

/** A conta que recebe a assinatura e a trava de segurança: sem flag e sem token, nada é cobrado. */
class SubscriptionBillingAccountTest {
    @Test
    void disabledWithoutTheFlag() {
        assertFalse(new SubscriptionBillingAccount(false, "token", "pk").enabled());
    }

    @Test
    void disabledWithoutTheToken() {
        assertFalse(new SubscriptionBillingAccount(true, "", "pk").enabled());
    }

    @Test
    void enabledBuildsFoodieCredentials() {
        SubscriptionBillingAccount account = new SubscriptionBillingAccount(true, "token", "pk");

        assertTrue(account.enabled());
        assertEquals("token", account.credentials().accessToken());
        assertEquals("pk", account.credentials().publicKey());
    }

    @Test
    void credentialsWithoutConfigurationIs503() {
        ApiException error = assertThrows(ApiException.class, () -> new SubscriptionBillingAccount(false, "", "").credentials());

        assertEquals(503, error.status());
        assertEquals("Cobrança de assinatura não configurada", error.getMessage());
    }
}
