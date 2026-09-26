package com.foodie.api.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FirebaseFcmSenderTest {
    @Test
    void disabledWithoutServiceAccount() {
        assertThat(new FirebaseFcmSender(new ObjectMapper(), "", null).configured()).isFalse();
    }

    @Test
    void disabledWhenServiceAccountIsMalformed() {
        assertThat(new FirebaseFcmSender(new ObjectMapper(), "not-json", null).configured()).isFalse();
    }

    @Test
    void enabledWithValidServiceAccount() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keys = generator.generateKeyPair();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keys.getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----\n";
        String serviceAccount = new ObjectMapper().writeValueAsString(Map.of(
            "client_email", "foodie@projeto.iam.gserviceaccount.com",
            "private_key", pem,
            "project_id", "projeto-foodie",
            "token_uri", "https://oauth2.googleapis.com/token"));

        assertThat(new FirebaseFcmSender(new ObjectMapper(), serviceAccount, null).configured()).isTrue();
        assertThat(new FirebaseFcmSender(new ObjectMapper(), serviceAccount, "projeto-explicito").configured()).isTrue();
    }
}
