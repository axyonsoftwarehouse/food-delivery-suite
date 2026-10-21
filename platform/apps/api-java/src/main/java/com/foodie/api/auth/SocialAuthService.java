package com.foodie.api.auth;

import com.foodie.api.ApiException;
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
        if (!identity.emailVerified()) throw new ApiException(403, "O Google não confirmou este email");
        return session("google", identity.email(), identity.name());
    }

    public AuthService.Login facebook(String accessToken) {
        FacebookIdentityService.Identity identity = facebook.verify(accessToken);
        return session("facebook", identity.email(), identity.name());
    }

    /**
     * Login social só vale para clientes. Admin, loja, cozinha e entregador entram com email e senha:
     * sem esta trava, quem obtivesse uma identidade social com o email de uma conta operacional entraria nela.
     *
     * Contas existentes só são reutilizadas quando já pertencem ao mesmo provedor. Uma conta criada com
     * email/senha não é vinculada silenciosamente: o dono precisa entrar com a senha. Uma conta ainda não
     * verificada é reivindicada pelo provedor — a senha e as sessões antigas são invalidadas, para que quem
     * cadastrou o email sem possuí-lo não continue com acesso.
     */
    private AuthService.Login session(String provider, String email, String name) {
        User user = repository.findByEmail(email).orElse(null);
        if (user == null) {
            user = repository.createCustomer(name, email, passwords.hash(Tokens.random()), provider);
        } else {
            if (!"customer".equals(user.role())) throw new ApiException(403, "Esta conta entra com email e senha");
            String linked = repository.socialProvider(user.id()).orElse(null);
            if (linked != null && !linked.equals(provider)) {
                throw new ApiException(409, "Esta conta já está vinculada a outro provedor social");
            }
            if (linked == null) {
                if (repository.isEmailVerified(user.id())) {
                    throw new ApiException(409, "Esta conta já existe com email e senha. Entre com sua senha para continuar");
                }
                if (!repository.claimSocialAccount(user.id(), provider, passwords.hash(Tokens.random()))) {
                    throw new ApiException(409, "Não foi possível vincular esta conta agora. Tente novamente");
                }
            }
        }
        repository.markEmailVerified(user.id());
        String token = Tokens.random();
        repository.createSession(Tokens.hash(token), user.id());
        return new AuthService.Login(user, token);
    }
}
