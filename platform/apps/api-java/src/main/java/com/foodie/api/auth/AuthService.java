package com.foodie.api.auth;

import com.foodie.api.ApiException;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private static final int ACCOUNT_THRESHOLD = 5;
    private static final int ORIGIN_THRESHOLD = 30;

    private final AuthRepository repository;
    private final PasswordVerifier passwords;

    public AuthService(AuthRepository repository, PasswordVerifier passwords) {
        this.repository = repository;
        this.passwords = passwords;
    }

    public Login login(String email, String password) {
        return login(email, password, null);
    }

    public Login login(String email, String password, String origin) {
        repository.deleteExpiredSessions();
        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        String accountKey = Tokens.hash("login:" + normalizedEmail);
        String originKey = origin == null ? null : Tokens.hash("login-ip:" + origin);
        if (repository.isLoginLimited(accountKey) || originKey != null && repository.isLoginLimited(originKey)) {
            throw new ApiException(429, "Muitas tentativas. Aguarde antes de tentar novamente");
        }
        var credentials = repository.findCredentials(normalizedEmail);
        if (credentials.isEmpty() || !passwords.matches(password, credentials.get().passwordHash()) || credentials.get().suspended()) {
            repository.registerLoginFailure(accountKey, ACCOUNT_THRESHOLD);
            if (originKey != null) repository.registerLoginFailure(originKey, ORIGIN_THRESHOLD);
            throw new ApiException(401, "Credenciais invÃ¡lidas");
        }
        repository.clearLoginFailures(accountKey);
        if (originKey != null) repository.clearLoginFailures(originKey);
        return newSession(credentials.get().user());
    }

    public Login signup(String name, String email, String password) {
        return signup(name, email, password, null);
    }

    public Login signup(String name, String email, String password, String origin) {
        String originKey = origin == null ? null : Tokens.hash("signup-ip:" + origin);
        if (originKey != null && repository.isLoginLimited(originKey)) {
            throw new ApiException(429, "Muitas tentativas. Aguarde antes de tentar novamente");
        }
        User customer;
        try {
            customer = repository.createCustomer(name.trim(), email.toLowerCase(Locale.ROOT), passwords.hash(password));
        } catch (DuplicateKeyException error) {
            if (originKey != null) repository.registerLoginFailure(originKey, ORIGIN_THRESHOLD);
            throw new ApiException(409, "JÃ¡ existe uma conta com este email");
        }
        if (originKey != null) repository.clearLoginFailures(originKey);
        return newSession(customer);
    }

    public Optional<User> currentUser(String token) {
        if (!validToken(token)) return Optional.empty();
        return repository.findSessionUser(Tokens.hash(token));
    }

    public User requireUser(String token, String... roles) {
        User user = currentUser(token).orElseThrow(() -> new ApiException(401, "FaÃ§a login para continuar"));
        if (roles.length > 0 && java.util.Arrays.stream(roles).noneMatch(user.role()::equals)) {
            throw new ApiException(403, "Acesso nÃ£o autorizado");
        }
        return user;
    }

    public void logout(String token) {
        if (validToken(token)) repository.deleteSession(Tokens.hash(token));
    }

    public void logoutAll(String token) {
        currentUser(token).ifPresent(user -> repository.deleteSessionsForUser(user.id()));
    }

    public boolean setSuspended(long userId, boolean suspended, String reason) {
        return repository.setSuspended(userId, suspended, reason == null ? null : reason.trim());
    }

    private Login newSession(User user) {
        String token = Tokens.random();
        repository.createSession(Tokens.hash(token), user.id());
        return new Login(user, token);
    }

    private static boolean validToken(String token) {
        return token != null && token.matches("[0-9a-f]{64}");
    }

    public record Login(User user, String token) {}
}
