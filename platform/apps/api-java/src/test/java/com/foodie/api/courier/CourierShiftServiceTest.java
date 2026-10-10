package com.foodie.api.courier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

@SuppressWarnings("unchecked")
class CourierShiftServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-10T15:00:00Z");
    private JdbcTemplate jdbc;
    private CourierShiftService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new CourierShiftService(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void courier(Long restaurantId, boolean approved, boolean suspended) {
        Map<String, Object> row = new HashMap<>();
        row.put("restaurant_id", restaurantId);
        row.put("courier_approved_at", approved ? Timestamp.from(NOW) : null);
        row.put("suspended_at", suspended ? Timestamp.from(NOW) : null);
        when(jdbc.queryForMap(startsWith("SELECT restaurant_id, courier_approved_at, suspended_at"), any(Object[].class))).thenReturn(row);
    }

    @Test
    void openCreatesTheShiftWithTheCourierStore() {
        courier(3L, true, false);
        when(jdbc.queryForList(eq(CourierShiftService.OPEN_SINCE), eq(Timestamp.class), any(Object[].class)))
            .thenReturn(List.of(), List.of(Timestamp.from(NOW)));

        CourierShiftService.Opened opened = service.open(9);

        assertThat(opened.created()).isTrue();
        assertThat(opened.shift()).containsEntry("open", true).containsEntry("startedAt", "2026-10-10T15:00:00Z");
        verify(jdbc).update(CourierShiftService.INSERT, 9L, 3L, Timestamp.from(NOW));
    }

    @Test
    void openReturnsTheShiftAlreadyOpen() {
        courier(3L, true, false);
        when(jdbc.queryForList(eq(CourierShiftService.OPEN_SINCE), eq(Timestamp.class), any(Object[].class)))
            .thenReturn(List.of(Timestamp.from(NOW.minusSeconds(600))));

        CourierShiftService.Opened opened = service.open(9);

        assertThat(opened.created()).isFalse();
        verify(jdbc, never()).update(eq(CourierShiftService.INSERT), any(Object[].class));
    }

    @Test
    void openLosingTheRaceReturnsTheShiftThatWon() {
        courier(3L, true, false);
        when(jdbc.queryForList(eq(CourierShiftService.OPEN_SINCE), eq(Timestamp.class), any(Object[].class)))
            .thenReturn(List.of(), List.of(Timestamp.from(NOW)));
        when(jdbc.update(eq(CourierShiftService.INSERT), any(Object[].class))).thenThrow(new DuplicateKeyException("uq_courier_shifts_open"));

        CourierShiftService.Opened opened = service.open(9);

        assertThat(opened.created()).isFalse();
        assertThat(opened.shift()).containsEntry("open", true);
    }

    @Test
    void openRefusesCourierWithoutStoreApprovalOrSuspended() {
        courier(null, true, false);
        assertThatThrownBy(() -> service.open(9)).isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.status()).isEqualTo(409));
        courier(3L, false, false);
        assertThatThrownBy(() -> service.open(9)).isInstanceOf(ApiException.class);
        courier(3L, true, true);
        assertThatThrownBy(() -> service.open(9)).isInstanceOf(ApiException.class);
        verify(jdbc, never()).update(eq(CourierShiftService.INSERT), any(Object[].class));
    }

    @Test
    void closeWithoutDeliveryClearsThePosition() {
        when(jdbc.queryForObject(CourierShiftService.ACTIVE_COUNT, Long.class, 9L)).thenReturn(0L);

        Map<String, Object> result = service.close(9);

        assertThat(result).containsEntry("ok", true).containsEntry("keepsSharing", false);
        verify(jdbc).update(CourierShiftService.CLOSE, Timestamp.from(NOW), 9L);
        verify(jdbc).update(CourierShiftService.DELETE_LOCATION, 9L);
    }

    @Test
    void closeWithADeliveryKeepsThePosition() {
        when(jdbc.queryForObject(CourierShiftService.ACTIVE_COUNT, Long.class, 9L)).thenReturn(2L);

        assertThat(service.close(9)).containsEntry("keepsSharing", true);
        verify(jdbc, never()).update(CourierShiftService.DELETE_LOCATION, 9L);
    }

    @Test
    void suspensionClosesTheShiftAndAlwaysClearsThePosition() {
        service.closeForSuspension(9);
        verify(jdbc).update(CourierShiftService.CLOSE, Timestamp.from(NOW), 9L);
        verify(jdbc).update(CourierShiftService.DELETE_LOCATION, 9L);
    }

    @Test
    void closeStaleClosesShiftsOlderThanTwelveHoursAndClearsOrphanPositions() {
        when(jdbc.update(eq(CourierShiftService.CLOSE_STALE), any(Object[].class))).thenReturn(2);

        assertThat(service.closeStale()).isEqualTo(2);
        verify(jdbc).update(CourierShiftService.CLOSE_STALE, Timestamp.from(Instant.parse("2026-10-10T03:00:00Z")));
        verify(jdbc).update(CourierShiftService.DELETE_ORPHAN_LOCATIONS);
    }

    @Test
    void supportOpensWithTheCourierStoreOrReturnsTheOpenShift() {
        when(jdbc.queryForList(CourierShiftService.OPEN_IDS, Long.class, 12L)).thenReturn(List.of(), List.of(40L));
        when(jdbc.queryForObject("SELECT restaurant_id FROM users WHERE id = ?", Long.class, 12L)).thenReturn(null);

        assertThat(service.openBySupport(12)).isEqualTo(new CourierShiftService.SupportShift(40L, true));
        verify(jdbc).update(CourierShiftService.INSERT, 12L, null, Timestamp.from(NOW));

        when(jdbc.queryForList(CourierShiftService.OPEN_IDS, Long.class, 13L)).thenReturn(List.of(41L));
        assertThat(service.openBySupport(13)).isEqualTo(new CourierShiftService.SupportShift(41L, false));
    }

    @Test
    void boardReadsTheStoreAndOnlyItsActiveCouriers() {
        when(jdbc.queryForMap(startsWith("SELECT latitude, longitude FROM restaurants"), any(Object[].class)))
            .thenReturn(Map.of("latitude", new BigDecimal("-3.7319000"), "longitude", new BigDecimal("-38.5267000")));
        when(jdbc.query(eq(CourierShiftService.BOARD), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(
            new CourierBoard.Row(5, "Bia", NOW.minusSeconds(60), 0, -3.7329, -38.5267, NOW)));

        Map<String, Object> board = service.board(3);

        assertThat((Map<String, Object>) board.get("restaurant")).containsEntry("latitude", -3.7319);
        assertThat((List<Map<String, Object>>) board.get("couriers")).singleElement()
            .satisfies(item -> assertThat(item).containsEntry("status", "available").containsEntry("suggested", true));
        verify(jdbc).query(eq(CourierShiftService.BOARD), any(RowMapper.class), eq(3L));
        assertThat(CourierShiftService.BOARD).contains("u.restaurant_id = ? AND u.suspended_at IS NULL");
    }

    @Test
    void historyValidatesThePeriodAndTheStore() {
        assertThatThrownBy(() -> service.history(3, 12, 15)).isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.status()).isEqualTo(400));
        when(jdbc.query(startsWith("SELECT 1 FROM users"), any(ResultSetExtractor.class), any(Object[].class))).thenReturn(null);
        assertThatThrownBy(() -> service.history(3, 12, 7)).isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.status()).isEqualTo(404));
    }

    @Test
    void historySumsMinutesCountingTheOpenShiftUntilNow() {
        when(jdbc.query(startsWith("SELECT 1 FROM users"), any(ResultSetExtractor.class), any(Object[].class))).thenReturn(1);
        Map<String, Object> open = new HashMap<>();
        open.put("started_at", Timestamp.from(NOW.minusSeconds(3600)));
        open.put("ended_at", null);
        Map<String, Object> closed = new HashMap<>();
        closed.put("started_at", Timestamp.from(NOW.minusSeconds(26 * 3600)));
        closed.put("ended_at", Timestamp.from(NOW.minusSeconds(24 * 3600)));
        when(jdbc.queryForList(contains("FROM courier_shifts WHERE courier_id = ? AND restaurant_id = ?"), any(Object[].class)))
            .thenReturn(List.of(open, closed));

        Map<String, Object> history = service.history(3, 12, 7);

        assertThat(history).containsEntry("totalMinutes", 180L);
        List<Map<String, Object>> shifts = (List<Map<String, Object>>) history.get("shifts");
        assertThat(shifts.get(0)).containsEntry("minutes", 60L).containsEntry("endedAt", null);
        assertThat(shifts.get(1)).containsEntry("minutes", 120L);
        verify(jdbc).queryForList(contains("FROM courier_shifts WHERE courier_id = ? AND restaurant_id = ?"),
            eq(12L), eq(3L), eq(Timestamp.from(Instant.parse("2026-10-03T15:00:00Z"))));
    }
}
