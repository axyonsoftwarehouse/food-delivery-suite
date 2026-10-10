package com.foodie.api.courier;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CourierBoardTest {
    private static final Instant NOW = Instant.parse("2026-10-10T15:00:00Z");
    private static final double STORE_LAT = -3.7319;
    private static final double STORE_LNG = -38.5267;

    private static CourierBoard.Row row(long id, String name, boolean onShift, long active, Double lat, Double lng, Instant at) {
        return new CourierBoard.Row(id, name, onShift ? NOW.minusSeconds(3600) : null, active, lat, lng, at);
    }

    @Test
    void ordersByGroupThenDistanceAndSuggestsTheNearestAvailable() {
        List<Map<String, Object>> board = CourierBoard.build(List.of(
            row(1, "Davi", false, 0, null, null, null),
            row(2, "Bruno", true, 1, -3.7400, STORE_LNG, NOW),
            row(3, "Ana", true, 0, -3.7419, STORE_LNG, NOW.minusSeconds(30)),
            row(4, "Caio", true, 0, null, null, null),
            row(5, "Bia", true, 0, -3.7329, STORE_LNG, NOW)), STORE_LAT, STORE_LNG, NOW);

        assertThat(board).extracting(item -> item.get("name")).containsExactly("Bia", "Ana", "Bruno", "Caio", "Davi");
        assertThat(board).extracting(item -> item.get("suggested")).containsExactly(true, false, false, false, false);
        assertThat((Long) board.get(0).get("distanceMeters")).isBetween(100L, 125L);
        assertThat(board.get(2)).containsEntry("status", "delivering").containsEntry("activeDeliveries", 1L);
        assertThat(board.get(3)).containsEntry("status", "no_signal").containsEntry("latitude", null);
        assertThat(board.get(4)).containsEntry("status", "off_shift").containsEntry("shiftStartedAt", null);
        assertThat(board.get(0)).containsEntry("shiftStartedAt", "2026-10-10T14:00:00Z")
            .containsEntry("locationUpdatedAt", "2026-10-10T15:00:00Z");
    }

    @Test
    void neverShowsAnOldPositionAndSuggestsNobodyWithoutAvailable() {
        List<Map<String, Object>> board = CourierBoard.build(List.of(
            row(2, "Bruno", true, 1, -3.7400, STORE_LNG, NOW.minusSeconds(300)),
            row(4, "Caio", true, 0, -3.7400, STORE_LNG, NOW.minusSeconds(300)),
            row(1, "Davi", false, 0, -3.7400, STORE_LNG, NOW)), STORE_LAT, STORE_LNG, NOW);

        assertThat(board).extracting(item -> item.get("status")).containsExactly("delivering", "no_signal", "off_shift");
        assertThat(board).allSatisfy(item -> {
            assertThat(item).containsEntry("latitude", null).containsEntry("longitude", null)
                .containsEntry("distanceMeters", null).containsEntry("locationUpdatedAt", null).containsEntry("suggested", false);
        });
    }

    @Test
    void storeWithoutCoordinatesKeepsPositionsWithoutDistanceAndOrdersByName() {
        List<Map<String, Object>> board = CourierBoard.build(List.of(
            row(5, "Bia", true, 0, -3.7329, STORE_LNG, NOW),
            row(3, "Ana", true, 0, -3.7419, STORE_LNG, NOW)), null, null, NOW);

        assertThat(board).extracting(item -> item.get("name")).containsExactly("Ana", "Bia");
        assertThat(board.get(0)).containsEntry("distanceMeters", null).containsEntry("latitude", -3.7419).containsEntry("suggested", true);
    }
}
