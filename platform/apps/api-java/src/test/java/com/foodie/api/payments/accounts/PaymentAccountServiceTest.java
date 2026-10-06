package com.foodie.api.payments.accounts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminAuditService;
import com.foodie.api.auth.User;
import com.foodie.api.payments.TokenCipher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PaymentAccountServiceTest {
    static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    final PaymentAccountRepository repo = mock(PaymentAccountRepository.class);
    final MercadoPagoOAuthClient oauth = mock(MercadoPagoOAuthClient.class);
    final AdminAuditService audit = mock(AdminAuditService.class);
    final TokenCipher cipher = new TokenCipher(Base64.getEncoder().encodeToString(new byte[32]));
    final PaymentAccountService service = new PaymentAccountService(repo, oauth, cipher, audit, Clock.fixed(NOW, ZoneOffset.UTC));
    final User owner = new User(5, "Dona da Cantina", "dona@cantina.com.br", "restaurant", 3L);

    PaymentAccountRepository.Account connected(Instant expiresAt) {
        return new PaymentAccountRepository.Account(9, 3, "mercadopago", "3588446200", "TESTUSER4062", "APP_USR-pk",
            cipher.encrypt("APP_USR-loja"), cipher.encrypt("TG-loja"), expiresAt, "connected", NOW.minusSeconds(3600), null);
    }

    @Test
    void startStoresAHashedStateAndAnEncryptedVerifier() {
        when(oauth.configured()).thenReturn(true);
        when(oauth.authorizationUrl(anyString(), anyString())).thenReturn("https://auth.mercadopago.com.br/authorization?x");

        assertEquals("https://auth.mercadopago.com.br/authorization?x", service.startConnection(owner));

        ArgumentCaptor<String> state = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> challenge = ArgumentCaptor.forClass(String.class);
        verify(oauth).authorizationUrl(state.capture(), challenge.capture());
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> verifier = ArgumentCaptor.forClass(String.class);
        verify(repo).insertState(hash.capture(), eq(3L), eq(5L), verifier.capture(), eq(NOW.plusSeconds(600)));
        assertEquals(PaymentAccountService.sha256Hex(state.getValue()), hash.getValue());
        assertEquals(PaymentAccountService.s256(cipher.decrypt(verifier.getValue())), challenge.getValue());
    }

    @Test
    void startWithoutConfigurationIs503() {
        when(oauth.configured()).thenReturn(false);
        assertEquals(503, assertThrows(ApiException.class, () -> service.startConnection(owner)).status());
        PaymentAccountService semChave = new PaymentAccountService(repo, oauth, new TokenCipher(""), audit, Clock.fixed(NOW, ZoneOffset.UTC));
        when(oauth.configured()).thenReturn(true);
        assertEquals(503, assertThrows(ApiException.class, () -> semChave.startConnection(owner)).status());
        verify(repo, never()).insertState(anyString(), anyLong(), anyLong(), anyString(), any());
    }

    @Test
    void ownerWithoutStoreIs403() {
        User semLoja = new User(5, "X", "x@y.com", "restaurant", null);
        assertEquals(403, assertThrows(ApiException.class, () -> service.startConnection(semLoja)).status());
    }

    @Test
    void completeExchangesTheCodeAndStoresEncryptedTokens() {
        when(repo.findStateForUpdate(PaymentAccountService.sha256Hex("st"))).thenReturn(Optional.of(
            new PaymentAccountRepository.OAuthState(3, 5, cipher.encrypt("verificador"), NOW.plusSeconds(300), null)));
        when(oauth.exchangeCode("TG-codigo", "verificador")).thenReturn(
            new MercadoPagoOAuthClient.OAuthTokens("APP_USR-loja", "TG-loja", "APP_USR-pk", "3588446200", 15552000));
        when(oauth.nickname("APP_USR-loja")).thenReturn("TESTUSER4062");
        when(repo.userName(5)).thenReturn("Dona da Cantina");

        assertEquals("conectado", service.completeConnection("TG-codigo", "st", null));

        verify(repo).markStateUsed(PaymentAccountService.sha256Hex("st"));
        ArgumentCaptor<String> access = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> refresh = ArgumentCaptor.forClass(String.class);
        verify(repo).upsertConnected(eq(3L), eq("mercadopago"), eq("3588446200"), eq("TESTUSER4062"), eq("APP_USR-pk"),
            access.capture(), refresh.capture(), eq(NOW.plusSeconds(15552000)), eq(5L));
        assertEquals("APP_USR-loja", cipher.decrypt(access.getValue()));
        assertEquals("TG-loja", cipher.decrypt(refresh.getValue()));
        verify(audit).record(any(User.class), eq("payment_account.connect"), eq("restaurant"), eq(3L), anyString());
    }

    @Test
    void stateProblemsAreReportedAndNeverExchange() {
        String hash = PaymentAccountService.sha256Hex("st");
        assertEquals("invalido", service.completeConnection("c", "", null));
        when(repo.findStateForUpdate(hash)).thenReturn(Optional.empty());
        assertEquals("invalido", service.completeConnection("c", "st", null));
        when(repo.findStateForUpdate(hash)).thenReturn(Optional.of(new PaymentAccountRepository.OAuthState(3, 5, "v1:x", NOW.plusSeconds(60), NOW)));
        assertEquals("invalido", service.completeConnection("c", "st", null));
        when(repo.findStateForUpdate(hash)).thenReturn(Optional.of(new PaymentAccountRepository.OAuthState(3, 5, "v1:x", NOW.minusSeconds(1), null)));
        assertEquals("expirado", service.completeConnection("c", "st", null));
        verify(oauth, never()).exchangeCode(anyString(), anyString());
    }

    @Test
    void deniedAuthorizationConsumesTheState() {
        when(repo.findStateForUpdate(PaymentAccountService.sha256Hex("st"))).thenReturn(Optional.of(
            new PaymentAccountRepository.OAuthState(3, 5, cipher.encrypt("v"), NOW.plusSeconds(300), null)));
        assertEquals("negado", service.completeConnection(null, "st", "access_denied"));
        verify(repo).markStateUsed(PaymentAccountService.sha256Hex("st"));
        verify(oauth, never()).exchangeCode(anyString(), anyString());
    }

    @Test
    void failedExchangeIsReportedNotThrown() {
        when(repo.findStateForUpdate(PaymentAccountService.sha256Hex("st"))).thenReturn(Optional.of(
            new PaymentAccountRepository.OAuthState(3, 5, cipher.encrypt("v"), NOW.plusSeconds(300), null)));
        when(oauth.exchangeCode("c", "v")).thenThrow(new ApiException(502, "Mercado Pago recusou a vinculação: HTTP 400"));
        assertEquals("falha", service.completeConnection("c", "st", null));
    }

    @Test
    void statusNeverExposesTokens() {
        when(repo.findByRestaurant(3)).thenReturn(Optional.of(connected(NOW.plusSeconds(86400 * 100))));
        Map<String, Object> status = service.status(3);
        assertEquals("connected", status.get("status"));
        assertEquals("TESTUSER4062", status.get("nickname"));
        assertEquals("3588446200", status.get("providerUserId"));
        assertFalse(status.toString().contains("APP_USR-loja"));
        assertFalse(status.toString().contains("v1:"));

        when(repo.findByRestaurant(4)).thenReturn(Optional.empty());
        assertEquals("not_connected", service.status(4).get("status"));
    }

    @Test
    void ownerDisconnectsAndIsAudited() {
        when(repo.findByRestaurant(3)).thenReturn(Optional.of(connected(NOW.plusSeconds(86400))));
        service.disconnectByOwner(owner);
        verify(repo).disconnect(9, 5, null);
        verify(audit).record(eq(owner), eq("payment_account.disconnect"), eq("restaurant"), eq(3L), anyString());
    }

    @Test
    void disconnectingWithoutAConnectedAccountIs409() {
        when(repo.findByRestaurant(3)).thenReturn(Optional.empty());
        assertEquals(409, assertThrows(ApiException.class, () -> service.disconnect(3, 1, "loja pediu ao suporte")).status());
    }
}
