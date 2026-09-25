package com.foodie.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
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

@RestController
public class AuthController {
    private final AuthService auth;
    private final AccountService account;
    private final boolean cookieSecure;

    public AuthController(AuthService auth, AccountService account, @Value("${app.cookie-secure:false}") boolean cookieSecure) {
        this.auth = auth;
        this.account = account;
        this.cookieSecure = cookieSecure;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<User> login(HttpServletRequest request, @Valid @RequestBody LoginRequest body) {
        AuthService.Login login = auth.login(body.email(), body.password(), clientIp(request));
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie(login.token(), Duration.ofDays(7)).toString()).body(login.user());
    }

    @PostMapping("/auth/signup")
    public ResponseEntity<User> signup(HttpServletRequest request, @Valid @RequestBody SignupRequest body) {
        AuthService.Login signup = auth.signup(body.name(), body.email(), body.password(), clientIp(request));
        account.sendVerification(signup.user());
        return ResponseEntity.status(201).header(HttpHeaders.SET_COOKIE, cookie(signup.token(), Duration.ofDays(7)).toString()).body(signup.user());
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Map<String, Boolean>> logout(@CookieValue(value = "foodie_session", required = false) String token) {
        auth.logout(token);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString()).body(Map.of("ok", true));
    }

    @PostMapping("/auth/logout-all")
    public ResponseEntity<Map<String, Boolean>> logoutAll(@CookieValue(value = "foodie_session", required = false) String token) {
        auth.logoutAll(token);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString()).body(Map.of("ok", true));
    }

    @PostMapping("/auth/verify-email")
    public Map<String, Boolean> verifyEmail(@Valid @RequestBody VerifyRequest body) {
        account.verifyEmail(body.token());
        return Map.of("ok", true);
    }

    @PostMapping("/auth/forgot-password")
    public Map<String, Boolean> forgotPassword(HttpServletRequest request, @Valid @RequestBody ForgotRequest body) {
        account.forgotPassword(body.email(), clientIp(request));
        return Map.of("ok", true);
    }

    @PostMapping("/auth/reset-password")
    public ResponseEntity<Map<String, Boolean>> resetPassword(@Valid @RequestBody ResetRequest body) {
        account.resetPassword(body.token(), body.password());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString()).body(Map.of("ok", true));
    }

    @GetMapping("/me")
    public Map<String, User> me(@CookieValue(value = "foodie_session", required = false) String token) {
        return java.util.Collections.singletonMap("user", auth.currentUser(token).orElse(null));
    }

    @GetMapping("/auth/security")
    public Map<String, Boolean> security(@CookieValue(value = "foodie_session", required = false) String token) {
        return Map.of("emailVerified", account.emailVerified(auth.requireUser(token)));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? null : remote;
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from("foodie_session", value)
            .httpOnly(true).secure(cookieSecure).sameSite("Lax").path("/").maxAge(maxAge).build();
    }

    public record LoginRequest(@NotBlank @Email @Size(max = 190) String email, @NotBlank @Size(max = 128) String password) {}
    public record SignupRequest(@NotBlank @Size(min = 2, max = 120) String name,
                                @NotBlank @Email @Size(max = 190) String email,
                                @NotBlank @Size(min = 12, max = 128) String password) {}
    public record VerifyRequest(@NotBlank @Size(max = 128) String token) {}
    public record ForgotRequest(@NotBlank @Email @Size(max = 190) String email) {}
    public record ResetRequest(@NotBlank @Size(max = 128) String token, @NotBlank @Size(min = 12, max = 128) String password) {}
}
