package com.foodie.api.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.ApiException;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PrivacyController.class)
class PrivacyControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;

    @MockitoBean
    private PrivacyService privacy;

    @MockitoBean
    private com.foodie.api.rewards.RewardsService rewards;

    private final User customer = new User(7, "Cliente", "cliente@demo.local", "customer", null);
    private final Cookie session = new Cookie("foodie_session", "session");

    @Test
    void exportIsADownloadThatIsNotCached() throws Exception {
        when(auth.requireUser("session")).thenReturn(customer);
        when(privacy.export(customer)).thenReturn(Map.of("account", Map.of("name", "Cliente")));

        mvc.perform(get("/me/data-export").cookie(session))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("foodie-meus-dados.json")))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.account.name").value("Cliente"));
    }

    @Test
    void privacyTellsTheScreenWhetherToAskForThePassword() throws Exception {
        when(auth.requireUser("session")).thenReturn(customer);
        when(privacy.passwordLogin(7)).thenReturn(true);

        mvc.perform(get("/me/privacy").cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.passwordLogin").value(true))
            .andExpect(jsonPath("$.canDelete").value(true));
    }

    @Test
    void deleteConfirmsAnonymizesNotifiesAndClearsTheCookie() throws Exception {
        when(auth.requireUser("session")).thenReturn(customer);
        when(privacy.deleteAccount(customer)).thenReturn("cliente@demo.local");

        mvc.perform(post("/me/delete-account").cookie(session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirm\":\"EXCLUIR\",\"password\":\"senha-certa-123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok").value(true))
            .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
        verify(privacy).confirmDeletion(customer, "EXCLUIR", "senha-certa-123");
        verify(privacy).sendDeletionNotice("cliente@demo.local");
    }

    @Test
    void failedConfirmationDeletesNothing() throws Exception {
        when(auth.requireUser("session")).thenReturn(customer);
        doThrow(new ApiException(403, "Senha incorreta")).when(privacy).confirmDeletion(eq(customer), eq("EXCLUIR"), any());

        mvc.perform(post("/me/delete-account").cookie(session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirm\":\"EXCLUIR\",\"password\":\"errada\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("Senha incorreta"));
        verify(privacy, never()).deleteAccount(any());
    }
}
