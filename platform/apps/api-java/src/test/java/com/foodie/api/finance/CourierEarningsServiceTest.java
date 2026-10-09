package com.foodie.api.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import com.foodie.api.courier.DailySeries;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

class CourierEarningsServiceTest {
    private JdbcTemplate jdbc;
    private CourierEarningsService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new CourierEarningsService(jdbc, Clock.fixed(Instant.parse("2026-10-07T17:00:00Z"), ZoneOffset.UTC));
        when(jdbc.query(contains("SELECT r.timezone"), any(ResultSetExtractor.class), any(Object[].class))).thenReturn("America/Fortaleza");
    }

    @Test
    @SuppressWarnings("unchecked")
    void dailyReturnsOneItemPerDayWithZerosAndValuesOnTheLocalDay() {
        // 2026-10-06T02:30Z = 05/10 23:30 em Fortaleza (dia local anterior)
        when(jdbc.query(contains("SELECT o.id, o.delivery_fee_cents, o.tip_cents, MAX(e.created_at) AS delivered_at"),
            any(RowMapper.class), any(Object[].class)))
            .thenReturn(List.of(
                new DailySeries.Row(Instant.parse("2026-10-07T15:00:00Z"), 500, 100),
                new DailySeries.Row(Instant.parse("2026-10-06T02:30:00Z"), 300, 0)));

        List<Map<String, Object>> series = service.daily(9, 7);

        assertThat(series).hasSize(7);
        assertThat(series.get(0)).containsEntry("date", "2026-10-01").containsEntry("count", 0L).containsEntry("deliveryFeeCents", 0L);
        assertThat(series.get(4)).containsEntry("date", "2026-10-05").containsEntry("deliveryFeeCents", 300L).containsEntry("count", 1L);
        assertThat(series.get(6)).containsEntry("date", "2026-10-07").containsEntry("deliveryFeeCents", 500L).containsEntry("tipCents", 100L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void dailyOnlyCountsPaidDeliveredOrders() {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        service.daily(9, 30);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(sql.getValue()).contains("p.status = 'paid'").contains("o.status = 'delivered'");
    }

    @Test
    void dailyRejectsUnsupportedPeriods() {
        assertThatThrownBy(() -> service.daily(9, 15)).isInstanceOf(ApiException.class).hasMessage("Período inválido");
    }
}
