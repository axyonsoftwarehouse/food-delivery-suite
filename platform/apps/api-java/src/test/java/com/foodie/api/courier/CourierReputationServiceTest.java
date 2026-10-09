package com.foodie.api.courier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class CourierReputationServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CourierReputationService service = new CourierReputationService(jdbc);

    private void data(long count, long sum) {
        when(jdbc.queryForMap(startsWith("SELECT COUNT(*) AS count, COALESCE(SUM(rating)"), any(Object[].class))).thenReturn(Map.of("count", count, "sum", sum));
        when(jdbc.queryForMap(startsWith("SELECT COALESCE(SUM(status = 'delivered')"), any(Object[].class))).thenReturn(Map.of("completed", 12L, "failed", 1L));
        when(jdbc.queryForList(startsWith("SELECT rating, comment, order_id, created_at"), any(Object[].class)))
            .thenReturn(List.of(Map.of("rating", 5, "comment", "rápido", "order_id", 40L)));
    }

    @Test
    void hidesTheAverageBelowFiveButKeepsTheCounts() {
        data(2, 9);
        Map<String, Object> reputation = service.forCourier(9);
        assertThat(reputation).containsEntry("average", null).containsEntry("count", 2L).containsEntry("minReviewsToShow", 5)
            .containsEntry("completed30d", 12L).containsEntry("failed30d", 1L);
    }

    @Test
    void showsTheAverageFromFiveAndNeverCarriesTheCustomer() {
        data(5, 24);
        Map<String, Object> reputation = service.forCourier(9);
        assertThat(reputation).containsEntry("average", 4.8);
        assertThat(reputation.get("recent").toString()).doesNotContain("customer");
    }
}
