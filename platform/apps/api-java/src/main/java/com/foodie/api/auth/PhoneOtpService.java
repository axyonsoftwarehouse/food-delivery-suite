package com.foodie.api.auth;

import com.foodie.api.ApiException;
import com.foodie.api.messaging.SmsService;
import java.security.SecureRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Login por telefone via código OTP. O envio é delegado ao provedor de SMS configurado. */
@Service
public class PhoneOtpService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthRepository repository;
    private final PasswordVerifier passwords;
    private final SmsService sms;
    private final int ttlMinutes;
    private final int maxAttempts;

    public PhoneOtpService(AuthRepository repository, PasswordVerifier passwords, SmsService sms,
                           @Value("${app.auth.otp.ttl-minutes:10}") int ttlMinutes,
                           @Value("${app.auth.otp.max-attempts:5}") int maxAttempts) {
        this.repository = repository;
        this.passwords = passwords;
        this.sms = sms;
        this.ttlMinutes = ttlMinutes;
        this.maxAttempts = maxAttempts;
    }

    public void request(String phone) {
        String normalized = normalize(phone);
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        repository.replaceOtp(normalized, Tokens.hash(code), ttlMinutes);
        sms.send(normalized, "Foodie: seu código de acesso é " + code);
    }

    public AuthService.Login verify(String phone, String code, String name) {
        String normalized = normalize(phone);
        if (code == null || !code.matches("\\d{4,8}")) throw new ApiException(400, "Código inválido");
        if (!repository.consumeOtp(normalized, Tokens.hash(code), maxAttempts)) {
            throw new ApiException(401, "Código inválido ou expirado");
        }
        User user = repository.findByPhone(normalized).orElseGet(() -> {
            String displayName = name == null || name.isBlank()
                ? "Cliente " + normalized.substring(Math.max(0, normalized.length() - 4))
                : name.strip();
            return repository.createCustomerWithPhone(displayName, normalized, passwords.hash(Tokens.random()));
        });
        repository.markEmailVerified(user.id());
        String token = Tokens.random();
        repository.createSession(Tokens.hash(token), user.id());
        return new AuthService.Login(user, token);
    }

    private static String normalize(String phone) {
        if (phone == null) throw new ApiException(400, "Telefone obrigatório");
        String digits = phone.replaceAll("[^0-9+]", "");
        if (digits.length() < 8 || digits.length() > 20) throw new ApiException(400, "Telefone inválido");
        return digits;
    }
}
