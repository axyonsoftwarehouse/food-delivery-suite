package com.foodie.api.payments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.foodie.api.ApiException;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class TokenCipherTest {
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String OTHER_KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    @Test
    void roundTripsAndNeverRepeatsTheCiphertext() {
        TokenCipher cipher = new TokenCipher(KEY);
        String a = cipher.encrypt("APP_USR-token-da-loja");
        String b = cipher.encrypt("APP_USR-token-da-loja");

        assertTrue(a.startsWith("v1:"));
        assertNotEquals(a, b); // IV aleatório
        assertEquals("APP_USR-token-da-loja", cipher.decrypt(a));
        assertEquals("APP_USR-token-da-loja", cipher.decrypt(b));
    }

    @Test
    void rejectsTamperedTextAndWrongKey() {
        String stored = new TokenCipher(KEY).encrypt("APP_USR-token-da-loja");
        byte[] raw = Base64.getDecoder().decode(stored.substring(3));
        raw[raw.length - 1] ^= 1;
        String tampered = "v1:" + Base64.getEncoder().encodeToString(raw);

        assertThrows(IllegalStateException.class, () -> new TokenCipher(KEY).decrypt(tampered));
        assertThrows(IllegalStateException.class, () -> new TokenCipher(OTHER_KEY).decrypt(stored));
        assertThrows(IllegalStateException.class, () -> new TokenCipher(KEY).decrypt("texto-aberto"));
    }

    @Test
    void withoutKeyRefusesToEncrypt() {
        TokenCipher cipher = new TokenCipher("");
        assertFalse(cipher.configured());
        assertEquals(503, assertThrows(ApiException.class, () -> cipher.encrypt("x")).status());
    }

    @Test
    void keyMustHave32Bytes() {
        assertThrows(IllegalStateException.class, () -> new TokenCipher(Base64.getEncoder().encodeToString(new byte[16])));
    }
}
