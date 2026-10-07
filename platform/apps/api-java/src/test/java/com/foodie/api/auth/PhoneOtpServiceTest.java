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
import com.foodie.api.messaging.SmsService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PhoneOtpServiceTest {
    private static final String PHONE = "+5585999999999";

    private final AuthRepository repository = Mockito.mock(AuthRepository.class);
    private final SmsService sms = Mockito.mock(SmsService.class);
    private final PhoneOtpService service = new PhoneOtpService(repository, new PasswordVerifier(), sms, 10, 5);

    @Test
    void sendsCodeAndCountsTheRequest() {
        service.request(PHONE, "203.0.113.9");

        verify(repository).registerLoginFailure(Tokens.hash("otp-send:" + PHONE), PhoneOtpService.SEND_PER_PHONE);
        verify(repository).registerLoginFailure(Tokens.hash("otp-send-ip:203.0.113.9"), PhoneOtpService.SEND_PER_ORIGIN);
        verify(sms).send(eq(PHONE), anyString());
    }

    @Test
    void refusesToSendWhenPhoneIsLimited() {
        when(repository.isLoginLimited(Tokens.hash("otp-send:" + PHONE))).thenReturn(true);

        assertEquals(429, assertThrows(ApiException.class, () -> service.request(PHONE, "203.0.113.9")).status());
        verify(repository, never()).replaceOtp(anyString(), anyString(), anyInt());
        verify(sms, never()).send(anyString(), anyString());
    }

    @Test
    void refusesToSendWhenOriginIsLimited() {
        when(repository.isLoginLimited(Tokens.hash("otp-send-ip:203.0.113.9"))).thenReturn(true);

        assertEquals(429, assertThrows(ApiException.class, () -> service.request(PHONE, "203.0.113.9")).status());
        verify(sms, never()).send(anyString(), anyString());
    }

    @Test
    void wrongCodeCountsAgainstThePhoneAcrossCodes() {
        when(repository.consumeOtp(eq(PHONE), anyString(), eq(5))).thenReturn(false);

        assertEquals(401, assertThrows(ApiException.class, () -> service.verify(PHONE, "123456", null)).status());
        verify(repository).registerLoginFailure(Tokens.hash("otp-verify:" + PHONE), PhoneOtpService.WRONG_CODES_PER_PHONE);
    }

    @Test
    void verificationBlockedAfterTooManyWrongCodes() {
        when(repository.isLoginLimited(Tokens.hash("otp-verify:" + PHONE))).thenReturn(true);

        assertEquals(429, assertThrows(ApiException.class, () -> service.verify(PHONE, "123456", null)).status());
        verify(repository, never()).consumeOtp(anyString(), anyString(), anyInt());
    }

    @Test
    void operationalAccountCannotLoginBySms() {
        when(repository.consumeOtp(eq(PHONE), anyString(), eq(5))).thenReturn(true);
        when(repository.findByPhone(PHONE)).thenReturn(Optional.of(new User(1, "Admin", "admin@example.com", "admin", null)));

        assertEquals(403, assertThrows(ApiException.class, () -> service.verify(PHONE, "123456", null)).status());
        verify(repository, never()).createSession(anyString(), anyLong());
    }

    @Test
    void customerGetsSession() {
        when(repository.consumeOtp(eq(PHONE), anyString(), eq(5))).thenReturn(true);
        when(repository.findByPhone(PHONE)).thenReturn(Optional.of(new User(9, "Cliente", "c@example.com", "customer", null)));

        AuthService.Login login = service.verify(PHONE, "123456", null);

        assertEquals(9, login.user().id());
        verify(repository).clearLoginFailures(Tokens.hash("otp-verify:" + PHONE));
        verify(repository).createSession(anyString(), eq(9L));
    }
}
