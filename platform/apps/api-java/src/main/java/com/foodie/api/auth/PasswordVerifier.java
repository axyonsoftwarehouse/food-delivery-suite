package com.foodie.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.bouncycastle.crypto.generators.SCrypt;
import org.springframework.stereotype.Component;

@Component
public class PasswordVerifier {
    private final SecureRandom random = new SecureRandom();

    public String hash(String password) {
        byte[] saltBytes = new byte[16];
        random.nextBytes(saltBytes);
        String salt = HexFormat.of().formatHex(saltBytes);
        byte[] hash = SCrypt.generate(password.getBytes(StandardCharsets.UTF_8), salt.getBytes(StandardCharsets.UTF_8), 16384, 8, 1, 64);
        return salt + ":" + HexFormat.of().formatHex(hash);
    }

    public boolean matches(String password, String stored) {
        if (password == null || stored == null) return false;
        String[] parts = stored.split(":", -1);
        if (parts.length != 2 || !parts[0].matches("[0-9a-f]{32}") || !parts[1].matches("[0-9a-f]{128}")) return false;
        byte[] actual = SCrypt.generate(
            password.getBytes(StandardCharsets.UTF_8),
            parts[0].getBytes(StandardCharsets.UTF_8),
            16384, 8, 1, 64
        );
        return MessageDigest.isEqual(actual, HexFormat.of().parseHex(parts[1]));
    }
}
