package com.foodie.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ReportsServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ReportsService service = new ReportsService(jdbc);

    @Test
    void earningsAggregateByPeriod() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
            Map.of("period", "2026-09-01", "kind", "sale", "total", 10000),
            Map.of("period", "2026-09-01", "kind", "commission", "total", -1000),
            Map.of("period", "2026-09-02", "kind", "sale", "total", 5000)));

        Map<String, Object> result = service.earnings("admin", "day", "2026-09-01", "2026-09-02");

        @SuppressWarnings("unchecked")
        Map<String, Object> totals = (Map<String, Object>) result.get("totals");
        assertThat(totals.get("saleCents")).isEqualTo(15000L);
        assertThat(totals.get("commissionCents")).isEqualTo(-1000L);
        assertThat(totals.get("netCents")).isEqualTo(14000L);
        assertThat((List<?>) result.get("buckets")).hasSize(2);
    }

    @Test
    void rejectsInvalidScopeAndGrouping() {
        assertThatThrownBy(() -> service.earnings("nobody", "day", null, null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.earnings("admin", "hour", null, null)).isInstanceOf(ApiException.class);
    }
}
