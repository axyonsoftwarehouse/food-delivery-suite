package com.foodie.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foodie.api.ApiException;
import org.junit.jupiter.api.Test;

class GoogleIdentityServiceTest {
    @Test
    void disabledWithoutClientId() {
        GoogleIdentityService service = new GoogleIdentityService("", "https://oauth2.googleapis.com");
        assertThat(service.configured()).isFalse();
        assertThatThrownBy(() -> service.verify("id-token"))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).status()).isEqualTo(503));
    }

    @Test
    void configuredWhenClientIdPresent() {
        GoogleIdentityService service = new GoogleIdentityService("client.apps.googleusercontent.com", "https://oauth2.googleapis.com");
        assertThat(service.configured()).isTrue();
    }
}
