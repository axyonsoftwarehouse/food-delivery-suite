package com.foodie.api.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PostalCoverageServiceTest {
    private PostalCoverageService serviceWith(List<Map<String, Object>> rows) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(rows);
        return new PostalCoverageService(jdbc);
    }

    private Map<String, Object> zone(int fixed, Integer base, Integer perKm) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 1L);
        row.put("name", "Centro");
        row.put("city", "Cidade");
        row.put("state", "SP");
        row.put("delivery_fee_cents", fixed);
        row.put("minimum_order_cents", 2000);
        row.put("delivery_minutes", 40);
        row.put("base_fee_cents", base);
        row.put("per_km_cents", perKm);
        return row;
    }

    @Test
    void fixedZoneMinimumIsTheFixedFee() {
        Map<String, Object> out = serviceWith(List.of(zone(599, null, null))).resolve("01001-000");
        assertEquals(599L, ((Number) out.get("minimum_fee_cents")).longValue());
        assertFalse(out.containsKey("base_fee_cents"));
        assertFalse(out.containsKey("per_km_cents"));
        assertEquals(599, out.get("delivery_fee_cents"));
    }

    @Test
    void perKmZoneMinimumIsBasePlusOneKm() {
        Map<String, Object> out = serviceWith(List.of(zone(599, 300, 150))).resolve("01001000");
        assertEquals(450L, ((Number) out.get("minimum_fee_cents")).longValue());
    }

    @Test
    void perKmZoneWithoutBaseUsesFixedPlusOneKm() {
        Map<String, Object> out = serviceWith(List.of(zone(599, null, 150))).resolve("01001000");
        assertEquals(749L, ((Number) out.get("minimum_fee_cents")).longValue());
    }

    @Test
    void unknownPostalCodeIs404() {
        ApiException e = assertThrows(ApiException.class, () -> serviceWith(List.of()).resolve("01001000"));
        assertEquals(404, e.status());
        assertEquals("Ainda não entregamos neste CEP", e.getMessage());
    }
}
