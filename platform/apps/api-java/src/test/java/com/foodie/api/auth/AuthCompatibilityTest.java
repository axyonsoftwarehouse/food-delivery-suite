package com.foodie.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AuthCompatibilityTest {
    private static final String NODE_HASH = "0123456789abcdef0123456789abcdef:adc5010e087f548f339d088567e44bce910a436f02a07205624fb707b76d10c28f0761563ae311b011f41b0c5859959e6a2147ed0a92599d589b59fb0917c2ee";

    @Test
    void acceptsPasswordHashCreatedByNodeScrypt() {
        PasswordVerifier verifier = new PasswordVerifier();
        assertTrue(verifier.matches("test-password-123", NODE_HASH));
        assertFalse(verifier.matches("wrong-password", NODE_HASH));
        assertFalse(verifier.matches("test-password-123", "broken"));
        String javaHash = verifier.hash("another-test-password");
        assertTrue(javaHash.matches("\\d+:\\d+:\\d+:[0-9a-f]{32}:[0-9a-f]{128}"));
        assertTrue(verifier.matches("another-test-password", javaHash));
        assertFalse(verifier.needsRehash(javaHash));
        assertTrue(verifier.needsRehash(NODE_HASH));
    }

    @Test
    void readsExistingSessionAndEnforcesRole() {
        AuthRepository repository = Mockito.mock(AuthRepository.class);
        AuthService service = new AuthService(repository, new PasswordVerifier());
        String token = "a".repeat(64);
        String nodeHash = "ffe054fe7ae0cb6dc65c3af9b61d5209f439851db43d0ba5997337df154668eb";
        // SHA-256 of the raw 64-character token, as stored by the TypeScript API.
        when(repository.findSessionUser(anyString())).thenReturn(Optional.of(new User(7, "Cliente", "cliente@demo.local", "customer", null)));

        assertEquals(7, service.requireUser(token, "customer").id());
        verify(repository).findSessionUser(eq(nodeHash));
        assertEquals(403, assertThrows(ApiException.class, () -> service.requireUser(token, "admin")).status());
        assertEquals(401, assertThrows(ApiException.class, () -> service.requireUser("invalid", "customer")).status());
    }

    @Test
    void rejectsLimitedAndSuspendedAccountsWithoutCreatingSessions() {
        AuthRepository repository = Mockito.mock(AuthRepository.class);
        AuthService service = new AuthService(repository, new PasswordVerifier());
        when(repository.isLoginLimited(anyString())).thenReturn(true);

        assertEquals(429, assertThrows(ApiException.class, () -> service.login("cliente@demo.local", "wrong-password")).status());

        when(repository.isLoginLimited(anyString())).thenReturn(false);
        var user = new User(7, "Cliente", "cliente@demo.local", "customer", null);
        when(repository.findCredentials("cliente@demo.local"))
            .thenReturn(Optional.of(new AuthRepository.Credentials(user, NODE_HASH, true)));

        assertEquals(401, assertThrows(ApiException.class, () -> service.login("cliente@demo.local", "test-password-123")).status());
        verify(repository).registerLoginFailure(anyString(), anyInt());
    }

    @Test
    void signupDoesNotCreateSessionAndCountsTheSuccessfulAttempt() {
        AuthRepository repository = Mockito.mock(AuthRepository.class);
        AuthService service = new AuthService(repository, new PasswordVerifier());
        var user = new User(7, "Cliente", "cliente@demo.local", "customer", null);
        when(repository.reserveSignup(anyString(), eq(30))).thenReturn(true);
        when(repository.createCustomer(anyString(), anyString(), anyString())).thenReturn(user);

        assertEquals(Optional.of(user), service.signup("Cliente", "cliente@demo.local", "203.0.113.10"));

        verify(repository).reserveSignup(anyString(), eq(30));
        verify(repository, never()).clearLoginFailures(anyString());
        verify(repository, never()).createSession(anyString(), anyLong());
    }

    @Test
    void signupIsBlockedWhenTheOriginReservationIsDenied() {
        AuthRepository repository = Mockito.mock(AuthRepository.class);
        AuthService service = new AuthService(repository, new PasswordVerifier());
        when(repository.reserveSignup(anyString(), eq(30))).thenReturn(false);

        assertEquals(429, assertThrows(ApiException.class,
            () -> service.signup("Cliente", "cliente@demo.local", "203.0.113.10")).status());
        verify(repository, never()).createCustomer(anyString(), anyString(), anyString());
    }

    @Test
    void duplicateSignupReturnsEmptyInsteadOfRevealingTheAccount() {
        AuthRepository repository = Mockito.mock(AuthRepository.class);
        AuthService service = new AuthService(repository, new PasswordVerifier());
        when(repository.reserveSignup(anyString(), eq(30))).thenReturn(true);
        when(repository.createCustomer(anyString(), anyString(), anyString()))
            .thenThrow(new org.springframework.dao.DuplicateKeyException("email"));

        assertEquals(Optional.empty(), service.signup("Cliente", "existe@demo.local", "203.0.113.10"));
    }

    @Test
    void logoutAllRevokesEverySessionForTheAuthenticatedUser() {
        AuthRepository repository = Mockito.mock(AuthRepository.class);
        AuthService service = new AuthService(repository, new PasswordVerifier());
        when(repository.findSessionUser(anyString())).thenReturn(Optional.of(new User(7, "Cliente", "cliente@demo.local", "customer", null)));

        service.logoutAll("a".repeat(64));

        verify(repository).deleteSessionsForUser(7);
    }
}
