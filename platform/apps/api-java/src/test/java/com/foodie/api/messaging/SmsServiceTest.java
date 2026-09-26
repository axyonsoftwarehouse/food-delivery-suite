package com.foodie.api.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;

class SmsServiceTest {
    private static SmsSender sender(String provider) {
        SmsSender sender = mock(SmsSender.class);
        when(sender.provider()).thenReturn(provider);
        return sender;
    }

    @Test
    void localSenderIsUsedByDefault() {
        SmsSender local = sender("local");
        SmsService service = new SmsService(List.of(local), "local");
        service.send("+5585999999999", "código 123456");
        verify(local).send("+5585999999999", "código 123456");
        assertThat(service.provider()).isEqualTo("local");
    }

    @Test
    void unknownProviderFails() {
        SmsSender local = sender("local");
        SmsService service = new SmsService(List.of(local), "twilio");
        assertThatThrownBy(() -> service.send("+5585999999999", "código"))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).status()).isEqualTo(500));
    }
}
