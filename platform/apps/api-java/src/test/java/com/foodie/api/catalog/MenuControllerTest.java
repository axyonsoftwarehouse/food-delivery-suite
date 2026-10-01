package com.foodie.api.catalog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.permissions.PermissionService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MenuController.class)
class MenuControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService auth;

    @MockitoBean
    private MenuService menu;

    @MockitoBean
    private PermissionService permissions;

    private static Map<String, Object> product(boolean available) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", 11L);
        value.put("restaurant_id", 7L);
        value.put("category_id", 3L);
        value.put("name", "Bowl novo");
        value.put("description", "");
        value.put("price_cents", 3090);
        value.put("available", available);
        return value;
    }

    @Test
    void restaurantEditsOwnProduct() throws Exception {
        when(auth.requireUser("session", "restaurant")).thenReturn(new User(5, "Cozinha", "cozinha@demo.local", "restaurant", 7L));
        when(menu.updateProduct(eq(7L), eq(11L), any())).thenReturn(product(true));

        mvc.perform(patch("/restaurant/products/11")
                .cookie(new jakarta.servlet.http.Cookie("foodie_session", "session"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Bowl novo\",\"priceCents\":3090}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.price_cents").value(3090));
    }

    @Test
    void restaurantCannotEditAnotherRestaurantProduct() throws Exception {
        when(auth.requireUser("session", "restaurant")).thenReturn(new User(5, "Cozinha", "cozinha@demo.local", "restaurant", 7L));
        when(menu.updateProduct(eq(7L), eq(12L), any())).thenThrow(new ApiException(404, "Produto não encontrado"));

        mvc.perform(patch("/restaurant/products/12")
                .cookie(new jakarta.servlet.http.Cookie("foodie_session", "session"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Outro produto\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void deletingProductUsedInOrdersIsBlocked() throws Exception {
        when(auth.requireUser("session", "restaurant")).thenReturn(new User(5, "Cozinha", "cozinha@demo.local", "restaurant", 7L));
        org.mockito.Mockito.doThrow(new ApiException(409, "Este produto já foi usado em pedidos; pause em vez de excluir"))
            .when(menu).deleteProduct(7L, 5L);

        mvc.perform(delete("/restaurant/products/5").cookie(new jakarta.servlet.http.Cookie("foodie_session", "session")))
            .andExpect(status().isConflict());
    }

    @Test
    void restaurantCreatesCategory() throws Exception {
        when(auth.requireUser("session", "restaurant")).thenReturn(new User(5, "Cozinha", "cozinha@demo.local", "restaurant", 7L));
        Map<String, Object> category = new LinkedHashMap<>();
        category.put("id", 9L);
        category.put("restaurant_id", 7L);
        category.put("name", "Sobremesas");
        when(menu.createCategory(7L, "Sobremesas")).thenReturn(category);

        mvc.perform(post("/restaurant/categories")
                .cookie(new jakarta.servlet.http.Cookie("foodie_session", "session"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Sobremesas\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(9));
    }

    @Test
    void restaurantCreatesVariation() throws Exception {
        when(auth.requireUser("session", "restaurant")).thenReturn(new User(5, "Cozinha", "cozinha@demo.local", "restaurant", 7L));
        Map<String, Object> variation = new LinkedHashMap<>();
        variation.put("id", 21L);
        variation.put("product_id", 11L);
        variation.put("name", "Grande");
        variation.put("price_delta_cents", 500);
        variation.put("available", true);
        variation.put("sort", 0);
        when(menu.createVariation(any(), eq(11L), eq("Grande"), eq(500), any())).thenReturn(variation);

        mvc.perform(post("/restaurant/products/11/variations")
                .cookie(new jakarta.servlet.http.Cookie("foodie_session", "session"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Grande\",\"priceDeltaCents\":500}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(21))
            .andExpect(jsonPath("$.price_delta_cents").value(500));
    }

    @Test
    void restaurantRejectsNegativeVariationDelta() throws Exception {
        when(auth.requireUser("session", "restaurant")).thenReturn(new User(5, "Cozinha", "cozinha@demo.local", "restaurant", 7L));

        mvc.perform(post("/restaurant/products/11/variations")
                .cookie(new jakarta.servlet.http.Cookie("foodie_session", "session"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Grande\",\"priceDeltaCents\":-100}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void restaurantCreatesAddonGroup() throws Exception {
        when(auth.requireUser("session", "restaurant")).thenReturn(new User(5, "Cozinha", "cozinha@demo.local", "restaurant", 7L));
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("id", 31L);
        group.put("restaurant_id", 7L);
        group.put("name", "Adicionais");
        group.put("min_select", 0);
        group.put("max_select", 3);
        group.put("required", false);
        when(menu.createAddonGroup(eq(7L), eq("Adicionais"), eq(0), eq(3), eq(false))).thenReturn(group);

        mvc.perform(post("/restaurant/addon-groups")
                .cookie(new jakarta.servlet.http.Cookie("foodie_session", "session"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Adicionais\",\"minSelect\":0,\"maxSelect\":3,\"required\":false}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.max_select").value(3));
    }

    @Test
    void restaurantCreatesTag() throws Exception {
        when(auth.requireUser("session", "restaurant")).thenReturn(new User(5, "Cozinha", "cozinha@demo.local", "restaurant", 7L));
        when(menu.createTag(eq(7L), eq("Vegano"))).thenReturn(Map.of("id", 41L, "name", "Vegano"));

        mvc.perform(post("/restaurant/tags")
                .cookie(new jakarta.servlet.http.Cookie("foodie_session", "session"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Vegano\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(41));
    }
}
