package com.foodie.api.catalog;

import java.util.List;
import java.util.Map;
import com.foodie.api.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CatalogController {
    private final CatalogRepository catalog;
    private final PostalCoverageService postalCoverage;

    public CatalogController(CatalogRepository catalog, PostalCoverageService postalCoverage) {
        this.catalog = catalog;
        this.postalCoverage = postalCoverage;
    }

    @GetMapping("/catalog")
    public Map<String, Object> catalog() {
        return Map.of(
            "restaurants", catalog.restaurants(),
            "categories", catalog.categories(),
            "products", catalog.products(),
            "coverage", catalog.coverage()
        );
    }

    @GetMapping("/catalog/meta")
    public Map<String, Object> metadata() {
        return Map.of(
            "restaurants", catalog.restaurants(),
            "categories", catalog.categories(),
            "products", List.of(),
            "coverage", catalog.coverage()
        );
    }

    @GetMapping("/catalog/products/{id}")
    public Map<String, Object> product(@PathVariable @Positive long id) {
        Map<String, Object> product = catalog.productDetail(id);
        if (product == null) throw new ApiException(404, "Produto não encontrado");
        return product;
    }

    @GetMapping("/catalog/search")
    public CatalogRepository.SearchPage search(
        @RequestParam(required = false) Long zoneId,
        @RequestParam(defaultValue = "") String q,
        @RequestParam(required = false) Long restaurantId,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(required = false) Long tagId,
        @RequestParam(required = false) Long after,
        @RequestParam(defaultValue = "12") int limit
    ) {
        if (zoneId == null && restaurantId == null || zoneId != null && zoneId < 1 || restaurantId != null && restaurantId < 1 || categoryId != null && categoryId < 1 || tagId != null && tagId < 1 || after != null && after < 1
            || limit < 1 || limit > 30 || q.strip().length() > 80) {
            throw new ApiException(400, "Filtros de catálogo inválidos");
        }
        return catalog.search(zoneId, q.strip(), restaurantId, categoryId, tagId, after, limit);
    }

    @GetMapping("/catalog/tags")
    public List<Map<String, Object>> tags(@RequestParam(required = false) Long zoneId, @RequestParam(required = false) Long restaurantId) {
        if (zoneId == null && restaurantId == null || zoneId != null && zoneId < 1 || restaurantId != null && restaurantId < 1) throw new ApiException(400, "Filtro de catálogo inválido");
        return catalog.tags(zoneId, restaurantId);
    }

    @GetMapping("/zones")
    public List<Map<String, Object>> zones() {
        return catalog.zones();
    }

    /** Bairro do CEP (ou as opções da cidade, quando o CEP não traz bairro). A taxa vem da loja, em {@code coverage}. */
    @GetMapping("/zones/resolve")
    public Map<String, Object> resolveZone(@RequestParam String postalCode, HttpServletRequest request) {
        return postalCoverage.resolve(postalCode, clientIp(request));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? null : remote;
    }
}
