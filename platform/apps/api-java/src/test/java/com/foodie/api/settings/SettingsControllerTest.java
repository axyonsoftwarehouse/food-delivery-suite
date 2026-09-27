package com.foodie.api.settings;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminAuditService;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.admin.AdminPermissions;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SettingsController.class)
class SettingsControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;

    @MockitoBean
    private AdminPermissionService permissions;

    @MockitoBean
    private AdminAuditService audit;

    @MockitoBean
    private SettingsService settings;

    @Test
    void listsSettingsForFullAdmin() throws Exception {
        when(auth.requireUser("s", "admin")).thenReturn(new User(1, "Admin", "admin@demo.local", "admin", null));
        doNothing().when(permissions).require(any(), eq(AdminPermissions.SETTINGS_MANAGE));
        when(settings.adminView()).thenReturn(List.of(
            Map.of("key", SettingsCatalog.BUSINESS_NAME, "label", "Nome do negócio", "type", "text", "group", "Negócio", "value", "Foodie")));

        mvc.perform(get("/admin/settings").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].key").value("business.name"))
            .andExpect(jsonPath("$[0].value").value("Foodie"));
    }

    @Test
    void restrictedAdminIsForbidden() throws Exception {
        when(auth.requireUser("s", "admin")).thenReturn(new User(2, "Suporte", "suporte@demo.local", "admin", null));
        doThrow(new ApiException(403, "Acesso não autorizado")).when(permissions).require(any(), eq(AdminPermissions.SETTINGS_MANAGE));

        mvc.perform(get("/admin/settings").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s")))
            .andExpect(status().isForbidden());
    }

    @Test
    void updatesSettings() throws Exception {
        when(auth.requireUser("s", "admin")).thenReturn(new User(1, "Admin", "admin@demo.local", "admin", null));
        doNothing().when(permissions).require(any(), eq(AdminPermissions.SETTINGS_MANAGE));
        when(settings.adminView()).thenReturn(List.of());

        mvc.perform(patch("/admin/settings").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"values\":{\"business.name\":\"Minha Loja\"}}"))
            .andExpect(status().isOk());
    }
}
