package com.foodie.api;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodie.api.catalog.CatalogController;
import com.foodie.api.catalog.CatalogRepository;
import com.foodie.api.catalog.PostalCoverageService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({CatalogController.class, HealthController.class})
class PublicApiContractTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CatalogRepository catalog;

    @MockitoBean
    private PostalCoverageService postalCoverage;

    @MockitoBean
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void publicCatalogKeepsPrototypeResponseShape() throws Exception {
        when(catalog.restaurants()).thenReturn(List.of(Map.of("id", 1, "name", "Cozinha Demo", "slug", "cozinha-demo")));
        when(catalog.categories()).thenReturn(List.of(Map.of("id", 2, "restaurant_id", 1, "name", "Pratos")));
        when(catalog.products()).thenReturn(List.of(Map.of("id", 3, "restaurant_id", 1, "category_id", 2, "name", "Prato", "description", "", "price_cents", 2990)));
        // A taxa, o mínimo e o tempo são da loja em cada bairro (taxa por bairro, 09/11).
        when(catalog.coverage()).thenReturn(List.of(Map.of("restaurant_id", 1, "zone_id", 4, "fee_cents", 599, "minimum_order_cents", 1500, "delivery_minutes", 25)));

        mvc.perform(get("/catalog"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.restaurants[0].slug").value("cozinha-demo"))
            .andExpect(jsonPath("$.categories[0].restaurant_id").value(1))
            .andExpect(jsonPath("$.products[0].price_cents").value(2990))
            .andExpect(jsonPath("$.coverage[0].zone_id").value(4))
            .andExpect(jsonPath("$.coverage[0].fee_cents").value(599))
            .andExpect(jsonPath("$.coverage[0].minimum_order_cents").value(1500))
            .andExpect(jsonPath("$.coverage[0].delivery_minutes").value(25));
    }

    @Test
    void zonesAreNeighborhoodsWithoutPrice() throws Exception {
        when(catalog.zones()).thenReturn(List.of(Map.of("id", 4, "name", "Centro", "city", "Fortaleza", "state", "CE")));

        mvc.perform(get("/zones"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Centro"))
            .andExpect(jsonPath("$[0].city").value("Fortaleza"));
    }

    @Test
    void postalCodeResolvesTheNeighborhoodAndRejectsUnknownCep() throws Exception {
        when(postalCoverage.resolve(eq("60000001"), any())).thenReturn(Map.of("id", 4, "name", "Centro", "city", "Fortaleza", "state", "CE", "covered", true));
        when(postalCoverage.resolve(eq("99999999"), any())).thenThrow(new ApiException(404, "CEP não encontrado. Confira os números"));

        mvc.perform(get("/zones/resolve").param("postalCode", "60000001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(4))
            .andExpect(jsonPath("$.covered").value(true));
        mvc.perform(get("/zones/resolve").param("postalCode", "99999999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("CEP não encontrado. Confira os números"));
        verify(postalCoverage).resolve(eq("60000001"), any());
    }

    @Test
    void customerCatalogUsesPagedSearchAndRejectsInvalidLimits() throws Exception {
        when(catalog.restaurants()).thenReturn(List.of());
        when(catalog.categories()).thenReturn(List.of());
        when(catalog.coverage()).thenReturn(List.of());
        when(catalog.search(eq(4L), eq("pizza"), eq(2L), eq(null), eq(null), eq(null), eq(1)))
            .thenReturn(new CatalogRepository.SearchPage(List.of(Map.of("id", 9, "name", "Pizza")), 9L));

        mvc.perform(get("/catalog/meta"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.products").isEmpty());
        mvc.perform(get("/catalog/search").param("zoneId", "4").param("restaurantId", "2").param("q", "pizza").param("limit", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].name").value("Pizza"))
            .andExpect(jsonPath("$.nextCursor").value(9));
        mvc.perform(get("/catalog/search").param("zoneId", "4").param("limit", "31"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/catalog/search").param("zoneId", "4").param("restaurantId", "0"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void searchWithoutZoneButWithRestaurantIsPublic() throws Exception {
        when(catalog.search(isNull(), eq(""), eq(3L), isNull(), isNull(), isNull(), eq(12)))
            .thenReturn(new CatalogRepository.SearchPage(List.of(Map.of("id", 9, "name", "Pizza")), null));

        mvc.perform(get("/catalog/search").param("restaurantId", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].name").value("Pizza"));
        verify(catalog).search(isNull(), eq(""), eq(3L), isNull(), isNull(), isNull(), eq(12));
    }

    @Test
    void searchWithoutZoneAndRestaurantIs400() throws Exception {
        mvc.perform(get("/catalog/search")).andExpect(status().isBadRequest());
    }

    @Test
    void tagsWithoutZoneButWithRestaurantIsPublic() throws Exception {
        when(catalog.tags(isNull(), eq(3L))).thenReturn(List.of(Map.of("id", 1, "name", "Vegano")));

        mvc.perform(get("/catalog/tags").param("restaurantId", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Vegano"));
        verify(catalog).tags(isNull(), eq(3L));
    }

    @Test
    void productDetailReturnsImagesAndVariations() throws Exception {
        when(catalog.productDetail(9L)).thenReturn(Map.of(
            "id", 9L, "name", "Pizza",
            "images", List.of(Map.of("id", 4L, "url", "https://cdn.foodie.local/p.png", "is_cover", true)),
            "variations", List.of(Map.of("id", 3L, "name", "Grande", "price_delta_cents", 500))));

        mvc.perform(get("/catalog/products/9"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.variations[0].price_delta_cents").value(500))
            .andExpect(jsonPath("$.images[0].is_cover").value(true));
        when(catalog.productDetail(404L)).thenReturn(null);
        mvc.perform(get("/catalog/products/404")).andExpect(status().isNotFound());
    }

    @Test
    void healthChecksDatabase() throws Exception {
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        mvc.perform(get("/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void readinessRequiresMigratedSchema() throws Exception {
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        when(jdbc.query(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.RowMapper.class)))
            .thenReturn(List.of("12"));

        mvc.perform(get("/ready"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ready"))
            .andExpect(jsonPath("$.schemaVersion").value("12"));
    }

    @Test
    void readinessFailsWithoutSchemaHistory() throws Exception {
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        mvc.perform(get("/ready")).andExpect(status().isServiceUnavailable());
    }
}
