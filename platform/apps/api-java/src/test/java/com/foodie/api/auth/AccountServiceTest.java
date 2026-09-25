package com.foodie.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

class AccountServiceTest {
    private final AuthRepository repository = Mockito.mock(AuthRepository.class);
    private final MailService mail = Mockito.mock(MailService.class);
    private final AccountService service = new AccountService(repository, new PasswordVerifier(), mail, "http://localhost:3001");

    @Test
    void verifyEmailConsumesSingleUseTokenAndMarksVerified() {
        when(repository.consumeActionToken(anyString(), eq("verify_email"))).thenReturn(Optional.of(7L));

        service.verifyEmail("raw-token");

        verify(repository).markEmailVerified(7L);
    }

    @Test
    void verifyEmailRejectsUnknownOrUsedToken() {
        when(repository.consumeActionToken(anyString(), eq("verify_email"))).thenReturn(Optional.empty());

        assertEquals(400, assertThrows(ApiException.class, () -> service.verifyEmail("raw-token")).status());
    }

    @Test
    void resetPasswordUpdatesHashAndRevokesSessions() {
        when(repository.consumeActionToken(anyString(), eq("reset_password"))).thenReturn(Optional.of(7L));

        service.resetPassword("raw-token", "nova-senha-forte-123");

        verify(repository).updatePassword(eq(7L), anyString());
        verify(repository).deleteSessionsForUser(7L);
    }

    @Test
    void forgotPasswordDoesNotRevealUnknownAccounts() {
        when(repository.isLoginLimited(anyString())).thenReturn(false);
        when(repository.findCredentials("ninguem@demo.local")).thenReturn(Optional.empty());

        service.forgotPassword("ninguem@demo.local", "1.2.3.4");

        verify(repository, never()).createActionToken(anyString(), anyLong(), anyString(), anyInt());
        verify(mail, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void forgotPasswordCreatesTokenForExistingAccount() {
        var user = new User(7, "Cliente", "cliente@demo.local", "customer", null);
        when(repository.isLoginLimited(anyString())).thenReturn(false);
        when(repository.findCredentials("cliente@demo.local")).thenReturn(Optional.of(new AuthRepository.Credentials(user, "hash", false)));

        service.forgotPassword("cliente@demo.local", "1.2.3.4");

        verify(repository).createActionToken(anyString(), eq(7L), eq("reset_password"), anyInt());
        verify(mail).send(eq("cliente@demo.local"), anyString(), anyString());
    }

    @Test
    void forgotPasswordIsRateLimited() {
        when(repository.isLoginLimited(anyString())).thenReturn(true);

        assertEquals(429, assertThrows(ApiException.class, () -> service.forgotPassword("cliente@demo.local", "1.2.3.4")).status());
    }
}
