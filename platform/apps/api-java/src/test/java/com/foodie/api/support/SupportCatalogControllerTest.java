package com.foodie.api.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.admin.AdminPermissions;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.catalog.MenuService;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SupportCatalogController.class)
class SupportCatalogControllerTest {
    private static final Cookie SESSION = new Cookie("foodie_session", "s");
    private final User admin = new User(1, "Ana Suporte", "ana@demo.local", "admin", null);

    @Autowired private MockMvc mvc;
    @MockitoBean private AuthService auth;
    @MockitoBean private AdminPermissionService permissions;
    @MockitoBean private SupportActionService support;
    @MockitoBean private MenuService menu;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(auth.requireUser("s", "admin")).thenReturn(admin);
        when(support.act(any(), anyLong(), anyString(), anyString(), any(), anyString(), any(), any()))
            .thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(7)).get());
    }

    @Test
    void readsTheStoreCatalogWithViewPermission() throws Exception {
        when(menu.catalog(7L)).thenReturn(Map.of("categories", java.util.List.of(), "products", java.util.List.of()));
        mvc.perform(get("/admin/support/restaurants/7/catalog").cookie(SESSION)).andExpect(status().isOk());
        verify(permissions).require(admin, AdminPermissions.SUPPORT_VIEW);
    }

    @Test
    void updatesProductScopedToTheStoreWithReason() throws Exception {
        when(menu.updateProduct(eq(7L), eq(11L), any())).thenReturn(Map.of("id", 11L, "price_cents", 3300));

        mvc.perform(patch("/admin/support/restaurants/7/products/11").cookie(SESSION)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Preço digitado errado pela loja\",\"data\":{\"priceCents\":3300}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.price_cents").value(3300));
        verify(permissions).require(admin, AdminPermissions.SUPPORT_ACT);
        verify(support).act(eq(admin), eq(7L), eq("product.update"), eq("product"), eq(11L), anyString(), eq("Preço digitado errado pela loja"), any());
    }

    @Test
    void productOfAnotherStoreIs404() throws Exception {
        when(menu.updateProduct(eq(7L), eq(12L), any())).thenThrow(new ApiException(404, "Produto não encontrado"));
        mvc.perform(patch("/admin/support/restaurants/7/products/12").cookie(SESSION)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Preço digitado errado pela loja\",\"data\":{\"priceCents\":3300}}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void withoutActPermissionIsForbiddenAndNothingRuns() throws Exception {
        doThrow(new ApiException(403, "Acesso não autorizado")).when(permissions).require(admin, AdminPermissions.SUPPORT_ACT);
        mvc.perform(delete("/admin/support/restaurants/7/products/11").cookie(SESSION)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Produto duplicado no cardápio\"}"))
            .andExpect(status().isForbidden());
        verify(menu, never()).deleteProduct(any(), anyLong());
    }

    @Test
    void invalidDataIs400() throws Exception {
        doNothing().when(permissions).require(admin, AdminPermissions.SUPPORT_ACT);
        mvc.perform(post("/admin/support/restaurants/7/categories").cookie(SESSION)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Categoria pedida pela loja\",\"data\":{\"name\":\"\"}}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createsCategoryForTheStore() throws Exception {
        when(menu.createCategory(7L, "Bebidas")).thenReturn(Map.of("id", 3L, "name", "Bebidas"));
        mvc.perform(post("/admin/support/restaurants/7/categories").cookie(SESSION)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Categoria pedida pela loja\",\"data\":{\"name\":\"Bebidas\"}}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Bebidas"));
    }
}
