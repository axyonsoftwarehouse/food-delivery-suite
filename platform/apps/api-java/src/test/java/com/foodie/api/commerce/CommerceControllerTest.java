package com.foodie.api.commerce;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CommerceController.class)
class CommerceControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;

    @MockitoBean
    private AdminPermissionService permissions;

    @MockitoBean
    private AdminAuditService audit;

    @MockitoBean
    private JdbcTemplate jdbc;

    @Test
    void listsCampaignsForFullAdmin() throws Exception {
        when(auth.requireUser("s", "admin")).thenReturn(new User(1, "Admin", "admin@demo.local", "admin", null));
        doNothing().when(permissions).require(any(), eq(AdminPermissions.PROMOTIONS_MANAGE));
        when(jdbc.queryForList(any(String.class))).thenReturn(List.of(Map.of("id", 3, "name", "Semana da pizza")));

        mvc.perform(get("/admin/commerce/campaigns").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Semana da pizza"));
    }

    @Test
    void restrictedAdminIsForbidden() throws Exception {
        when(auth.requireUser("s", "admin")).thenReturn(new User(2, "Suporte", "suporte@demo.local", "admin", null));
        doThrow(new ApiException(403, "Acesso não autorizado")).when(permissions).require(any(), eq(AdminPermissions.PROMOTIONS_MANAGE));

        mvc.perform(get("/admin/commerce/campaigns").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s")))
            .andExpect(status().isForbidden());
    }

    @Test
    void publicBannersNeedNoAuth() throws Exception {
        when(jdbc.queryForList(any(String.class))).thenReturn(List.of(Map.of("id", 1, "title", "Promo")));
        mvc.perform(get("/public/banners"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].title").value("Promo"));
    }

    @Test
    void adminCannotCreateOrDeleteCampaigns() throws Exception {
        when(auth.requireUser("s", "admin")).thenReturn(new User(1, "Admin", "admin@demo.local", "admin", null));
        mvc.perform(post("/admin/commerce/campaigns").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Geral\",\"type\":\"basic\",\"percent\":10}"))
            .andExpect(status().isMethodNotAllowed());
        mvc.perform(delete("/admin/commerce/campaigns/3").cookie(new jakarta.servlet.http.Cookie("foodie_session", "s")))
            .andExpect(status().is4xxClientError());
    }
}
