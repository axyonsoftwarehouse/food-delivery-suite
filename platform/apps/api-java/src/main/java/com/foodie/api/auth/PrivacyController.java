package com.foodie.api.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Direitos do titular (LGPD): cópia dos dados e exclusão da conta pelo próprio cliente. */
@RestController
public class PrivacyController {
    private final AuthService auth;
    private final PrivacyService privacy;
    private final boolean cookieSecure;

    public PrivacyController(AuthService auth, PrivacyService privacy, @Value("${app.cookie-secure:false}") boolean cookieSecure) {
        this.auth = auth;
        this.privacy = privacy;
        this.cookieSecure = cookieSecure;
    }

    @GetMapping("/me/privacy")
    public Map<String, Object> privacy(@CookieValue(value = "foodie_session", required = false) String token) {
        User user = auth.requireUser(token);
        return Map.of("passwordLogin", privacy.passwordLogin(user.id()), "canDelete", "customer".equals(user.role()));
    }

    @GetMapping("/me/data-export")
    public ResponseEntity<Map<String, Object>> export(@CookieValue(value = "foodie_session", required = false) String token) {
        User user = auth.requireUser(token);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"foodie-meus-dados.json\"")
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(privacy.export(user));
    }

    @PostMapping("/me/delete-account")
    public ResponseEntity<Map<String, Boolean>> deleteAccount(@CookieValue(value = "foodie_session", required = false) String token,
                                                              @Valid @RequestBody DeleteRequest body) {
        User user = auth.requireUser(token);
        privacy.confirmDeletion(user, body.confirm(), body.password());
        String email = privacy.deleteAccount(user);
        privacy.sendDeletionNotice(email);
        ResponseCookie cleared = ResponseCookie.from("foodie_session", "")
            .httpOnly(true).secure(cookieSecure).sameSite("Lax").path("/").maxAge(0).build();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cleared.toString()).body(Map.of("ok", true));
    }

    public record DeleteRequest(@NotBlank @Size(max = 20) String confirm, @Size(max = 128) String password) {}
}
