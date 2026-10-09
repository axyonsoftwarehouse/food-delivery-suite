package com.foodie.api.restaurant;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.permissions.PermissionService;
import com.foodie.api.permissions.Permissions;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RestaurantContactController.class)
class RestaurantContactControllerTest {
    private static final User OWNER = new User(2, "Loja", "loja@demo.local", "restaurant", 3L);
    private static final Cookie SESSION = new Cookie("foodie_session", "s");

    @Autowired private MockMvc mvc;
    @MockitoBean private AuthService auth;
    @MockitoBean private PermissionService permissions;
    @MockitoBean private JdbcTemplate jdbc;

    @Test
    void storeSavesItsOwnPhoneOnlyWithDigits() throws Exception {
        when(auth.requireUser("s", "restaurant", "kitchen")).thenReturn(OWNER);
        mvc.perform(put("/restaurant/contact").cookie(SESSION).contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"(85) 3222-1100\"}"))
            .andExpect(status().isOk());
        verify(permissions).require(OWNER, Permissions.SETTINGS_MANAGE);
        verify(jdbc).update("UPDATE restaurants SET phone = ? WHERE id = ?", "8532221100", 3L);
    }

    @Test
    void invalidPhoneIsRefused() throws Exception {
        when(auth.requireUser("s", "restaurant", "kitchen")).thenReturn(OWNER);
        mvc.perform(put("/restaurant/contact").cookie(SESSION).contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"123\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void withoutSettingsPermissionIsForbidden() throws Exception {
        when(auth.requireUser("s", "restaurant", "kitchen")).thenReturn(OWNER);
        doThrow(new ApiException(403, "Acesso não autorizado")).when(permissions).require(eq(OWNER), eq(Permissions.SETTINGS_MANAGE));
        mvc.perform(put("/restaurant/contact").cookie(SESSION).contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"8532221100\"}"))
            .andExpect(status().isForbidden());
    }
}
