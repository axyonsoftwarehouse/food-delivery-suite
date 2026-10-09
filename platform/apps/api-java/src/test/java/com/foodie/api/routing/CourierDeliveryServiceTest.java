package com.foodie.api.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class CourierDeliveryServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CourierDeliveryService service = new CourierDeliveryService(jdbc);

    @Test
    void activeBringsOnlyTheCouriersOngoingDeliveriesWithPhones() {
        Map<String, Object> row = new HashMap<>(Map.of("id", 40L, "status", "assigned"));
        row.put("contact_phone", "85999990000"); row.put("restaurant_phone", "8532221100");
        when(jdbc.queryForList(contains("o.courier_id = ? AND o.status IN ('assigned','picked_up')"), eq(9L))).thenReturn(List.of(row));
        when(jdbc.queryForList(contains("FROM order_items"), eq(40L))).thenReturn(List.of(Map.of("name", "Prato", "quantity", 1)));

        List<Map<String, Object>> active = service.active(9);

        assertThat(active).hasSize(1);
        assertThat(active.getFirst()).containsEntry("contact_phone", "85999990000").containsKey("items");
    }

    @Test
    void historyNeverCarriesPhones() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        when(jdbc.queryForList(sql.capture(), eq(9L))).thenReturn(List.of());

        service.history(9, "week");

        assertThat(sql.getValue()).doesNotContain("contact_phone").doesNotContain("phone")
            .contains("o.courier_id = ?").contains("INTERVAL 6 DAY");
    }

    @Test
    void historyAcceptsOnlyTodayOrWeek() {
        assertThatThrownBy(() -> service.history(9, "year")).hasMessage("Período inválido");
    }

    @Test
    void profileShowsTheStoreAndTheVehicle() {
        when(jdbc.queryForList(contains("LEFT JOIN courier_profiles"), eq(9L)))
            .thenReturn(List.of(Map.of("name", "Bia", "restaurant_name", "Cozinha Demo", "vehicle_type", "moto")));
        assertThat(service.profile(9)).containsEntry("restaurant_name", "Cozinha Demo").containsEntry("vehicle_type", "moto");
    }

    @Test
    void activeDeliveryCheck() {
        when(jdbc.queryForObject(contains("status IN ('assigned','picked_up')"), eq(Integer.class), eq(9L))).thenReturn(1);
        assertThat(service.hasActiveDelivery(9)).isTrue();
        when(jdbc.queryForObject(contains("status IN ('assigned','picked_up')"), eq(Integer.class), eq(9L))).thenReturn(0);
        assertThat(service.hasActiveDelivery(9)).isFalse();
    }

    @Test
    void activeTellsIfTheCodeIsNeededWithoutRevealingIt() {
        assertThat(CourierDeliveryService.ACTIVE_SQL).contains("o.delivery_code IS NOT NULL AS requires_delivery_code");
        // Tira o indicador; o que sobrar nao pode selecionar a coluna crua (o.delivery_code), so o contador de tentativas.
        assertThat(CourierDeliveryService.ACTIVE_SQL.replace("o.delivery_code IS NOT NULL AS requires_delivery_code", ""))
            .doesNotContainPattern("o\\.delivery_code(?!_attempts)");

        Map<String, Object> row = new HashMap<>(Map.of("id", 40L, "status", "picked_up"));
        row.put("requires_delivery_code", 1L); row.put("delivery_code_attempts", 2);
        when(jdbc.queryForList(contains("o.courier_id = ?"), eq(9L))).thenReturn(List.of(row));
        when(jdbc.queryForList(contains("FROM order_items"), eq(40L))).thenReturn(List.of());

        Map<String, Object> delivery = service.active(9).getFirst();

        assertThat(delivery).containsEntry("requires_delivery_code", true).containsEntry("code_attempts_left", 3)
            .doesNotContainKey("delivery_code_attempts");
    }

    @Test
    void activeAcceptsABooleanCodeFlag() {
        Map<String, Object> row = new HashMap<>(Map.of("id", 41L, "status", "picked_up"));
        row.put("requires_delivery_code", Boolean.TRUE); row.put("delivery_code_attempts", 0);
        when(jdbc.queryForList(contains("o.courier_id = ?"), eq(9L))).thenReturn(List.of(row));
        when(jdbc.queryForList(contains("FROM order_items"), eq(41L))).thenReturn(List.of());

        assertThat(service.active(9).getFirst()).containsEntry("requires_delivery_code", true);
    }
}
