package com.foodie.api.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

class PrivacyServiceTest {
    private final JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
    private final AuthRepository repository = Mockito.mock(AuthRepository.class);
    private final PasswordVerifier passwords = new PasswordVerifier();
    private final MailService mail = Mockito.mock(MailService.class);
    private final PrivacyService service = new PrivacyService(jdbc, repository, passwords, mail);
    private final User customer = new User(7, "Cliente", "cliente@demo.local", "customer", null);

    @SuppressWarnings("unchecked")
    private void passwordLogin(boolean value, String storedHash) {
        when(jdbc.query(eq("SELECT social_provider IS NULL AND email NOT LIKE ? FROM users WHERE id = ?"),
            any(ResultSetExtractor.class), anyString(), anyLong())).thenReturn(value);
        when(jdbc.query(eq("SELECT password_hash FROM users WHERE id = ?"), any(ResultSetExtractor.class), anyLong()))
            .thenReturn(storedHash);
    }

    @Test
    void onlyCustomersDeleteTheirOwnAccount() {
        var courier = new User(8, "Entregador", "entregador@demo.local", "courier", 1L);
        assertEquals(403, assertThrows(ApiException.class, () -> service.confirmDeletion(courier, "EXCLUIR", null)).status());
        assertEquals(403, assertThrows(ApiException.class, () -> service.deleteAccount(courier)).status());
    }

    @Test
    void confirmationWordIsRequired() {
        assertEquals(400, assertThrows(ApiException.class, () -> service.confirmDeletion(customer, "excluir", null)).status());
    }

    @Test
    void passwordAccountMustConfirmThePasswordAndFailuresAreCounted() {
        passwordLogin(true, passwords.hash("senha-certa-123"));

        assertEquals(403, assertThrows(ApiException.class, () -> service.confirmDeletion(customer, "EXCLUIR", "errada")).status());
        verify(repository).registerLoginFailure(anyString(), anyInt());

        assertDoesNotThrow(() -> service.confirmDeletion(customer, "EXCLUIR", "senha-certa-123"));
        verify(repository).clearLoginFailures(anyString());
    }

    @Test
    void lockedOutAfterTooManyWrongPasswords() {
        passwordLogin(true, passwords.hash("senha-certa-123"));
        when(repository.isLoginLimited(anyString())).thenReturn(true);

        assertEquals(429, assertThrows(ApiException.class,
            () -> service.confirmDeletion(customer, "EXCLUIR", "senha-certa-123")).status());
    }

    @Test
    void socialOrPhoneAccountConfirmsWithTheWordOnly() {
        passwordLogin(false, null);

        assertDoesNotThrow(() -> service.confirmDeletion(customer, "EXCLUIR", null));
        verify(repository, never()).registerLoginFailure(anyString(), anyInt());
    }

    @Test
    void deletionNoticeSkipsPhoneAccountsAndNeverFailsTheDeletion() {
        service.sendDeletionNotice(null);
        verify(mail, never()).send(anyString(), anyString(), anyString());

        doThrow(new IllegalStateException("smtp fora")).when(mail).send(anyString(), anyString(), anyString());
        assertDoesNotThrow(() -> service.sendDeletionNotice("cliente@demo.local"));
    }
}
