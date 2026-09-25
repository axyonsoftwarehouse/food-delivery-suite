package com.foodie.api.auth;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
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

    @Test
    void loginKeepsResponseAndCookieContract() throws Exception {
        var user = new User(7, "Cliente", "cliente@demo.local", "customer", null);
        when(auth.login("cliente@demo.local", "test-password-123")).thenReturn(new AuthService.Login(user, "a".repeat(64)));

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
        when(auth.login("cliente@demo.local", "wrong")).thenThrow(new ApiException(401, "Credenciais inválidas"));
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"cliente@demo.local\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Credenciais inválidas"));
    }

    @Test
    void signupCreatesOnlyCustomerSession() throws Exception {
        var customer = new User(9, "Nova Cliente", "nova@demo.local", "customer", null);
        when(auth.signup("Nova Cliente", "nova@demo.local", "test-password-123"))
            .thenReturn(new AuthService.Login(customer, "b".repeat(64)));

        mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nova Cliente\",\"email\":\"nova@demo.local\",\"password\":\"test-password-123\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.role").value("customer"))
            .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("foodie_session=")));
    }
}
