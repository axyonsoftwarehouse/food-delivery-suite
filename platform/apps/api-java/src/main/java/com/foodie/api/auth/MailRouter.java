package com.foodie.api.auth;

import com.foodie.api.ApiException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Seleciona o provedor de email configurado ({@code app.mail.provider}, padrão {@code log}). */
@Service
public class MailRouter implements MailService {
    private final Map<String, MailProvider> providers;
    private final String provider;

    public MailRouter(List<MailProvider> providers, @Value("${app.mail.provider:log}") String provider) {
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(MailProvider::provider, item -> item));
        this.provider = provider;
    }

    @Override
    public void send(String to, String subject, String body) {
        MailProvider target = providers.get(provider);
        if (target == null) throw new ApiException(500, "Provedor de email desconhecido: " + provider);
        target.send(to, subject, body);
    }

    public String provider() {
        return provider;
    }
}
