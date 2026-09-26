package com.foodie.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;

class MailRouterTest {
    private static MailProvider provider(String name) {
        MailProvider provider = mock(MailProvider.class);
        when(provider.provider()).thenReturn(name);
        return provider;
    }

    @Test
    void logIsUsedByDefault() {
        MailProvider log = provider("log");
        MailRouter router = new MailRouter(List.of(log, provider("smtp")), "log");
        router.send("cliente@demo.local", "Assunto", "Corpo");
        verify(log).send("cliente@demo.local", "Assunto", "Corpo");
        assertThat(router.provider()).isEqualTo("log");
    }

    @Test
    void smtpIsUsedWhenSelected() {
        MailProvider smtp = provider("smtp");
        MailRouter router = new MailRouter(List.of(provider("log"), smtp), "smtp");
        router.send("cliente@demo.local", "Assunto", "Corpo");
        verify(smtp).send("cliente@demo.local", "Assunto", "Corpo");
    }

    @Test
    void unknownProviderFails() {
        MailRouter router = new MailRouter(List.of(provider("log")), "sendgrid");
        assertThatThrownBy(() -> router.send("a@b", "c", "d"))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).status()).isEqualTo(500));
    }
}
