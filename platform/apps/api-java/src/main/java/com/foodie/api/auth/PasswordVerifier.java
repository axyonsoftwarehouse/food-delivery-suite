package com.foodie.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.bouncycastle.crypto.generators.SCrypt;
import org.springframework.stereotype.Component;

@Component
public class PasswordVerifier {
    // OWASP para scrypt: memória alta e custo de CPU moderado. 64 MiB (2^16) é o meio-termo
    // para uma VPS de 4 GB; hashes antigos (2^14) continuam válidos e são migrados no login.
    private static final int N = 65536;
    private static final int R = 8;
    private static final int P = 1;
    private static final int DK_LEN = 64;
    private static final int LEGACY_N = 16384;

    private final SecureRandom random = new SecureRandom();

    public String hash(String password) {
        byte[] saltBytes = new byte[16];
        random.nextBytes(saltBytes);
        String salt = HexFormat.of().formatHex(saltBytes);
        byte[] hash = SCrypt.generate(password.getBytes(StandardCharsets.UTF_8), salt.getBytes(StandardCharsets.UTF_8), N, R, P, DK_LEN);
        return N + ":" + R + ":" + P + ":" + salt + ":" + HexFormat.of().formatHex(hash);
    }

    public boolean matches(String password, String stored) {
        if (password == null || stored == null) return false;
        Params params = params(stored);
        if (params == null) return false;
        byte[] actual = SCrypt.generate(password.getBytes(StandardCharsets.UTF_8), params.salt().getBytes(StandardCharsets.UTF_8),
            params.n(), params.r(), params.p(), DK_LEN);
        return MessageDigest.isEqual(actual, HexFormat.of().parseHex(params.hash()));
    }

    /** O hash precisa ser recalculado no próximo login (formato antigo ou parâmetros menores). */
    public boolean needsRehash(String stored) {
        Params params = params(stored);
        return params == null || params.n() < N || params.r() < R || params.p() < P;
    }

    private static Params params(String stored) {
        String[] parts = stored.split(":", -1);
        try {
            if (parts.length == 2) {
                // Formato antigo salt:hash, sempre scrypt N=2^14 r=8 p=1.
                if (!parts[0].matches("[0-9a-f]{32}") || !parts[1].matches("[0-9a-f]{128}")) return null;
                return new Params(LEGACY_N, 8, 1, parts[0], parts[1]);
            }
            if (parts.length != 5) return null;
            int n = Integer.parseInt(parts[0]);
            int r = Integer.parseInt(parts[1]);
            int p = Integer.parseInt(parts[2]);
            if (n < 2 || r < 1 || p < 1 || !parts[3].matches("[0-9a-f]{32}") || !parts[4].matches("[0-9a-f]{128}")) return null;
            return new Params(n, r, p, parts[3], parts[4]);
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private record Params(int n, int r, int p, String salt, String hash) {}
}
