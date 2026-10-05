package com.foodie.api.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

class CampaignServiceTest {
    private final JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
    private final CampaignService campaigns = new CampaignService(jdbc);

    @Test
    void choosesLargestEligibleDiscountWithoutApplyingUnrelatedItem() {
        when(jdbc.queryForList(anyString(), eq(7L))).thenReturn(List.of(
            Map.of("id", 1L, "name", "Geral", "type", "basic", "percent", new BigDecimal("10.00")),
            Map.of("id", 2L, "name", "Prato", "type", "item", "percent", new BigDecimal("30.00"), "product_id", 3L),
            Map.of("id", 3L, "name", "Outro", "type", "item", "percent", new BigDecimal("90.00"), "product_id", 99L)));
        CampaignService.Applied applied = campaigns.best(7, List.of(
            new CampaignService.Line(3, 4000), new CampaignService.Line(4, 2000)));
        assertEquals(2L, applied.campaignId());
        assertEquals(1200L, applied.discountCents());
    }

    @Test
    void returnsNoCampaignForEmptyCart() {
        assertNull(campaigns.best(7, List.of()));
    }

    @Test
    void onlyQueriesCampaignsOfTheOrderRestaurant() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        when(jdbc.queryForList(sql.capture(), eq(7L))).thenReturn(List.of());
        campaigns.best(7, List.of(new CampaignService.Line(3, 4000)));
        assertTrue(sql.getValue().contains("restaurant_id = ?"));
        assertFalse(sql.getValue().contains("restaurant_id IS NULL"));
    }
}
