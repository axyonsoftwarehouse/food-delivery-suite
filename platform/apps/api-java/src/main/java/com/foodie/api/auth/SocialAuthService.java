package com.foodie.api.auth;

import org.springframework.stereotype.Service;

/** Login social. Hoje só Google; a estrutura permite adicionar outros provedores depois. */
@Service
public class SocialAuthService {
    private final AuthRepository repository;
    private final PasswordVerifier passwords;
    private final GoogleIdentityService google;
    private final FacebookIdentityService facebook;

    public SocialAuthService(AuthRepository repository, PasswordVerifier passwords, GoogleIdentityService google, FacebookIdentityService facebook) {
        this.repository = repository;
        this.passwords = passwords;
        this.google = google;
        this.facebook = facebook;
    }

    public AuthService.Login google(String idToken) {
        GoogleIdentityService.GoogleIdentity identity = google.verify(idToken);
        return session(identity.email(), identity.name());
    }

    public AuthService.Login facebook(String accessToken) {
        FacebookIdentityService.Identity identity = facebook.verify(accessToken);
        return session(identity.email(), identity.name());
    }

    private AuthService.Login session(String email, String name) {
        User user = repository.findByEmail(email).orElseGet(() ->
            repository.createCustomer(name, email, passwords.hash(Tokens.random())));
        repository.markEmailVerified(user.id());
        String token = Tokens.random();
        repository.createSession(Tokens.hash(token), user.id());
        return new AuthService.Login(user, token);
    }
}
