package com.foodie.api.catalog;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.permissions.PermissionService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CatalogImportController.class)
class CatalogImportControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;

    @MockitoBean
    private PermissionService permissions;

    @MockitoBean
    private CatalogImportService imports;

    @Test
    void restaurantCanPreviewItsOwnCsv() throws Exception {
        when(auth.requireUser("session", "restaurant"))
            .thenReturn(new User(5, "Dono", "dono@demo.local", "restaurant", 7L));
        when(imports.importCsv(eq(7L), eq("categoria,produto,descricao,preco\nBebidas,Suco,Natural,9.90\n"), eq(true)))
            .thenReturn(new CatalogImportService.Result(true, false, 1, 1, 0, List.of(), List.of()));

        mvc.perform(post("/restaurant/catalog/import?dryRun=true")
                .cookie(new jakarta.servlet.http.Cookie("foodie_session", "session"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"csv\":\"categoria,produto,descricao,preco\\nBebidas,Suco,Natural,9.90\\n\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dryRun").value(true))
            .andExpect(jsonPath("$.productsCreated").value(1));
    }

    @Test
    void emptyCsvIsRejectedByValidation() throws Exception {
        when(auth.requireUser("session", "restaurant"))
            .thenReturn(new User(5, "Dono", "dono@demo.local", "restaurant", 7L));

        mvc.perform(post("/restaurant/catalog/import")
                .cookie(new jakarta.servlet.http.Cookie("foodie_session", "session"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"csv\":\"\"}"))
            .andExpect(status().isBadRequest());
    }
}
