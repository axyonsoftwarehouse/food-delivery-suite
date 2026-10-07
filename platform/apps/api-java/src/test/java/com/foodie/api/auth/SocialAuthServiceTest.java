package com.foodie.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SocialAuthServiceTest {
    private final AuthRepository repository = Mockito.mock(AuthRepository.class);
    private final GoogleIdentityService google = Mockito.mock(GoogleIdentityService.class);
    private final FacebookIdentityService facebook = Mockito.mock(FacebookIdentityService.class);
    private final SocialAuthService service = new SocialAuthService(repository, new PasswordVerifier(), google, facebook);

    @Test
    void customerGetsSession() {
        when(facebook.verify("token")).thenReturn(new FacebookIdentityService.Identity("ana@example.com", "Ana"));
        when(repository.findByEmail("ana@example.com")).thenReturn(Optional.of(new User(5, "Ana", "ana@example.com", "customer", null)));

        AuthService.Login login = service.facebook("token");

        assertEquals(5, login.user().id());
        verify(repository).createSession(anyString(), Mockito.eq(5L));
    }

    @Test
    void operationalAccountCannotUseSocialLogin() {
        when(google.verify("id-token")).thenReturn(new GoogleIdentityService.GoogleIdentity("admin@example.com", "Admin", true));
        when(repository.findByEmail("admin@example.com")).thenReturn(Optional.of(new User(1, "Admin", "admin@example.com", "admin", null)));

        assertEquals(403, assertThrows(ApiException.class, () -> service.google("id-token")).status());
        verify(repository, never()).createSession(anyString(), anyLong());
    }
}
