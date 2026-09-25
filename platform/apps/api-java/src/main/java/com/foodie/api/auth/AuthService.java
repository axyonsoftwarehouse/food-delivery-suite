package com.foodie.api.auth;

import com.foodie.api.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final AuthRepository repository;
    private final PasswordVerifier passwords;
    private final SecureRandom random = new SecureRandom();

    public AuthService(AuthRepository repository, PasswordVerifier passwords) {
        this.repository = repository;
        this.passwords = passwords;
    }

    @Transactional
    public Login login(String email, String password) {
        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        String limitKey = hashToken("login:" + normalizedEmail);
        if (repository.isLoginLimited(limitKey)) {
            throw new ApiException(429, "Muitas tentativas. Aguarde antes de tentar novamente");
        }
        var credentials = repository.findCredentials(normalizedEmail);
        if (credentials.isEmpty() || !passwords.matches(password, credentials.get().passwordHash()) || credentials.get().suspended()) {
            repository.registerLoginFailure(limitKey);
            throw new ApiException(401, "Credenciais inválidas");
        }
        repository.clearLoginFailures(limitKey);
        return newSession(credentials.get().user());
    }

    @Transactional
    public Login signup(String name, String email, String password) {
        User customer = repository.createCustomer(name.trim(), email.toLowerCase(Locale.ROOT), passwords.hash(password));
        return newSession(customer);
    }

    public Optional<User> currentUser(String token) {
        if (!validToken(token)) return Optional.empty();
        return repository.findSessionUser(hashToken(token));
    }

    public User requireUser(String token, String... roles) {
        User user = currentUser(token).orElseThrow(() -> new ApiException(401, "Faça login para continuar"));
        if (roles.length > 0 && java.util.Arrays.stream(roles).noneMatch(user.role()::equals)) {
            throw new ApiException(403, "Acesso não autorizado");
        }
        return user;
    }

    public void logout(String token) {
        if (validToken(token)) repository.deleteSession(hashToken(token));
    }

    public void logoutAll(String token) {
        currentUser(token).ifPresent(user -> repository.deleteSessionsForUser(user.id()));
    }

    public boolean setSuspended(long userId, boolean suspended, String reason) {
        return repository.setSuspended(userId, suspended, reason == null ? null : reason.trim());
    }

    private Login newSession(User user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        repository.createSession(hashToken(token), user.id());
        return new Login(user, token);
    }

    private static boolean validToken(String token) {
        return token != null && token.matches("[0-9a-f]{64}");
    }

    private static String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public record Login(User user, String token) {}
}
