package com.foodie.api.catalog;

import com.foodie.api.ApiException;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.User;
import com.foodie.api.permissions.PermissionService;
import com.foodie.api.permissions.Permissions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Importação em massa do catálogo da própria loja (E23). */
@RestController
public class CatalogImportController {
    private final AuthService auth;
    private final PermissionService permissions;
    private final CatalogImportService imports;

    public CatalogImportController(AuthService auth, PermissionService permissions, CatalogImportService imports) {
        this.auth = auth;
        this.permissions = permissions;
        this.imports = imports;
    }

    @PostMapping("/restaurant/catalog/import")
    public CatalogImportService.Result importCsv(@CookieValue(value = "foodie_session", required = false) String token,
                                                 @RequestParam(defaultValue = "false") boolean dryRun,
                                                 @Valid @RequestBody ImportRequest body) {
        User user = auth.requireUser(token, "restaurant");
        if (user.restaurantId() == null) throw new ApiException(403, "Acesso não autorizado");
        permissions.require(user, Permissions.CATALOG_MANAGE);
        return imports.importCsv(user.restaurantId(), body.csv(), dryRun);
    }

    public record ImportRequest(@NotBlank @Size(max = 200_000) String csv) {}
}
