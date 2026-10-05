package com.foodie.api.payments;

import com.foodie.api.ApiException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cifra os tokens do Mercado Pago de cada loja (AES-256-GCM). A chave vem do ambiente e nunca vai
 * para o banco: um backup ou acesso ao banco não entrega o dinheiro das lojas. Sem chave, a vinculação
 * recusa — gravar token em texto aberto não é alternativa.
 */
@Component
public class TokenCipher {
    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public TokenCipher(@Value("${app.payments.token-key:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            this.key = null;
            return;
        }
        byte[] raw = Base64.getDecoder().decode(base64Key.strip());
        if (raw.length != 32) throw new IllegalStateException("PAYMENTS_TOKEN_KEY precisa ter 32 bytes em base64");
        this.key = new SecretKeySpec(raw, "AES");
    }

    public boolean configured() {
        return key != null;
    }

    public String encrypt(String plain) {
        if (key == null) throw new ApiException(503, "Vinculação do Mercado Pago não configurada");
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array());
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Não foi possível cifrar o token", error);
        }
    }

    public String decrypt(String stored) {
        if (key == null) throw new ApiException(503, "Vinculação do Mercado Pago não configurada");
        if (stored == null || !stored.startsWith(PREFIX)) throw new IllegalStateException("Token criptografado inválido ou chave errada");
        try {
            byte[] raw = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES));
            return new String(cipher.doFinal(raw, IV_BYTES, raw.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException error) {
            throw new IllegalStateException("Token criptografado inválido ou chave errada", error);
        }
    }
}
