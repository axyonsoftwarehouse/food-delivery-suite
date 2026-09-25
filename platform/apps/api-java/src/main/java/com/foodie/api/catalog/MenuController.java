package com.foodie.api.catalog;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
import org.springframework.web.bind.annotation.RequestBody;
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

    private User requireRestaurant(String token) {
        User user = auth.requireUser(token, "restaurant");
        if (user.restaurantId() == null) throw new ApiException(403, "Acesso nÃ£o autorizado");
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
                                       Boolean available) {
        public MenuService.ProductUpdate toUpdate() {
            return new MenuService.ProductUpdate(categoryId, name, description, priceCents, available);
        }
    }
    public record AvailabilityRequest(@NotNull Boolean available) {}
}
