package com.foodie.api.auth;

import com.foodie.api.ApiException;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private static final int FORGOT_ACCOUNT_THRESHOLD = 5;
    private static final int FORGOT_ORIGIN_THRESHOLD = 30;
    private static final int VERIFY_MINUTES = 60 * 24;
    private static final int RESET_MINUTES = 30;

    private final AuthRepository repository;
    private final PasswordVerifier passwords;
    private final MailService mail;
    private final String publicBaseUrl;

    public AccountService(AuthRepository repository, PasswordVerifier passwords, MailService mail,
                          @Value("${app.public-base-url:http://127.0.0.1:3001}") String publicBaseUrl) {
        this.repository = repository;
        this.passwords = passwords;
        this.mail = mail;
        this.publicBaseUrl = publicBaseUrl;
    }

    public void sendVerification(User user) {
        String token = Tokens.random();
        repository.createActionToken(Tokens.hash(token), user.id(), "verify_email", VERIFY_MINUTES);
        mail.send(user.email(), "Confirme seu email Foodie",
            "Confirme seu email em " + publicBaseUrl + "/verify-email?token=" + token + " (válido por 24 horas).");
    }

    @Transactional
    public void verifyEmail(String token) {
        long userId = repository.consumeActionToken(Tokens.hash(token), "verify_email")
            .orElseThrow(() -> new ApiException(400, "Token inválido ou expirado"));
        repository.markEmailVerified(userId);
    }

    public void forgotPassword(String email, String origin) {
        String normalized = email.toLowerCase(Locale.ROOT);
        String accountKey = Tokens.hash("forgot:" + normalized);
        String originKey = origin == null ? null : Tokens.hash("forgot-ip:" + origin);
        if (repository.isLoginLimited(accountKey) || originKey != null && repository.isLoginLimited(originKey)) {
            throw new ApiException(429, "Muitas tentativas. Aguarde antes de tentar novamente");
        }
        repository.registerLoginFailure(accountKey, FORGOT_ACCOUNT_THRESHOLD);
        if (originKey != null) repository.registerLoginFailure(originKey, FORGOT_ORIGIN_THRESHOLD);
        var credentials = repository.findCredentials(normalized);
        if (credentials.isEmpty() || credentials.get().suspended()) return;
        String token = Tokens.random();
        repository.createActionToken(Tokens.hash(token), credentials.get().user().id(), "reset_password", RESET_MINUTES);
        mail.send(credentials.get().user().email(), "Redefinição de senha Foodie",
            "Defina uma nova senha em " + publicBaseUrl + "/reset-password?token=" + token + " (válido por 30 minutos).");
    }

    @Transactional
    public void resetPassword(String token, String password) {
        long userId = repository.consumeActionToken(Tokens.hash(token), "reset_password")
            .orElseThrow(() -> new ApiException(400, "Token inválido ou expirado"));
        repository.updatePassword(userId, passwords.hash(password));
        repository.deleteSessionsForUser(userId);
    }

    public boolean emailVerified(User user) {
        return repository.isEmailVerified(user.id());
    }
}
