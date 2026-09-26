package com.foodie.api.catalog;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MenuController {
    private final AuthService auth;
    private final MenuService menu;

    public MenuController(AuthService auth, MenuService menu) {
        this.auth = auth;
        this.menu = menu;
    }

    @GetMapping("/admin/restaurants/{id}/catalog")
    public Map<String, Object> adminCatalog(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        auth.requireUser(token, "admin");
        return menu.catalog(id);
    }

    @PostMapping("/admin/categories")
    public ResponseEntity<Map<String, Object>> adminCategory(@CookieValue(value = "foodie_session", required = false) String token,
                                                             @Valid @RequestBody AdminCategoryRequest body) {
        auth.requireUser(token, "admin");
        return created(menu.createCategory(body.restaurantId(), body.name()));
    }

    @PatchMapping("/admin/categories/{id}")
    public Map<String, Object> adminRenameCategory(@CookieValue(value = "foodie_session", required = false) String token,
                                                   @PathVariable @Positive long id, @Valid @RequestBody CategoryRequest body) {
        auth.requireUser(token, "admin");
        return menu.renameCategory(null, id, body.name());
    }

    @DeleteMapping("/admin/categories/{id}")
    public Map<String, Boolean> adminDeleteCategory(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        auth.requireUser(token, "admin");
        menu.deleteCategory(null, id);
        return Map.of("ok", true);
    }

    @PostMapping("/admin/products")
    public ResponseEntity<Map<String, Object>> adminProduct(@CookieValue(value = "foodie_session", required = false) String token,
                                                            @Valid @RequestBody AdminProductRequest body) {
        auth.requireUser(token, "admin");
        return created(menu.createProduct(body.restaurantId(), body.categoryId(), body.name(), body.description(), body.priceCents()));
    }

    @PatchMapping("/admin/products/{id}")
    public Map<String, Object> adminUpdateProduct(@CookieValue(value = "foodie_session", required = false) String token,
                                                  @PathVariable @Positive long id, @Valid @RequestBody ProductUpdateRequest body) {
        auth.requireUser(token, "admin");
        return menu.updateProduct(null, id, body.toUpdate());
    }

    @DeleteMapping("/admin/products/{id}")
    public Map<String, Boolean> adminDeleteProduct(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        auth.requireUser(token, "admin");
        menu.deleteProduct(null, id);
        return Map.of("ok", true);
    }

    @GetMapping("/admin/products/{id}/variations")
    public List<Map<String, Object>> adminVariations(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        auth.requireUser(token, "admin");
        return menu.variations(null, id);
    }

    @PostMapping("/admin/products/{id}/variations")
    public ResponseEntity<Map<String, Object>> adminCreateVariation(@CookieValue(value = "foodie_session", required = false) String token,
                                                                    @PathVariable @Positive long id, @Valid @RequestBody VariationRequest body) {
        auth.requireUser(token, "admin");
        return created(menu.createVariation(null, id, body.name(), body.priceDeltaCents(), body.sort()));
    }

    @PatchMapping("/admin/products/{id}/variations/{variationId}")
    public Map<String, Object> adminUpdateVariation(@CookieValue(value = "foodie_session", required = false) String token,
                                                    @PathVariable @Positive long id, @PathVariable @Positive long variationId,
                                                    @Valid @RequestBody VariationUpdateRequest body) {
        auth.requireUser(token, "admin");
        return menu.updateVariation(null, id, variationId, body.name(), body.priceDeltaCents(), body.available(), body.sort());
    }

    @DeleteMapping("/admin/products/{id}/variations/{variationId}")
    public Map<String, Boolean> adminDeleteVariation(@CookieValue(value = "foodie_session", required = false) String token,
                                                     @PathVariable @Positive long id, @PathVariable @Positive long variationId) {
        auth.requireUser(token, "admin");
        menu.deleteVariation(null, id, variationId);
        return Map.of("ok", true);
    }

    @GetMapping("/admin/products/{id}/images")
    public List<Map<String, Object>> adminImages(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        auth.requireUser(token, "admin");
        return menu.images(null, id);
    }

    @PutMapping("/admin/products/{id}/images")
    public List<Map<String, Object>> adminReplaceImages(@CookieValue(value = "foodie_session", required = false) String token,
                                                        @PathVariable @Positive long id, @Valid @RequestBody ImagesRequest body) {
        auth.requireUser(token, "admin");
        return menu.replaceImages(null, id, body.toImages());
    }

    @GetMapping("/admin/addon-groups")
    public List<Map<String, Object>> adminAddonGroups(@CookieValue(value = "foodie_session", required = false) String token,
                                                      @RequestParam @Positive long restaurantId) {
        auth.requireUser(token, "admin");
        return menu.addonGroups(restaurantId);
    }

    @PostMapping("/admin/addon-groups")
    public ResponseEntity<Map<String, Object>> adminCreateAddonGroup(@CookieValue(value = "foodie_session", required = false) String token,
                                                                     @Valid @RequestBody AddonGroupCreateRequest body) {
        auth.requireUser(token, "admin");
        return created(menu.createAddonGroup(body.restaurantId(), body.name(), body.minSelect(), body.maxSelect(), body.required()));
    }

    @PatchMapping("/admin/addon-groups/{id}")
    public Map<String, Object> adminUpdateAddonGroup(@CookieValue(value = "foodie_session", required = false) String token,
                                                     @PathVariable @Positive long id, @Valid @RequestBody AddonGroupUpdateRequest body) {
        auth.requireUser(token, "admin");
        return menu.updateAddonGroup(null, id, body.name(), body.minSelect(), body.maxSelect(), body.required());
    }

    @DeleteMapping("/admin/addon-groups/{id}")
    public Map<String, Boolean> adminDeleteAddonGroup(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        auth.requireUser(token, "admin");
        menu.deleteAddonGroup(null, id);
        return Map.of("ok", true);
    }

    @PostMapping("/admin/addon-groups/{id}/addons")
    public ResponseEntity<Map<String, Object>> adminCreateAddon(@CookieValue(value = "foodie_session", required = false) String token,
                                                                @PathVariable @Positive long id, @Valid @RequestBody AddonRequest body) {
        auth.requireUser(token, "admin");
        return created(menu.createAddon(null, id, body.name(), body.priceCents()));
    }

    @PatchMapping("/admin/addon-groups/{id}/addons/{addonId}")
    public Map<String, Object> adminUpdateAddon(@CookieValue(value = "foodie_session", required = false) String token,
                                                @PathVariable @Positive long id, @PathVariable @Positive long addonId, @Valid @RequestBody AddonUpdateRequest body) {
        auth.requireUser(token, "admin");
        return menu.updateAddon(null, id, addonId, body.name(), body.priceCents(), body.available());
    }

    @DeleteMapping("/admin/addon-groups/{id}/addons/{addonId}")
    public Map<String, Boolean> adminDeleteAddon(@CookieValue(value = "foodie_session", required = false) String token,
                                                 @PathVariable @Positive long id, @PathVariable @Positive long addonId) {
        auth.requireUser(token, "admin");
        menu.deleteAddon(null, id, addonId);
        return Map.of("ok", true);
    }

    @GetMapping("/admin/products/{id}/addon-groups")
    public List<Map<String, Object>> adminProductAddonGroups(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id,
                                                             @RequestParam(defaultValue = "0") long variationId) {
        auth.requireUser(token, "admin");
        return menu.productAddonGroups(null, id, variationId);
    }

    @PutMapping("/admin/products/{id}/addon-groups")
    public List<Map<String, Object>> adminSetProductAddonGroups(@CookieValue(value = "foodie_session", required = false) String token,
                                                                @PathVariable @Positive long id, @RequestParam(defaultValue = "0") long variationId,
                                                                @Valid @RequestBody GroupIdsRequest body) {
        auth.requireUser(token, "admin");
        return menu.setProductAddonGroups(null, id, variationId, body.groupIds());
    }

    @GetMapping("/admin/tags")
    public List<Map<String, Object>> adminTags(@CookieValue(value = "foodie_session", required = false) String token,
                                               @RequestParam @Positive long restaurantId) {
        auth.requireUser(token, "admin");
        return menu.tags(restaurantId);
    }

    @PostMapping("/admin/tags")
    public ResponseEntity<Map<String, Object>> adminCreateTag(@CookieValue(value = "foodie_session", required = false) String token,
                                                              @Valid @RequestBody TagCreateRequest body) {
        auth.requireUser(token, "admin");
        return created(menu.createTag(body.restaurantId(), body.name()));
    }

    @DeleteMapping("/admin/tags/{id}")
    public Map<String, Boolean> adminDeleteTag(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        auth.requireUser(token, "admin");
        menu.deleteTag(null, id);
        return Map.of("ok", true);
    }

    @GetMapping("/admin/products/{id}/tags")
    public List<Map<String, Object>> adminProductTags(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        auth.requireUser(token, "admin");
        return menu.productTags(null, id);
    }

    @PutMapping("/admin/products/{id}/tags")
    public List<Map<String, Object>> adminSetProductTags(@CookieValue(value = "foodie_session", required = false) String token,
                                                         @PathVariable @Positive long id, @Valid @RequestBody TagIdsRequest body) {
        auth.requireUser(token, "admin");
        return menu.setProductTags(null, id, body.tagIds());
    }

    @GetMapping("/admin/products/{id}/combo-items")
    public List<Map<String, Object>> adminComboItems(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        auth.requireUser(token, "admin");
        return menu.comboItems(null, id);
    }

    @PutMapping("/admin/products/{id}/combo-items")
    public List<Map<String, Object>> adminSetComboItems(@CookieValue(value = "foodie_session", required = false) String token,
                                                        @PathVariable @Positive long id, @Valid @RequestBody ComboItemsRequest body) {
        auth.requireUser(token, "admin");
        return menu.setComboItems(null, id, body.toItems());
    }

    @GetMapping("/restaurant/catalog")
    public Map<String, Object> ownCatalog(@CookieValue(value = "foodie_session", required = false) String token) {
        return menu.catalog(requireRestaurant(token).restaurantId());
    }

    @GetMapping("/restaurant/products")
    public List<Map<String, Object>> ownProducts(@CookieValue(value = "foodie_session", required = false) String token) {
        return menu.products(requireRestaurant(token).restaurantId());
    }

    @PostMapping("/restaurant/categories")
    public ResponseEntity<Map<String, Object>> ownCategory(@CookieValue(value = "foodie_session", required = false) String token,
                                                           @Valid @RequestBody CategoryRequest body) {
        return created(menu.createCategory(requireRestaurant(token).restaurantId(), body.name()));
    }

    @PatchMapping("/restaurant/categories/{id}")
    public Map<String, Object> ownRenameCategory(@CookieValue(value = "foodie_session", required = false) String token,
                                                 @PathVariable @Positive long id, @Valid @RequestBody CategoryRequest body) {
        return menu.renameCategory(requireRestaurant(token).restaurantId(), id, body.name());
    }

    @DeleteMapping("/restaurant/categories/{id}")
    public Map<String, Boolean> ownDeleteCategory(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        menu.deleteCategory(requireRestaurant(token).restaurantId(), id);
        return Map.of("ok", true);
    }

    @PostMapping("/restaurant/products")
    public ResponseEntity<Map<String, Object>> ownProduct(@CookieValue(value = "foodie_session", required = false) String token,
                                                          @Valid @RequestBody ProductRequest body) {
        return created(menu.createProduct(requireRestaurant(token).restaurantId(), body.categoryId(), body.name(), body.description(), body.priceCents()));
    }

    @PatchMapping("/restaurant/products/{id}")
    public Map<String, Object> ownUpdateProduct(@CookieValue(value = "foodie_session", required = false) String token,
                                                @PathVariable @Positive long id, @Valid @RequestBody ProductUpdateRequest body) {
        return menu.updateProduct(requireRestaurant(token).restaurantId(), id, body.toUpdate());
    }

    @PatchMapping("/restaurant/products/{id}/availability")
    public Map<String, Object> ownAvailability(@CookieValue(value = "foodie_session", required = false) String token,
                                               @PathVariable @Positive long id, @Valid @RequestBody AvailabilityRequest body) {
        return menu.setAvailability(requireRestaurant(token).restaurantId(), id, body.available());
    }

    @DeleteMapping("/restaurant/products/{id}")
    public Map<String, Boolean> ownDeleteProduct(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        menu.deleteProduct(requireRestaurant(token).restaurantId(), id);
        return Map.of("ok", true);
    }

    @GetMapping("/restaurant/products/{id}/variations")
    public List<Map<String, Object>> ownVariations(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        return menu.variations(requireRestaurant(token).restaurantId(), id);
    }

    @PostMapping("/restaurant/products/{id}/variations")
    public ResponseEntity<Map<String, Object>> ownCreateVariation(@CookieValue(value = "foodie_session", required = false) String token,
                                                                  @PathVariable @Positive long id, @Valid @RequestBody VariationRequest body) {
        return created(menu.createVariation(requireRestaurant(token).restaurantId(), id, body.name(), body.priceDeltaCents(), body.sort()));
    }

    @PatchMapping("/restaurant/products/{id}/variations/{variationId}")
    public Map<String, Object> ownUpdateVariation(@CookieValue(value = "foodie_session", required = false) String token,
                                                  @PathVariable @Positive long id, @PathVariable @Positive long variationId,
                                                  @Valid @RequestBody VariationUpdateRequest body) {
        return menu.updateVariation(requireRestaurant(token).restaurantId(), id, variationId, body.name(), body.priceDeltaCents(), body.available(), body.sort());
    }

    @DeleteMapping("/restaurant/products/{id}/variations/{variationId}")
    public Map<String, Boolean> ownDeleteVariation(@CookieValue(value = "foodie_session", required = false) String token,
                                                   @PathVariable @Positive long id, @PathVariable @Positive long variationId) {
        menu.deleteVariation(requireRestaurant(token).restaurantId(), id, variationId);
        return Map.of("ok", true);
    }

    @GetMapping("/restaurant/products/{id}/images")
    public List<Map<String, Object>> ownImages(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        return menu.images(requireRestaurant(token).restaurantId(), id);
    }

    @PutMapping("/restaurant/products/{id}/images")
    public List<Map<String, Object>> ownReplaceImages(@CookieValue(value = "foodie_session", required = false) String token,
                                                      @PathVariable @Positive long id, @Valid @RequestBody ImagesRequest body) {
        return menu.replaceImages(requireRestaurant(token).restaurantId(), id, body.toImages());
    }

    @GetMapping("/restaurant/addon-groups")
    public List<Map<String, Object>> ownAddonGroups(@CookieValue(value = "foodie_session", required = false) String token) {
        return menu.addonGroups(requireRestaurant(token).restaurantId());
    }

    @PostMapping("/restaurant/addon-groups")
    public ResponseEntity<Map<String, Object>> ownCreateAddonGroup(@CookieValue(value = "foodie_session", required = false) String token,
                                                                   @Valid @RequestBody AddonGroupRequest body) {
        return created(menu.createAddonGroup(requireRestaurant(token).restaurantId(), body.name(), body.minSelect(), body.maxSelect(), body.required()));
    }

    @PatchMapping("/restaurant/addon-groups/{id}")
    public Map<String, Object> ownUpdateAddonGroup(@CookieValue(value = "foodie_session", required = false) String token,
                                                   @PathVariable @Positive long id, @Valid @RequestBody AddonGroupUpdateRequest body) {
        return menu.updateAddonGroup(requireRestaurant(token).restaurantId(), id, body.name(), body.minSelect(), body.maxSelect(), body.required());
    }

    @DeleteMapping("/restaurant/addon-groups/{id}")
    public Map<String, Boolean> ownDeleteAddonGroup(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        menu.deleteAddonGroup(requireRestaurant(token).restaurantId(), id);
        return Map.of("ok", true);
    }

    @PostMapping("/restaurant/addon-groups/{id}/addons")
    public ResponseEntity<Map<String, Object>> ownCreateAddon(@CookieValue(value = "foodie_session", required = false) String token,
                                                              @PathVariable @Positive long id, @Valid @RequestBody AddonRequest body) {
        return created(menu.createAddon(requireRestaurant(token).restaurantId(), id, body.name(), body.priceCents()));
    }

    @PatchMapping("/restaurant/addon-groups/{id}/addons/{addonId}")
    public Map<String, Object> ownUpdateAddon(@CookieValue(value = "foodie_session", required = false) String token,
                                              @PathVariable @Positive long id, @PathVariable @Positive long addonId, @Valid @RequestBody AddonUpdateRequest body) {
        return menu.updateAddon(requireRestaurant(token).restaurantId(), id, addonId, body.name(), body.priceCents(), body.available());
    }

    @DeleteMapping("/restaurant/addon-groups/{id}/addons/{addonId}")
    public Map<String, Boolean> ownDeleteAddon(@CookieValue(value = "foodie_session", required = false) String token,
                                               @PathVariable @Positive long id, @PathVariable @Positive long addonId) {
        menu.deleteAddon(requireRestaurant(token).restaurantId(), id, addonId);
        return Map.of("ok", true);
    }

    @GetMapping("/restaurant/products/{id}/addon-groups")
    public List<Map<String, Object>> ownProductAddonGroups(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id,
                                                           @RequestParam(defaultValue = "0") long variationId) {
        return menu.productAddonGroups(requireRestaurant(token).restaurantId(), id, variationId);
    }

    @PutMapping("/restaurant/products/{id}/addon-groups")
    public List<Map<String, Object>> ownSetProductAddonGroups(@CookieValue(value = "foodie_session", required = false) String token,
                                                              @PathVariable @Positive long id, @RequestParam(defaultValue = "0") long variationId,
                                                              @Valid @RequestBody GroupIdsRequest body) {
        return menu.setProductAddonGroups(requireRestaurant(token).restaurantId(), id, variationId, body.groupIds());
    }

    @GetMapping("/restaurant/tags")
    public List<Map<String, Object>> ownTags(@CookieValue(value = "foodie_session", required = false) String token) {
        return menu.tags(requireRestaurant(token).restaurantId());
    }

    @PostMapping("/restaurant/tags")
    public ResponseEntity<Map<String, Object>> ownCreateTag(@CookieValue(value = "foodie_session", required = false) String token,
                                                            @Valid @RequestBody TagRequest body) {
        return created(menu.createTag(requireRestaurant(token).restaurantId(), body.name()));
    }

    @DeleteMapping("/restaurant/tags/{id}")
    public Map<String, Boolean> ownDeleteTag(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        menu.deleteTag(requireRestaurant(token).restaurantId(), id);
        return Map.of("ok", true);
    }

    @GetMapping("/restaurant/products/{id}/tags")
    public List<Map<String, Object>> ownProductTags(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        return menu.productTags(requireRestaurant(token).restaurantId(), id);
    }

    @PutMapping("/restaurant/products/{id}/tags")
    public List<Map<String, Object>> ownSetProductTags(@CookieValue(value = "foodie_session", required = false) String token,
                                                       @PathVariable @Positive long id, @Valid @RequestBody TagIdsRequest body) {
        return menu.setProductTags(requireRestaurant(token).restaurantId(), id, body.tagIds());
    }

    @GetMapping("/restaurant/products/{id}/combo-items")
    public List<Map<String, Object>> ownComboItems(@CookieValue(value = "foodie_session", required = false) String token, @PathVariable @Positive long id) {
        return menu.comboItems(requireRestaurant(token).restaurantId(), id);
    }

    @PutMapping("/restaurant/products/{id}/combo-items")
    public List<Map<String, Object>> ownSetComboItems(@CookieValue(value = "foodie_session", required = false) String token,
                                                      @PathVariable @Positive long id, @Valid @RequestBody ComboItemsRequest body) {
        return menu.setComboItems(requireRestaurant(token).restaurantId(), id, body.toItems());
    }

    private User requireRestaurant(String token) {
        User user = auth.requireUser(token, "restaurant");
        if (user.restaurantId() == null) throw new ApiException(403, "Acesso não autorizado");
        return user;
    }

    private static ResponseEntity<Map<String, Object>> created(Map<String, Object> body) {
        return ResponseEntity.status(201).body(body);
    }

    public record AdminCategoryRequest(@Positive long restaurantId, @NotBlank @Size(min = 2, max = 120) String name) {}
    public record CategoryRequest(@NotBlank @Size(min = 2, max = 120) String name) {}
    public record AdminProductRequest(@Positive long restaurantId, @Positive long categoryId,
                                      @NotBlank @Size(min = 2, max = 160) String name,
                                      @Size(max = 500) String description,
                                      @Min(1) @Max(10_000_000) int priceCents) {}
    public record ProductRequest(@Positive long categoryId,
                                 @NotBlank @Size(min = 2, max = 160) String name,
                                 @Size(max = 500) String description,
                                 @Min(1) @Max(10_000_000) int priceCents) {}
    public record ProductUpdateRequest(@Positive Long categoryId,
                                       @Size(min = 2, max = 160) String name,
                                       @Size(max = 500) String description,
                                       @Min(1) @Max(10_000_000) Integer priceCents,
                                       Boolean available,
                                       Boolean isCombo,
                                       @Min(-1) @Max(100_000) Integer stock,
                                       @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$") String availableFrom,
                                       @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$") String availableUntil) {
        public MenuService.ProductUpdate toUpdate() {
            return new MenuService.ProductUpdate(categoryId, name, description, priceCents, available, isCombo, stock, availableFrom, availableUntil);
        }
    }
    public record AvailabilityRequest(@NotNull Boolean available) {}

    public record VariationRequest(@NotBlank @Size(min = 1, max = 80) String name,
                                   @Min(0) @Max(10_000_000) int priceDeltaCents,
                                   @Min(0) @Max(999) Integer sort) {}

    public record VariationUpdateRequest(@Size(min = 1, max = 80) String name,
                                         @Min(0) @Max(10_000_000) Integer priceDeltaCents,
                                         Boolean available,
                                         @Min(0) @Max(999) Integer sort) {}

    public record ImagesRequest(@NotNull @Size(max = 8) List<@Valid ImageItem> images) {
        public List<MenuService.Image> toImages() {
            return images.stream().map((image) -> new MenuService.Image(image.url(), image.cover())).toList();
        }
    }

    public record ImageItem(@NotBlank @Size(max = 512) String url, Boolean cover) {}

    public record AddonGroupCreateRequest(@Positive long restaurantId,
                                          @NotBlank @Size(min = 1, max = 80) String name,
                                          @Min(0) @Max(20) int minSelect,
                                          @Min(1) @Max(20) int maxSelect,
                                          boolean required) {}

    public record AddonGroupRequest(@NotBlank @Size(min = 1, max = 80) String name,
                                    @Min(0) @Max(20) int minSelect,
                                    @Min(1) @Max(20) int maxSelect,
                                    boolean required) {}

    public record AddonGroupUpdateRequest(@Size(min = 1, max = 80) String name,
                                          @Min(0) @Max(20) Integer minSelect,
                                          @Min(1) @Max(20) Integer maxSelect,
                                          Boolean required) {}

    public record AddonRequest(@NotBlank @Size(min = 1, max = 80) String name,
                               @Min(0) @Max(10_000_000) int priceCents) {}

    public record AddonUpdateRequest(@Size(min = 1, max = 80) String name,
                                     @Min(0) @Max(10_000_000) Integer priceCents,
                                     Boolean available) {}

    public record GroupIdsRequest(@NotNull @Size(max = 20) List<@Positive Long> groupIds) {}

    public record TagCreateRequest(@Positive long restaurantId, @NotBlank @Size(min = 1, max = 60) String name) {}

    public record TagRequest(@NotBlank @Size(min = 1, max = 60) String name) {}

    public record TagIdsRequest(@NotNull @Size(max = 20) List<@Positive Long> tagIds) {}

    public record ComboItemsRequest(@NotNull @Size(max = 30) List<@Valid ComboItemRequest> items) {
        public List<MenuService.ComboItem> toItems() {
            return items.stream().map((item) -> new MenuService.ComboItem(item.componentProductId(), item.quantity())).toList();
        }
    }

    public record ComboItemRequest(@Positive long componentProductId, @Min(1) @Max(20) Integer quantity) {}
}
