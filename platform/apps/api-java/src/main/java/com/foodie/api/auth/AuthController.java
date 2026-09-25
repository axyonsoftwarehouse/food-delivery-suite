package com.foodie.api.auth;

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
    private final boolean cookieSecure;

    public AuthController(AuthService auth, @Value("${app.cookie-secure:false}") boolean cookieSecure) {
        this.auth = auth;
        this.cookieSecure = cookieSecure;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<User> login(@Valid @RequestBody LoginRequest request) {
        AuthService.Login login = auth.login(request.email(), request.password());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie(login.token(), Duration.ofDays(7)).toString()).body(login.user());
    }

    @PostMapping("/auth/signup")
    public ResponseEntity<User> signup(@Valid @RequestBody SignupRequest request) {
        AuthService.Login signup = auth.signup(request.name(), request.email(), request.password());
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

    @GetMapping("/me")
    public Map<String, User> me(@CookieValue(value = "foodie_session", required = false) String token) {
        return java.util.Collections.singletonMap("user", auth.currentUser(token).orElse(null));
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from("foodie_session", value)
            .httpOnly(true).secure(cookieSecure).sameSite("Lax").path("/").maxAge(maxAge).build();
    }

    public record LoginRequest(@NotBlank @Email @Size(max = 190) String email, @NotBlank @Size(max = 128) String password) {}
    public record SignupRequest(@NotBlank @Size(min = 2, max = 120) String name,
                                @NotBlank @Email @Size(max = 190) String email,
                                @NotBlank @Size(min = 12, max = 128) String password) {}
}
