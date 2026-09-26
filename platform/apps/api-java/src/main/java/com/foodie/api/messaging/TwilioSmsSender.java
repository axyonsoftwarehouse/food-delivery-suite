package com.foodie.api.messaging;

import com.foodie.api.ApiException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Provedor Twilio (estrutura pronta; requer credenciais para enviar). */
@Service
public class TwilioSmsSender implements SmsSender {
    private final RestClient client;
    private final String accountSid;
    private final String authToken;
    private final String from;

    public TwilioSmsSender(@Value("${app.sms.twilio.account-sid:}") String accountSid,
                           @Value("${app.sms.twilio.auth-token:}") String authToken,
                           @Value("${app.sms.from:}") String from,
                           @Value("${app.sms.twilio.base-url:https://api.twilio.com}") String baseUrl) {
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.from = from;
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public String provider() {
        return "twilio";
    }

    public boolean configured() {
        return accountSid != null && !accountSid.isBlank() && authToken != null && !authToken.isBlank();
    }

    @Override
    public void send(String phone, String message) {
        if (!configured()) throw new ApiException(503, "SMS não configurado: defina TWILIO_ACCOUNT_SID e TWILIO_AUTH_TOKEN");
        if (from == null || from.isBlank()) throw new ApiException(503, "SMS sem remetente: defina SMS_FROM");
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", phone);
        form.add("From", from);
        form.add("Body", message);
        try {
            client.post().uri("/2010-04-01/Accounts/{sid}/Messages.json", accountSid)
                .headers(headers -> headers.setBasicAuth(accountSid, authToken))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (RestClientResponseException error) {
            throw new ApiException(502, "Twilio recusou o envio (" + error.getStatusCode().value() + ")");
        }
    }

    /** Diagnóstico sem segredos para o painel/health. */
    public Map<String, Object> status() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("provider", provider());
        value.put("configured", configured());
        value.put("from", from == null || from.isBlank() ? null : from);
        return value;
    }
}
