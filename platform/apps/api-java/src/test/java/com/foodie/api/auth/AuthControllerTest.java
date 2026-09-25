package com.foodie.api.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.ApiException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
class AuthControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;

    @MockitoBean
    private AccountService account;

    @Test
    void loginKeepsResponseAndCookieContract() throws Exception {
        var user = new User(7, "Cliente", "cliente@demo.local", "customer", null);
        when(auth.login(eq("cliente@demo.local"), eq("test-password-123"), any())).thenReturn(new AuthService.Login(user, "a".repeat(64)));

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"cliente@demo.local\",\"password\":\"test-password-123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("customer"))
            .andExpect(jsonPath("$.restaurantId").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("foodie_session=")))
            .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly")))
            .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("SameSite=Lax")));
    }

    @Test
    void unauthenticatedMeAndLogoutMatchPrototype() throws Exception {
        when(auth.currentUser(null)).thenReturn(Optional.empty());
        mvc.perform(get("/me")).andExpect(status().isOk())
            .andExpect(jsonPath("$.user").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(post("/auth/logout")).andExpect(status().isOk())
            .andExpect(jsonPath("$.ok").value(true))
            .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
    }

    @Test
    void logoutAllClearsTheCookieAndDelegatesSessionRevocation() throws Exception {
        mvc.perform(post("/auth/logout-all").cookie(new jakarta.servlet.http.Cookie("foodie_session", "a".repeat(64))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok").value(true))
            .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
        verify(auth).logoutAll("a".repeat(64));
    }

    @Test
    void badCredentialsKeepErrorShape() throws Exception {
        when(auth.login(eq("cliente@demo.local"), eq("wrong"), any())).thenThrow(new ApiException(401, "Credenciais inválidas"));
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"cliente@demo.local\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Credenciais inválidas"));
    }

    @Test
    void signupCreatesOnlyCustomerSession() throws Exception {
        var customer = new User(9, "Nova Cliente", "nova@demo.local", "customer", null);
        when(auth.signup(eq("Nova Cliente"), eq("nova@demo.local"), eq("test-password-123"), any()))
            .thenReturn(new AuthService.Login(customer, "b".repeat(64)));

        mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nova Cliente\",\"email\":\"nova@demo.local\",\"password\":\"test-password-123\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.role").value("customer"))
            .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("foodie_session=")));
        verify(account).sendVerification(customer);
    }

    @Test
    void forgotPasswordAlwaysReturnsOk() throws Exception {
        mvc.perform(post("/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"naoexiste@demo.local\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok").value(true));
        verify(account).forgotPassword(eq("naoexiste@demo.local"), any());
    }

    @Test
    void resetPasswordRejectsInvalidToken() throws Exception {
        org.mockito.Mockito.doThrow(new ApiException(400, "Token inválido ou expirado"))
            .when(account).resetPassword(eq("bad"), eq("test-password-123"));

        mvc.perform(post("/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"bad\",\"password\":\"test-password-123\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Token inválido ou expirado"));
    }

    @Test
    void verifyEmailDelegatesToAccountService() throws Exception {
        mvc.perform(post("/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"verify-token\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok").value(true));
        verify(account).verifyEmail("verify-token");
    }

    @Test
    void securityReportsVerificationState() throws Exception {
        var user = new User(7, "Cliente", "cliente@demo.local", "customer", null);
        when(auth.requireUser("session")).thenReturn(user);
        when(account.emailVerified(user)).thenReturn(true);

        mvc.perform(get("/auth/security").cookie(new jakarta.servlet.http.Cookie("foodie_session", "session")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.emailVerified").value(true));
    }
}
