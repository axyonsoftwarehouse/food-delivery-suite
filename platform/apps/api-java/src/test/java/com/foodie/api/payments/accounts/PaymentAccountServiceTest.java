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
import java.util.List;
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
    final PaymentAccountService service = new PaymentAccountService(repo, oauth, cipher, audit, Clock.fixed(NOW, ZoneOffset.UTC), false);
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
        PaymentAccountService semChave = new PaymentAccountService(repo, oauth, new TokenCipher(""), audit, Clock.fixed(NOW, ZoneOffset.UTC), false);
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
        when(oauth.accountInfo("APP_USR-loja")).thenReturn(new MercadoPagoOAuthClient.AccountInfo("TESTUSER4062", false, true));
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

    private PaymentAccountService strictService() {
        return new PaymentAccountService(repo, oauth, cipher, audit, Clock.fixed(NOW, ZoneOffset.UTC), true);
    }

    private void stubExchange(boolean testUser) {
        when(repo.findStateForUpdate(PaymentAccountService.sha256Hex("st"))).thenReturn(Optional.of(
            new PaymentAccountRepository.OAuthState(3, 5, cipher.encrypt("verificador"), NOW.plusSeconds(300), null)));
        when(oauth.exchangeCode("TG-codigo", "verificador")).thenReturn(
            new MercadoPagoOAuthClient.OAuthTokens("APP_USR-loja", "TG-loja", "APP_USR-pk", "91587907", 15552000));
        when(oauth.accountInfo("APP_USR-loja")).thenReturn(new MercadoPagoOAuthClient.AccountInfo("TESTUSER4062", testUser, true));
        when(repo.userName(5)).thenReturn("Dona da Cantina");
    }

    @Test
    void testEnvironmentRefusesARealAccountAndStoresNothing() {
        stubExchange(false);
        assertEquals("conta_real", strictService().completeConnection("TG-codigo", "st", null));
        verify(repo, never()).upsertConnected(anyLong(), anyString(), anyString(), any(), any(), anyString(), anyString(), any(), anyLong());
        verify(audit, never()).record(any(User.class), eq("payment_account.connect"), anyString(), anyLong(), anyString());
    }

    @Test
    void testEnvironmentWithoutAnAnswerFromUsersMeIsAFailureNotARealAccount() {
        // Sem resposta do /users/me nao se sabe se a conta e de teste: nao grava nada, mas o motivo e
        // "falha" (tente de novo), nao "conta_real" (que mandaria a loja trocar de conta a toa).
        stubExchange(false);
        when(oauth.accountInfo("APP_USR-loja")).thenReturn(new MercadoPagoOAuthClient.AccountInfo(null, false, false));
        assertEquals("falha", strictService().completeConnection("TG-codigo", "st", null));
        verify(repo, never()).upsertConnected(anyLong(), anyString(), anyString(), any(), any(), anyString(), anyString(), any(), anyLong());
    }

    @Test
    void testEnvironmentAcceptsATestUser() {
        stubExchange(true);
        assertEquals("conectado", strictService().completeConnection("TG-codigo", "st", null));
        verify(repo).upsertConnected(eq(3L), eq("mercadopago"), eq("91587907"), eq("TESTUSER4062"), eq("APP_USR-pk"),
            anyString(), anyString(), eq(NOW.plusSeconds(15552000)), eq(5L));
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

    @Test
    void credentialsComeDecryptedForAConnectedStore() {
        when(repo.findByRestaurant(3)).thenReturn(Optional.of(connected(NOW.plusSeconds(86400 * 100))));
        MerchantCredentials creds = service.credentialsFor(3).orElseThrow();
        assertEquals(9, creds.accountId());
        assertEquals(3, creds.restaurantId());
        assertEquals("APP_USR-loja", creds.accessToken());
        assertEquals("APP_USR-pk", creds.publicKey());
        assertEquals("3588446200", creds.providerUserId());
        verify(oauth, never()).refresh(anyString());
    }

    @Test
    void tokenCloseToExpiringIsRenewed() {
        PaymentAccountRepository.Account account = connected(NOW.plusSeconds(86400 * 6));
        when(repo.findById(9)).thenReturn(Optional.of(account));
        when(repo.findByIdForUpdate(9)).thenReturn(Optional.of(account));
        when(oauth.refresh("TG-loja")).thenReturn(Optional.of(
            new MercadoPagoOAuthClient.OAuthTokens("APP_USR-novo", "TG-novo", null, "3588446200", 15552000)));

        assertEquals("APP_USR-novo", service.credentialsForAccount(9).orElseThrow().accessToken());

        ArgumentCaptor<String> access = ArgumentCaptor.forClass(String.class);
        verify(repo).updateTokens(eq(9L), access.capture(), anyString(), isNull(), eq(NOW.plusSeconds(15552000)));
        assertEquals("APP_USR-novo", cipher.decrypt(access.getValue()));
    }

    @Test
    void refusedRenewalMarksNeedsReconnect() {
        PaymentAccountRepository.Account account = connected(NOW.plusSeconds(3600));
        when(repo.findByRestaurant(3)).thenReturn(Optional.of(account));
        when(repo.findByIdForUpdate(9)).thenReturn(Optional.of(account));
        when(oauth.refresh("TG-loja")).thenReturn(Optional.empty());
        assertTrue(service.credentialsFor(3).isEmpty());
        verify(repo).markNeedsReconnect(9);
    }

    @Test
    void renewalAlreadyDoneByAnotherRequestIsReusedWithoutCallingTheProvider() {
        // Duas requisicoes viram o token perto de vencer. A primeira renovou; a segunda, ao travar a linha,
        // encontra o refresh token novo e nao pode gastar o antigo (o Mercado Pago o recusaria e a loja
        // cairia em "precisa reconectar" sem motivo).
        PaymentAccountRepository.Account stale = connected(NOW.plusSeconds(86400 * 6));
        PaymentAccountRepository.Account renewed = new PaymentAccountRepository.Account(9, 3, "mercadopago", "3588446200", "TESTUSER4062",
            "APP_USR-pk", cipher.encrypt("APP_USR-novo"), cipher.encrypt("TG-novo"), NOW.plusSeconds(15552000), "connected", NOW.minusSeconds(3600), null);
        when(repo.findById(9)).thenReturn(Optional.of(stale));
        when(repo.findByIdForUpdate(9)).thenReturn(Optional.of(renewed));

        assertEquals("APP_USR-novo", service.credentialsForAccount(9).orElseThrow().accessToken());

        verify(oauth, never()).refresh(anyString());
        verify(repo, never()).updateTokens(anyLong(), anyString(), anyString(), any(), any());
        verify(repo, never()).markNeedsReconnect(anyLong());
    }

    @Test
    void renewalWithTheSameRefreshTokenButAlreadyFarFromExpiringIsNotRepeated() {
        PaymentAccountRepository.Account stale = connected(NOW.plusSeconds(86400 * 6));
        PaymentAccountRepository.Account renewed = new PaymentAccountRepository.Account(9, 3, "mercadopago", "3588446200", "TESTUSER4062",
            "APP_USR-pk", cipher.encrypt("APP_USR-novo"), stale.refreshTokenEnc(), NOW.plusSeconds(15552000), "connected", NOW.minusSeconds(3600), null);
        when(repo.findById(9)).thenReturn(Optional.of(stale));
        when(repo.findByIdForUpdate(9)).thenReturn(Optional.of(renewed));

        assertEquals("APP_USR-novo", service.credentialsForAccount(9).orElseThrow().accessToken());
        verify(oauth, never()).refresh(anyString());
    }

    @Test
    void accountDisconnectedMeanwhileHasNoCredentials() {
        PaymentAccountRepository.Account stale = connected(NOW.plusSeconds(86400 * 6));
        PaymentAccountRepository.Account gone = new PaymentAccountRepository.Account(9, 3, "mercadopago", "3588446200", null, "APP_USR-pk",
            null, null, null, "disconnected", NOW.minusSeconds(3600), NOW);
        when(repo.findById(9)).thenReturn(Optional.of(stale));
        when(repo.findByIdForUpdate(9)).thenReturn(Optional.of(gone));

        assertTrue(service.credentialsForAccount(9).isEmpty());
        verify(oauth, never()).refresh(anyString());
    }

    /** Gerenciador de transacao de mentira: registra o que o TransactionTemplate pediu. */
    static final class RecordingTransactions implements org.springframework.transaction.PlatformTransactionManager {
        final List<Integer> propagations = new java.util.ArrayList<>();
        int commits;
        int rollbacks;

        @Override
        public org.springframework.transaction.TransactionStatus getTransaction(org.springframework.transaction.TransactionDefinition definition) {
            propagations.add(definition.getPropagationBehavior());
            return new org.springframework.transaction.support.SimpleTransactionStatus();
        }

        @Override
        public void commit(org.springframework.transaction.TransactionStatus status) { commits++; }

        @Override
        public void rollback(org.springframework.transaction.TransactionStatus status) { rollbacks++; }
    }

    @Test
    void renewalRunsInItsOwnCommittedTransaction() {
        // O refresh token e de uso unico: se a renovacao ficasse na transacao de quem chamou (cobranca,
        // webhook, estorno) e essa falhasse depois, o rollback apagaria o token novo e o banco ficaria com
        // o ja gasto. Por isso a renovacao abre uma transacao nova (REQUIRES_NEW) e confirma sozinha.
        RecordingTransactions transactions = new RecordingTransactions();
        PaymentAccountService isolated = new PaymentAccountService(repo, oauth, cipher, audit, Clock.fixed(NOW, ZoneOffset.UTC), false,
            PaymentAccountService.renewalTransaction(transactions));
        PaymentAccountRepository.Account account = connected(NOW.plusSeconds(86400 * 6));
        when(repo.findById(9)).thenReturn(Optional.of(account));
        when(repo.findByIdForUpdate(9)).thenReturn(Optional.of(account));
        when(oauth.refresh("TG-loja")).thenReturn(Optional.of(
            new MercadoPagoOAuthClient.OAuthTokens("APP_USR-novo", "TG-novo", null, "3588446200", 15552000)));

        assertEquals("APP_USR-novo", isolated.credentialsForAccount(9).orElseThrow().accessToken());

        assertEquals(List.of(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW), transactions.propagations);
        assertEquals(1, transactions.commits);
        verify(repo).findByIdForUpdate(9);
        verify(repo).updateTokens(eq(9L), anyString(), anyString(), isNull(), eq(NOW.plusSeconds(15552000)));
    }

    @Test
    void tokenFarFromExpiringNeedsNoTransactionNorLock() {
        RecordingTransactions transactions = new RecordingTransactions();
        PaymentAccountService isolated = new PaymentAccountService(repo, oauth, cipher, audit, Clock.fixed(NOW, ZoneOffset.UTC), false,
            PaymentAccountService.renewalTransaction(transactions));
        when(repo.findById(9)).thenReturn(Optional.of(connected(NOW.plusSeconds(86400 * 100))));

        assertEquals("APP_USR-loja", isolated.credentialsForAccount(9).orElseThrow().accessToken());
        assertTrue(transactions.propagations.isEmpty());
        verify(repo, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void storeNotConnectedHasNoCredentials() {
        PaymentAccountRepository.Account reconnect = new PaymentAccountRepository.Account(9, 3, "mercadopago", "3588446200", null, "APP_USR-pk",
            null, null, null, "needs_reconnect", NOW, null);
        when(repo.findByRestaurant(3)).thenReturn(Optional.of(reconnect));
        assertTrue(service.credentialsFor(3).isEmpty());
        assertTrue(service.publicKeyFor(3).isEmpty());
        when(repo.findByRestaurant(4)).thenReturn(Optional.empty());
        assertTrue(service.credentialsFor(4).isEmpty());
    }

    @Test
    void providerUserMapsToEveryConnectedStore() {
        when(repo.findConnectedByProviderUser("mercadopago", "3588446200")).thenReturn(List.of(connected(NOW.plusSeconds(86400 * 100))));
        List<MerchantCredentials> creds = service.credentialsForProviderUser("3588446200");
        assertEquals(1, creds.size());
        assertEquals(3, creds.getFirst().restaurantId());
        when(repo.markNeedsReconnectByProviderUser("mercadopago", "3588446200")).thenReturn(1);
        assertEquals(1, service.markNeedsReconnect("3588446200"));
    }
}
