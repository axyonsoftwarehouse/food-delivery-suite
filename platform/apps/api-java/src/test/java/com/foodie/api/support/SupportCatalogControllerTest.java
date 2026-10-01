package com.foodie.api.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.admin.AdminPermissions;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.catalog.MenuService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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

    private record WriteRouteTestCase(
        String method,
        String path,
        String body,
        String expectedAction,
        String entityType,
        Long entityId
    ) {}

    static Stream<WriteRouteTestCase> writeRoutesProvider() {
        return Stream.of(
            // Categories
            new WriteRouteTestCase("POST", "/admin/support/restaurants/7/categories",
                "{\"reason\":\"Motivo valido de teste\",\"data\":{\"name\":\"Bebidas\"}}",
                "category.create", "category", null),

            new WriteRouteTestCase("PATCH", "/admin/support/restaurants/7/categories/11",
                "{\"reason\":\"Motivo valido de teste\",\"data\":{\"name\":\"Bebidas Premium\"}}",
                "category.update", "category", 11L),

            new WriteRouteTestCase("DELETE", "/admin/support/restaurants/7/categories/3",
                "{\"reason\":\"Motivo valido de teste\"}",
                "category.delete", "category", 3L),

            // Products
            new WriteRouteTestCase("POST", "/admin/support/restaurants/7/products",
                "{\"reason\":\"Motivo valido de teste\",\"data\":{\"categoryId\":11,\"name\":\"Hamburguer\",\"description\":\"Hamburguer sabroso\",\"priceCents\":1500}}",
                "product.create", "product", null),

            new WriteRouteTestCase("PATCH", "/admin/support/restaurants/7/products/11",
                "{\"reason\":\"Motivo valido de teste\",\"data\":{\"priceCents\":1600}}",
                "product.update", "product", 11L),

            new WriteRouteTestCase("DELETE", "/admin/support/restaurants/7/products/3",
                "{\"reason\":\"Motivo valido de teste\"}",
                "product.delete", "product", 3L),

            // Variations
            new WriteRouteTestCase("POST", "/admin/support/restaurants/7/products/11/variations",
                "{\"reason\":\"Motivo valido de teste\",\"data\":{\"name\":\"Grande\",\"priceDeltaCents\":200,\"sort\":1}}",
                "variation.create", "product", 11L),

            new WriteRouteTestCase("PATCH", "/admin/support/restaurants/7/products/11/variations/31",
                "{\"reason\":\"Motivo valido de teste\",\"data\":{\"name\":\"Pequena\",\"priceDeltaCents\":100,\"sort\":0}}",
                "variation.update", "product", 11L),

            new WriteRouteTestCase("DELETE", "/admin/support/restaurants/7/products/11/variations/5",
                "{\"reason\":\"Motivo valido de teste\"}",
                "variation.delete", "product", 11L),

            // Product Images
            new WriteRouteTestCase("PUT", "/admin/support/restaurants/7/products/11/images",
                "{\"reason\":\"Motivo valido de teste\",\"data\":{\"images\":[{\"url\":\"https://example.com/image.jpg\",\"cover\":true}]}}",
                "product.images", "product", 11L),

            // Product Addon Groups
            new WriteRouteTestCase("PUT", "/admin/support/restaurants/7/products/11/addon-groups",
                "{\"reason\":\"Motivo valido de teste\",\"data\":{\"groupIds\":[1,2]}}",
                "product.addon-groups", "product", 11L),

            // Product Tags
            new WriteRouteTestCase("PUT", "/admin/support/restaurants/7/products/11/tags",
                "{\"reason\":\"Motivo valido de teste\",\"data\":{\"tagIds\":[1,2]}}",
                "product.tags", "product", 11L),

            // Combo Items
            new WriteRouteTestCase("PUT", "/admin/support/restaurants/7/products/11/combo-items",
                "{\"reason\":\"Motivo valido de teste\",\"data\":{\"items\":[{\"componentProductId\":5,\"quantity\":2}]}}",
                "product.combo", "product", 11L),

            // Addon Groups
            new WriteRouteTestCase("POST", "/admin/support/restaurants/7/addon-groups",
                "{\"reason\":\"Motivo valido de teste\",\"data\":{\"name\":\"Adicionais\",\"minSelect\":1,\"maxSelect\":3,\"required\":true}}",
                "addon-group.create", "addon_group", null),

            new WriteRouteTestCase("PATCH", "/admin/support/restaurants/7/addon-groups/31",
                "{\"reason\":\"Motivo valido de teste\",\"data\":{\"name\":\"Extras\",\"minSelect\":0,\"maxSelect\":5,\"required\":false}}",
                "addon-group.update", "addon_group", 31L),

            new WriteRouteTestCase("DELETE", "/admin/support/restaurants/7/addon-groups/5",
                "{\"reason\":\"Motivo valido de teste\"}",
                "addon-group.delete", "addon_group", 5L),

            // Addons
            new WriteRouteTestCase("POST", "/admin/support/restaurants/7/addon-groups/31/addons",
                "{\"reason\":\"Motivo valido de teste\",\"data\":{\"name\":\"Queijo extra\",\"priceCents\":150}}",
                "addon.create", "addon_group", 31L),

            new WriteRouteTestCase("PATCH", "/admin/support/restaurants/7/addon-groups/31/addons/9",
                "{\"reason\":\"Motivo valido de teste\",\"data\":{\"name\":\"Bacon\",\"priceCents\":200,\"available\":true}}",
                "addon.update", "addon_group", 31L),

            new WriteRouteTestCase("DELETE", "/admin/support/restaurants/7/addon-groups/31/addons/3",
                "{\"reason\":\"Motivo valido de teste\"}",
                "addon.delete", "addon_group", 31L),

            // Tags
            new WriteRouteTestCase("POST", "/admin/support/restaurants/7/tags",
                "{\"reason\":\"Motivo valido de teste\",\"data\":{\"name\":\"Vegan\"}}",
                "tag.create", "tag", null),

            new WriteRouteTestCase("DELETE", "/admin/support/restaurants/7/tags/11",
                "{\"reason\":\"Motivo valido de teste\"}",
                "tag.delete", "tag", 11L)
        );
    }

    @ParameterizedTest(name = "{0} {1} -> {3}")
    @MethodSource("writeRoutesProvider")
    void writingAllRoutesCallsSupportActWithStoreIdAndAction(WriteRouteTestCase testCase) throws Exception {
        // Setup mocks based on the action being tested
        switch (testCase.expectedAction()) {
            case "category.create" -> when(menu.createCategory(7L, "Bebidas")).thenReturn(Map.of("id", 1L));
            case "category.update" -> when(menu.renameCategory(7L, 11L, "Bebidas Premium")).thenReturn(Map.of("id", 11L));
            case "category.delete" -> doNothing().when(menu).deleteCategory(7L, 3L);
            case "product.create" -> when(menu.createProduct(7L, 11L, "Hamburguer", "Hamburguer sabroso", 1500)).thenReturn(Map.of("id", 1L));
            case "product.update" -> when(menu.updateProduct(eq(7L), eq(11L), any())).thenReturn(Map.of("id", 11L));
            case "product.delete" -> doNothing().when(menu).deleteProduct(7L, 3L);
            case "variation.create" -> when(menu.createVariation(7L, 11L, "Grande", 200, 1)).thenReturn(Map.of("id", 1L));
            case "variation.update" -> when(menu.updateVariation(eq(7L), eq(11L), eq(31L), anyString(), anyInt(), any(), any())).thenReturn(Map.of("id", 31L));
            case "variation.delete" -> doNothing().when(menu).deleteVariation(7L, 11L, 5L);
            case "product.images" -> when(menu.replaceImages(eq(7L), eq(11L), any())).thenReturn(List.of());
            case "product.addon-groups" -> when(menu.setProductAddonGroups(eq(7L), eq(11L), anyLong(), any())).thenReturn(List.of());
            case "product.tags" -> when(menu.setProductTags(eq(7L), eq(11L), any())).thenReturn(List.of());
            case "product.combo" -> when(menu.setComboItems(eq(7L), eq(11L), any())).thenReturn(List.of());
            case "addon-group.create" -> when(menu.createAddonGroup(7L, "Adicionais", 1, 3, true)).thenReturn(Map.of("id", 1L));
            case "addon-group.update" -> when(menu.updateAddonGroup(7L, 31L, "Extras", 0, 5, false)).thenReturn(Map.of("id", 31L));
            case "addon-group.delete" -> doNothing().when(menu).deleteAddonGroup(7L, 5L);
            case "addon.create" -> when(menu.createAddon(7L, 31L, "Queijo extra", 150)).thenReturn(Map.of("id", 1L));
            case "addon.update" -> when(menu.updateAddon(eq(7L), eq(31L), eq(9L), anyString(), anyInt(), any())).thenReturn(Map.of("id", 9L));
            case "addon.delete" -> doNothing().when(menu).deleteAddon(7L, 31L, 3L);
            case "tag.create" -> when(menu.createTag(7L, "Vegan")).thenReturn(Map.of("id", 1L));
            case "tag.delete" -> doNothing().when(menu).deleteTag(7L, 11L);
            default -> throw new IllegalArgumentException("Unknown action: " + testCase.expectedAction());
        }

        MockHttpServletRequestBuilder builder = switch (testCase.method()) {
            case "POST" -> post(testCase.path());
            case "PATCH" -> patch(testCase.path());
            case "PUT" -> put(testCase.path());
            case "DELETE" -> delete(testCase.path());
            default -> throw new IllegalArgumentException("Unknown method: " + testCase.method());
        };

        mvc.perform(builder.cookie(SESSION).contentType(MediaType.APPLICATION_JSON).content(testCase.body()))
            .andExpect(status().is2xxSuccessful());

        String entity = testCase.entityType();
        Long entityId = testCase.entityId();
        String action = testCase.expectedAction();

        verify(support).act(eq(admin), eq(7L), eq(action), eq(entity), entityId != null ? eq(entityId) : any(), anyString(), eq("Motivo valido de teste"), any());
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
        verify(menu).updateProduct(eq(7L), eq(12L), any());
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
        verify(support, never()).act(any(), anyLong(), anyString(), anyString(), any(), anyString(), any(), any());
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
