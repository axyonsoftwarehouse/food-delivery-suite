package com.foodie.api.auth;

import com.foodie.api.ApiException;
import com.foodie.api.messaging.SmsService;
import java.security.SecureRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Login por telefone via código OTP. O envio é delegado ao provedor de SMS configurado.
 *
 * <p>Limites (janelas de 15 minutos, na mesma tabela do limite de login): cada pedido de código gera um
 * código novo com tentativas zeradas, então sem limite de envio dava para pedir código e chutar
 * indefinidamente — e disparar SMS pagos sem parar. Os erros de verificação também contam por telefone,
 * somando todos os códigos emitidos na janela.
 */
@Service
public class PhoneOtpService {
    static final int SEND_PER_PHONE = 5;
    static final int SEND_PER_ORIGIN = 20;
    static final int WRONG_CODES_PER_PHONE = 10;
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
        request(phone, null);
    }

    public void request(String phone, String origin) {
        String normalized = normalize(phone);
        String phoneKey = Tokens.hash("otp-send:" + normalized);
        String originKey = origin == null ? null : Tokens.hash("otp-send-ip:" + origin);
        if (repository.isLoginLimited(phoneKey) || originKey != null && repository.isLoginLimited(originKey)) {
            throw new ApiException(429, "Muitos códigos solicitados. Aguarde antes de pedir outro");
        }
        repository.registerLoginFailure(phoneKey, SEND_PER_PHONE);
        if (originKey != null) repository.registerLoginFailure(originKey, SEND_PER_ORIGIN);
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        repository.replaceOtp(normalized, Tokens.hash(code), ttlMinutes);
        sms.send(normalized, "Foodie: seu código de acesso é " + code);
    }

    public AuthService.Login verify(String phone, String code, String name) {
        String normalized = normalize(phone);
        if (code == null || !code.matches("\\d{4,8}")) throw new ApiException(400, "Código inválido");
        String wrongKey = Tokens.hash("otp-verify:" + normalized);
        if (repository.isLoginLimited(wrongKey)) {
            throw new ApiException(429, "Muitas tentativas. Aguarde antes de tentar novamente");
        }
        if (!repository.consumeOtp(normalized, Tokens.hash(code), maxAttempts)) {
            repository.registerLoginFailure(wrongKey, WRONG_CODES_PER_PHONE);
            throw new ApiException(401, "Código inválido ou expirado");
        }
        repository.clearLoginFailures(wrongKey);
        User user = repository.findByPhone(normalized).orElseGet(() -> {
            String displayName = name == null || name.isBlank()
                ? "Cliente " + normalized.substring(Math.max(0, normalized.length() - 4))
                : name.strip();
            return repository.createCustomerWithPhone(displayName, normalized, passwords.hash(Tokens.random()));
        });
        // Telefone de conta operacional (admin, loja, entregador) não abre sessão por SMS: essas contas
        // entram com email e senha.
        if (!"customer".equals(user.role())) throw new ApiException(403, "Esta conta entra com email e senha");
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
