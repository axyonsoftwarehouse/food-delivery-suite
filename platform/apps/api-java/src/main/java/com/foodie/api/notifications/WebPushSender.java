package com.foodie.api.notifications;

import com.foodie.api.ApiException;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class WebPushSender implements PushSender {
    private final String publicKey;
    private final String privateKey;
    private final String subject;

    public WebPushSender(@Value("${app.webpush.public-key:}") String publicKey,
                         @Value("${app.webpush.private-key:}") String privateKey,
                         @Value("${app.webpush.subject:mailto:contato@foodie.local}") String subject) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.subject = subject;
    }

    @Override
    public boolean configured() {
        return publicKey != null && !publicKey.isBlank() && privateKey != null && !privateKey.isBlank();
    }

    public String publicKey() {
        return publicKey;
    }

    @Override
    public void send(String endpoint, String p256dh, String auth, String payload) {
        if (!configured()) throw new ApiException(503, "Web Push não configurado: defina WEBPUSH_PUBLIC_KEY e WEBPUSH_PRIVATE_KEY");
        // Inscrições gravadas antes da validação no cadastro também passam por aqui.
        if (!PushEndpoints.allowed(endpoint)) throw new ApiException(400, "Endereço de Web Push fora dos serviços permitidos");
        try {
            Notification notification = new Notification(endpoint, p256dh, auth, payload);
            HttpResponse response = new PushService(publicKey, privateKey, subject).send(notification);
            int status = response.getStatusLine().getStatusCode();
            if (status < 200 || status >= 300) throw new ApiException(502, "Web Push recusado (" + status + ")");
        } catch (ApiException error) {
            throw error;
        } catch (Exception error) {
            throw new ApiException(502, "Falha no Web Push: " + error.getMessage());
        }
    }
}
