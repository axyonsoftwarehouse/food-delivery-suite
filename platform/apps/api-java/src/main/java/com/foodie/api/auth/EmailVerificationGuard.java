package com.foodie.api.auth;

import com.foodie.api.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Exige email confirmado para contas de cliente operarem (login e pedidos) quando
 * {@code app.auth.require-email-verification} está ativo. Contas administrativas e de
 * operação são criadas pelo admin e não passam por aqui.
 */
@Component
public class EmailVerificationGuard {
    private final AuthRepository repository;
    private final boolean required;

    public EmailVerificationGuard(AuthRepository repository,
                                  @Value("${app.auth.require-email-verification:true}") boolean required) {
        this.repository = repository;
        this.required = required;
    }

    public void requireVerified(User user) {
        if (!required || user == null) return;
        if (!"customer".equals(user.role())) return;
        if (repository.isEmailVerified(user.id())) return;
        throw new ApiException(403, "Confirme seu email para continuar");
    }
}
