package com.foodie.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import org.junit.jupiter.api.Test;

class EmailVerificationGuardTest {
    private final AuthRepository repository = mock(AuthRepository.class);

    @Test
    void blocksUnverifiedCustomersWhenRequired() {
        EmailVerificationGuard guard = new EmailVerificationGuard(repository, true);
        User customer = new User(7, "Cliente", "cliente@demo.local", "customer", null);
        when(repository.isEmailVerified(7)).thenReturn(false);

        assertThatThrownBy(() -> guard.requireVerified(customer))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).status()).isEqualTo(403));
    }

    @Test
    void allowsVerifiedCustomers() {
        EmailVerificationGuard guard = new EmailVerificationGuard(repository, true);
        User customer = new User(7, "Cliente", "cliente@demo.local", "customer", null);
        when(repository.isEmailVerified(7)).thenReturn(true);

        assertThatCode(() -> guard.requireVerified(customer)).doesNotThrowAnyException();
    }

    @Test
    void ignoresNonCustomerRoles() {
        EmailVerificationGuard guard = new EmailVerificationGuard(repository, true);
        User restaurant = new User(5, "Loja", "loja@demo.local", "restaurant", 7L);

        assertThatCode(() -> guard.requireVerified(restaurant)).doesNotThrowAnyException();
    }

    @Test
    void skipsWhenDisabled() {
        EmailVerificationGuard guard = new EmailVerificationGuard(repository, false);
        User customer = new User(7, "Cliente", "cliente@demo.local", "customer", null);

        assertThatCode(() -> guard.requireVerified(customer)).doesNotThrowAnyException();
    }
}
