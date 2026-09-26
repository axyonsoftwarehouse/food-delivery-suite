package com.foodie.api.auth;

import org.springframework.stereotype.Service;

/** Login social. Hoje só Google; a estrutura permite adicionar outros provedores depois. */
@Service
public class SocialAuthService {
    private final AuthRepository repository;
    private final PasswordVerifier passwords;
    private final GoogleIdentityService google;

    public SocialAuthService(AuthRepository repository, PasswordVerifier passwords, GoogleIdentityService google) {
        this.repository = repository;
        this.passwords = passwords;
        this.google = google;
    }

    public AuthService.Login google(String idToken) {
        GoogleIdentityService.GoogleIdentity identity = google.verify(idToken);
        User user = repository.findByEmail(identity.email()).orElseGet(() ->
            repository.createCustomer(identity.name(), identity.email(), passwords.hash(Tokens.random())));
        String token = Tokens.random();
        repository.createSession(Tokens.hash(token), user.id());
        return new AuthService.Login(user, token);
    }
}
