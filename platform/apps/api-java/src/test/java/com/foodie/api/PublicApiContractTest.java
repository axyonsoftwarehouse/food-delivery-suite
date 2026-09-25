package com.foodie.api;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;
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
        when(catalog.coverage()).thenReturn(List.of(Map.of("restaurant_id", 1, "zone_id", 4)));

        mvc.perform(get("/catalog"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.restaurants[0].slug").value("cozinha-demo"))
            .andExpect(jsonPath("$.categories[0].restaurant_id").value(1))
            .andExpect(jsonPath("$.products[0].price_cents").value(2990))
            .andExpect(jsonPath("$.coverage[0].zone_id").value(4));
    }

    @Test
    void zonesKeepPrototypeResponseShape() throws Exception {
        when(catalog.zones()).thenReturn(List.of(Map.of("id", 4, "name", "Fortaleza", "city", "Fortaleza", "state", "CE", "delivery_fee_cents", 599, "minimum_order_cents", 1500)));

        mvc.perform(get("/zones"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].delivery_fee_cents").value(599))
            .andExpect(jsonPath("$[0].minimum_order_cents").value(1500));
    }

    @Test
    void postalCodeResolvesZoneAndRejectsUncoveredAddress() throws Exception {
        when(postalCoverage.resolve("60000001")).thenReturn(Map.of("id", 4, "name", "Fortaleza", "city", "Fortaleza", "state", "CE", "delivery_fee_cents", 599, "minimum_order_cents", 1500));
        when(postalCoverage.resolve("99999999")).thenThrow(new ApiException(404, "Ainda não entregamos neste CEP"));

        mvc.perform(get("/zones/resolve").param("postalCode", "60000001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(4));
        mvc.perform(get("/zones/resolve").param("postalCode", "99999999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("Ainda não entregamos neste CEP"));
        verify(postalCoverage).resolve("60000001");
    }

    @Test
    void customerCatalogUsesPagedSearchAndRejectsInvalidLimits() throws Exception {
        when(catalog.restaurants()).thenReturn(List.of());
        when(catalog.categories()).thenReturn(List.of());
        when(catalog.coverage()).thenReturn(List.of());
        when(catalog.search(eq(4L), eq("pizza"), eq(null), eq(null), eq(1)))
            .thenReturn(new CatalogRepository.SearchPage(List.of(Map.of("id", 9, "name", "Pizza")), 9L));

        mvc.perform(get("/catalog/meta"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.products").isEmpty());
        mvc.perform(get("/catalog/search").param("zoneId", "4").param("q", "pizza").param("limit", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].name").value("Pizza"))
            .andExpect(jsonPath("$.nextCursor").value(9));
        mvc.perform(get("/catalog/search").param("zoneId", "4").param("limit", "31"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void healthChecksDatabase() throws Exception {
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        mvc.perform(get("/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"));
    }
}
