package com.foodie.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SocialAuthServiceTest {
    private final AuthRepository repository = mock(AuthRepository.class);
    private final GoogleIdentityService google = mock(GoogleIdentityService.class);
    private final FacebookIdentityService facebook = mock(FacebookIdentityService.class);
    private final SocialAuthService service = new SocialAuthService(repository, new PasswordVerifier(), google, facebook);

    @Test
    void customerLinkedToTheProviderGetsSession() {
        when(facebook.verify("token")).thenReturn(new FacebookIdentityService.Identity("ana@example.com", "Ana"));
        when(repository.findByEmail("ana@example.com")).thenReturn(Optional.of(new User(5, "Ana", "ana@example.com", "customer", null)));
        when(repository.socialProvider(5)).thenReturn(Optional.of("facebook"));
        when(repository.isEmailVerified(5)).thenReturn(true);

        AuthService.Login login = service.facebook("token");

        assertEquals(5, login.user().id());
        verify(repository).createSession(anyString(), eq(5L));
    }

    @Test
    void unverifiedAccountIsClaimedAndOldCredentialsAreInvalidated() {
        when(facebook.verify("token")).thenReturn(new FacebookIdentityService.Identity("ana@example.com", "Ana"));
        when(repository.findByEmail("ana@example.com")).thenReturn(Optional.of(new User(5, "Ana", "ana@example.com", "customer", null)));
        when(repository.socialProvider(5)).thenReturn(Optional.empty());
        when(repository.isEmailVerified(5)).thenReturn(false);
        when(repository.claimSocialAccount(eq(5L), eq("facebook"), anyString())).thenReturn(true);

        service.facebook("token");

        verify(repository).claimSocialAccount(eq(5L), eq("facebook"), anyString());
        verify(repository).createSession(anyString(), eq(5L));
    }

    @Test
    void verifiedPasswordAccountIsNotSilentlyLinked() {
        when(google.verify("id-token")).thenReturn(new GoogleIdentityService.GoogleIdentity("ana@example.com", "Ana", true));
        when(repository.findByEmail("ana@example.com")).thenReturn(Optional.of(new User(5, "Ana", "ana@example.com", "customer", null)));
        when(repository.socialProvider(5)).thenReturn(Optional.empty());
        when(repository.isEmailVerified(5)).thenReturn(true);

        assertEquals(409, assertThrows(ApiException.class, () -> service.google("id-token")).status());
        verify(repository, never()).claimSocialAccount(anyLong(), anyString(), anyString());
        verify(repository, never()).createSession(anyString(), anyLong());
    }

    @Test
    void unverifiedGoogleEmailIsRejected() {
        when(google.verify("id-token")).thenReturn(new GoogleIdentityService.GoogleIdentity("ana@example.com", "Ana", false));

        assertEquals(403, assertThrows(ApiException.class, () -> service.google("id-token")).status());
        verify(repository, never()).findByEmail(anyString());
    }

    @Test
    void operationalAccountCannotUseSocialLogin() {
        when(google.verify("id-token")).thenReturn(new GoogleIdentityService.GoogleIdentity("admin@example.com", "Admin", true));
        when(repository.findByEmail("admin@example.com")).thenReturn(Optional.of(new User(1, "Admin", "admin@example.com", "admin", null)));

        assertEquals(403, assertThrows(ApiException.class, () -> service.google("id-token")).status());
        verify(repository, never()).createSession(anyString(), anyLong());
    }
}
