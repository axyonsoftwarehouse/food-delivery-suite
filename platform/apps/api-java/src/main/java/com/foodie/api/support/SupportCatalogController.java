package com.foodie.api.support;

import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.admin.AdminPermissions;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.catalog.MenuController;
import com.foodie.api.catalog.MenuService;
import com.foodie.api.support.SupportRequests.ReasonRequest;
import com.foodie.api.support.SupportRequests.SupportRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Cardápio da loja no modo suporte (E48). Toda escrita passa por {@link SupportActionService}. */
@RestController
@RequestMapping("/admin/support/restaurants/{id}")
public class SupportCatalogController {
    private final AuthService auth;
    private final AdminPermissionService permissions;
    private final SupportActionService support;
    private final MenuService menu;

    public SupportCatalogController(AuthService auth, AdminPermissionService permissions, SupportActionService support, MenuService menu) {
        this.auth = auth;
        this.permissions = permissions;
        this.support = support;
        this.menu = menu;
    }

    // ----- leitura -----

    @GetMapping("/catalog")
    public Map<String, Object> catalog(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        viewer(token);
        return menu.catalog(id);
    }

    @GetMapping("/addon-groups")
    public List<Map<String, Object>> addonGroups(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        viewer(token);
        return menu.addonGroups(id);
    }

    @GetMapping("/tags")
    public List<Map<String, Object>> tags(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        viewer(token);
        return menu.tags(id);
    }

    @GetMapping("/products/{productId}/variations")
    public List<Map<String, Object>> variations(@CookieValue(value = "foodie_session", required = false) String token,
                                                @PathVariable @Positive long id, @PathVariable @Positive long productId) {
        viewer(token);
        return menu.variations(id, productId);
    }

    @GetMapping("/products/{productId}/images")
    public List<Map<String, Object>> images(@CookieValue(value = "foodie_session", required = false) String token,
                                            @PathVariable @Positive long id, @PathVariable @Positive long productId) {
        viewer(token);
        return menu.images(id, productId);
    }

    @GetMapping("/products/{productId}/addon-groups")
    public List<Map<String, Object>> productAddonGroups(@CookieValue(value = "foodie_session", required = false) String token,
                                                        @PathVariable @Positive long id, @PathVariable @Positive long productId,
                                                        @RequestParam(defaultValue = "0") long variationId) {
        viewer(token);
        return menu.productAddonGroups(id, productId, variationId);
    }

    @GetMapping("/products/{productId}/tags")
    public List<Map<String, Object>> productTags(@CookieValue(value = "foodie_session", required = false) String token,
                                                 @PathVariable @Positive long id, @PathVariable @Positive long productId) {
        viewer(token);
        return menu.productTags(id, productId);
    }

    @GetMapping("/products/{productId}/combo-items")
    public List<Map<String, Object>> comboItems(@CookieValue(value = "foodie_session", required = false) String token,
                                                @PathVariable @Positive long id, @PathVariable @Positive long productId) {
        viewer(token);
        return menu.comboItems(id, productId);
    }

    // ----- categorias -----

    @PostMapping("/categories")
    public ResponseEntity<Map<String, Object>> createCategory(@CookieValue(value = "foodie_session", required = false) String token,
                                                              @PathVariable @Positive long id,
                                                              @Valid @RequestBody SupportRequest<MenuController.CategoryRequest> body) {
        User actor = actor(token);
        String name = body.data().name();
        return ResponseEntity.status(201).body(act(actor, id, "category.create", "category", null, "Categoria criada: " + name, body.reason(),
            () -> menu.createCategory(id, name)));
    }

    @PatchMapping("/categories/{categoryId}")
    public Map<String, Object> renameCategory(@CookieValue(value = "foodie_session", required = false) String token,
                                              @PathVariable @Positive long id, @PathVariable @Positive long categoryId,
                                              @Valid @RequestBody SupportRequest<MenuController.CategoryRequest> body) {
        User actor = actor(token);
        String name = body.data().name();
        return act(actor, id, "category.update", "category", categoryId, "Categoria renomeada: " + name, body.reason(),
            () -> menu.renameCategory(id, categoryId, name));
    }

    @DeleteMapping("/categories/{categoryId}")
    public Map<String, Boolean> deleteCategory(@CookieValue(value = "foodie_session", required = false) String token,
                                               @PathVariable @Positive long id, @PathVariable @Positive long categoryId,
                                               @RequestBody ReasonRequest body) {
        User actor = actor(token);
        return act(actor, id, "category.delete", "category", categoryId, "Categoria #" + categoryId + " excluída", body.reason(), () -> {
            menu.deleteCategory(id, categoryId);
            return Map.of("ok", true);
        });
    }

    // ----- produtos -----

    @PostMapping("/products")
    public ResponseEntity<Map<String, Object>> createProduct(@CookieValue(value = "foodie_session", required = false) String token,
                                                             @PathVariable @Positive long id,
                                                             @Valid @RequestBody SupportRequest<MenuController.ProductRequest> body) {
        User actor = actor(token);
        MenuController.ProductRequest data = body.data();
        return ResponseEntity.status(201).body(act(actor, id, "product.create", "product", null, "Produto criado: " + data.name(), body.reason(),
            () -> menu.createProduct(id, data.categoryId(), data.name(), data.description(), data.priceCents())));
    }

    @PatchMapping("/products/{productId}")
    public Map<String, Object> updateProduct(@CookieValue(value = "foodie_session", required = false) String token,
                                             @PathVariable @Positive long id, @PathVariable @Positive long productId,
                                             @Valid @RequestBody SupportRequest<MenuController.ProductUpdateRequest> body) {
        User actor = actor(token);
        MenuController.ProductUpdateRequest data = body.data();
        String summary = Boolean.FALSE.equals(data.available()) ? "Produto #" + productId + " pausado"
            : Boolean.TRUE.equals(data.available()) ? "Produto #" + productId + " reativado"
            : "Produto #" + productId + " alterado";
        return act(actor, id, "product.update", "product", productId, summary, body.reason(),
            () -> menu.updateProduct(id, productId, data.toUpdate()));
    }

    @DeleteMapping("/products/{productId}")
    public Map<String, Boolean> deleteProduct(@CookieValue(value = "foodie_session", required = false) String token,
                                              @PathVariable @Positive long id, @PathVariable @Positive long productId,
                                              @RequestBody ReasonRequest body) {
        User actor = actor(token);
        return act(actor, id, "product.delete", "product", productId, "Produto #" + productId + " excluído", body.reason(), () -> {
            menu.deleteProduct(id, productId);
            return Map.of("ok", true);
        });
    }

    @PostMapping("/products/{productId}/variations")
    public ResponseEntity<Map<String, Object>> createVariation(@CookieValue(value = "foodie_session", required = false) String token,
                                                               @PathVariable @Positive long id, @PathVariable @Positive long productId,
                                                               @Valid @RequestBody SupportRequest<MenuController.VariationRequest> body) {
        User actor = actor(token);
        MenuController.VariationRequest data = body.data();
        return ResponseEntity.status(201).body(act(actor, id, "variation.create", "product", productId,
            "Variação criada no produto #" + productId + ": " + data.name(), body.reason(),
            () -> menu.createVariation(id, productId, data.name(), data.priceDeltaCents(), data.sort())));
    }

    @PatchMapping("/products/{productId}/variations/{variationId}")
    public Map<String, Object> updateVariation(@CookieValue(value = "foodie_session", required = false) String token,
                                               @PathVariable @Positive long id, @PathVariable @Positive long productId,
                                               @PathVariable @Positive long variationId,
                                               @Valid @RequestBody SupportRequest<MenuController.VariationUpdateRequest> body) {
        User actor = actor(token);
        MenuController.VariationUpdateRequest data = body.data();
        return act(actor, id, "variation.update", "product", productId, "Variação #" + variationId + " alterada", body.reason(),
            () -> menu.updateVariation(id, productId, variationId, data.name(), data.priceDeltaCents(), data.available(), data.sort()));
    }

    @DeleteMapping("/products/{productId}/variations/{variationId}")
    public Map<String, Boolean> deleteVariation(@CookieValue(value = "foodie_session", required = false) String token,
                                                @PathVariable @Positive long id, @PathVariable @Positive long productId,
                                                @PathVariable @Positive long variationId, @RequestBody ReasonRequest body) {
        User actor = actor(token);
        return act(actor, id, "variation.delete", "product", productId, "Variação #" + variationId + " excluída", body.reason(), () -> {
            menu.deleteVariation(id, productId, variationId);
            return Map.of("ok", true);
        });
    }

    @PutMapping("/products/{productId}/images")
    public List<Map<String, Object>> replaceImages(@CookieValue(value = "foodie_session", required = false) String token,
                                                   @PathVariable @Positive long id, @PathVariable @Positive long productId,
                                                   @Valid @RequestBody SupportRequest<MenuController.ImagesRequest> body) {
        User actor = actor(token);
        return act(actor, id, "product.images", "product", productId, "Imagens do produto #" + productId + " alteradas", body.reason(),
            () -> menu.replaceImages(id, productId, body.data().toImages()));
    }

    @PutMapping("/products/{productId}/addon-groups")
    public List<Map<String, Object>> setProductAddonGroups(@CookieValue(value = "foodie_session", required = false) String token,
                                                           @PathVariable @Positive long id, @PathVariable @Positive long productId,
                                                           @RequestParam(defaultValue = "0") long variationId,
                                                           @Valid @RequestBody SupportRequest<MenuController.GroupIdsRequest> body) {
        User actor = actor(token);
        return act(actor, id, "product.addon-groups", "product", productId, "Adicionais do produto #" + productId + " alterados", body.reason(),
            () -> menu.setProductAddonGroups(id, productId, variationId, body.data().groupIds()));
    }

    @PutMapping("/products/{productId}/tags")
    public List<Map<String, Object>> setProductTags(@CookieValue(value = "foodie_session", required = false) String token,
                                                    @PathVariable @Positive long id, @PathVariable @Positive long productId,
                                                    @Valid @RequestBody SupportRequest<MenuController.TagIdsRequest> body) {
        User actor = actor(token);
        return act(actor, id, "product.tags", "product", productId, "Tags do produto #" + productId + " alteradas", body.reason(),
            () -> menu.setProductTags(id, productId, body.data().tagIds()));
    }

    @PutMapping("/products/{productId}/combo-items")
    public List<Map<String, Object>> setComboItems(@CookieValue(value = "foodie_session", required = false) String token,
                                                   @PathVariable @Positive long id, @PathVariable @Positive long productId,
                                                   @Valid @RequestBody SupportRequest<MenuController.ComboItemsRequest> body) {
        User actor = actor(token);
        return act(actor, id, "product.combo", "product", productId, "Itens do combo #" + productId + " alterados", body.reason(),
            () -> menu.setComboItems(id, productId, body.data().toItems()));
    }

    // ----- adicionais -----

    @PostMapping("/addon-groups")
    public ResponseEntity<Map<String, Object>> createAddonGroup(@CookieValue(value = "foodie_session", required = false) String token,
                                                                @PathVariable @Positive long id,
                                                                @Valid @RequestBody SupportRequest<MenuController.AddonGroupRequest> body) {
        User actor = actor(token);
        MenuController.AddonGroupRequest data = body.data();
        return ResponseEntity.status(201).body(act(actor, id, "addon-group.create", "addon_group", null, "Grupo de adicionais criado: " + data.name(), body.reason(),
            () -> menu.createAddonGroup(id, data.name(), data.minSelect(), data.maxSelect(), data.required())));
    }

    @PatchMapping("/addon-groups/{groupId}")
    public Map<String, Object> updateAddonGroup(@CookieValue(value = "foodie_session", required = false) String token,
                                                @PathVariable @Positive long id, @PathVariable @Positive long groupId,
                                                @Valid @RequestBody SupportRequest<MenuController.AddonGroupUpdateRequest> body) {
        User actor = actor(token);
        MenuController.AddonGroupUpdateRequest data = body.data();
        return act(actor, id, "addon-group.update", "addon_group", groupId, "Grupo de adicionais #" + groupId + " alterado", body.reason(),
            () -> menu.updateAddonGroup(id, groupId, data.name(), data.minSelect(), data.maxSelect(), data.required()));
    }

    @DeleteMapping("/addon-groups/{groupId}")
    public Map<String, Boolean> deleteAddonGroup(@CookieValue(value = "foodie_session", required = false) String token,
                                                 @PathVariable @Positive long id, @PathVariable @Positive long groupId,
                                                 @RequestBody ReasonRequest body) {
        User actor = actor(token);
        return act(actor, id, "addon-group.delete", "addon_group", groupId, "Grupo de adicionais #" + groupId + " excluído", body.reason(), () -> {
            menu.deleteAddonGroup(id, groupId);
            return Map.of("ok", true);
        });
    }

    @PostMapping("/addon-groups/{groupId}/addons")
    public ResponseEntity<Map<String, Object>> createAddon(@CookieValue(value = "foodie_session", required = false) String token,
                                                           @PathVariable @Positive long id, @PathVariable @Positive long groupId,
                                                           @Valid @RequestBody SupportRequest<MenuController.AddonRequest> body) {
        User actor = actor(token);
        MenuController.AddonRequest data = body.data();
        return ResponseEntity.status(201).body(act(actor, id, "addon.create", "addon_group", groupId, "Adicional criado: " + data.name(), body.reason(),
            () -> menu.createAddon(id, groupId, data.name(), data.priceCents())));
    }

    @PatchMapping("/addon-groups/{groupId}/addons/{addonId}")
    public Map<String, Object> updateAddon(@CookieValue(value = "foodie_session", required = false) String token,
                                           @PathVariable @Positive long id, @PathVariable @Positive long groupId,
                                           @PathVariable @Positive long addonId,
                                           @Valid @RequestBody SupportRequest<MenuController.AddonUpdateRequest> body) {
        User actor = actor(token);
        MenuController.AddonUpdateRequest data = body.data();
        return act(actor, id, "addon.update", "addon_group", groupId, "Adicional #" + addonId + " alterado", body.reason(),
            () -> menu.updateAddon(id, groupId, addonId, data.name(), data.priceCents(), data.available()));
    }

    @DeleteMapping("/addon-groups/{groupId}/addons/{addonId}")
    public Map<String, Boolean> deleteAddon(@CookieValue(value = "foodie_session", required = false) String token,
                                            @PathVariable @Positive long id, @PathVariable @Positive long groupId,
                                            @PathVariable @Positive long addonId, @RequestBody ReasonRequest body) {
        User actor = actor(token);
        return act(actor, id, "addon.delete", "addon_group", groupId, "Adicional #" + addonId + " excluído", body.reason(), () -> {
            menu.deleteAddon(id, groupId, addonId);
            return Map.of("ok", true);
        });
    }

    // ----- tags -----

    @PostMapping("/tags")
    public ResponseEntity<Map<String, Object>> createTag(@CookieValue(value = "foodie_session", required = false) String token,
                                                         @PathVariable @Positive long id,
                                                         @Valid @RequestBody SupportRequest<MenuController.TagRequest> body) {
        User actor = actor(token);
        String name = body.data().name();
        return ResponseEntity.status(201).body(act(actor, id, "tag.create", "tag", null, "Tag criada: " + name, body.reason(),
            () -> menu.createTag(id, name)));
    }

    @DeleteMapping("/tags/{tagId}")
    public Map<String, Boolean> deleteTag(@CookieValue(value = "foodie_session", required = false) String token,
                                          @PathVariable @Positive long id, @PathVariable @Positive long tagId,
                                          @RequestBody ReasonRequest body) {
        User actor = actor(token);
        return act(actor, id, "tag.delete", "tag", tagId, "Tag #" + tagId + " excluída", body.reason(), () -> {
            menu.deleteTag(id, tagId);
            return Map.of("ok", true);
        });
    }

    private <T> T act(User actor, long id, String action, String entity, Long entityId, String summary, String reason, Supplier<T> change) {
        return support.act(actor, id, action, entity, entityId, summary, reason, change);
    }

    private User viewer(String token) {
        User user = auth.requireUser(token, "admin");
        permissions.require(user, AdminPermissions.SUPPORT_VIEW);
        return user;
    }

    private User actor(String token) {
        User user = auth.requireUser(token, "admin");
        permissions.require(user, AdminPermissions.SUPPORT_ACT);
        return user;
    }
}
