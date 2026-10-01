package com.foodie.api.catalog;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.permissions.PermissionService;
import com.foodie.api.permissions.Permissions;
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
    private final PermissionService permissions;

    public MenuController(AuthService auth, MenuService menu, PermissionService permissions) {
        this.auth = auth;
        this.menu = menu;
        this.permissions = permissions;
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
        permissions.require(user, Permissions.CATALOG_MANAGE);
        return user;
    }

    private static ResponseEntity<Map<String, Object>> created(Map<String, Object> body) {
        return ResponseEntity.status(201).body(body);
    }

    public record CategoryRequest(@NotBlank @Size(min = 2, max = 120) String name) {}
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

    public record TagRequest(@NotBlank @Size(min = 1, max = 60) String name) {}

    public record TagIdsRequest(@NotNull @Size(max = 20) List<@Positive Long> tagIds) {}

    public record ComboItemsRequest(@NotNull @Size(max = 30) List<@Valid ComboItemRequest> items) {
        public List<MenuService.ComboItem> toItems() {
            return items.stream().map((item) -> new MenuService.ComboItem(item.componentProductId(), item.quantity())).toList();
        }
    }

    public record ComboItemRequest(@Positive long componentProductId, @Min(1) @Max(20) Integer quantity) {}
}
