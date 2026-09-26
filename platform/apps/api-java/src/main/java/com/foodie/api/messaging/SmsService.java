package com.foodie.api.messaging;

import com.foodie.api.ApiException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Seleciona o provedor de SMS configurado ({@code app.sms.provider}). */
@Service
public class SmsService {
    private final Map<String, SmsSender> senders;
    private final String provider;

    public SmsService(List<SmsSender> senders, @Value("${app.sms.provider:local}") String provider) {
        this.senders = senders.stream().collect(Collectors.toUnmodifiableMap(SmsSender::provider, sender -> sender));
        this.provider = provider;
    }

    public void send(String phone, String message) {
        SmsSender sender = senders.get(provider);
        if (sender == null) throw new ApiException(500, "Provedor de SMS desconhecido: " + provider);
        sender.send(phone, message);
    }

    public String provider() {
        return provider;
    }
}
