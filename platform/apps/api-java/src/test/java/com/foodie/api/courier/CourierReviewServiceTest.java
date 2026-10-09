package com.foodie.api.courier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import com.foodie.api.auth.User;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class CourierReviewServiceTest {
    private static final User CUSTOMER = new User(8, "Ana", "ana@demo.local", "customer", null);
    private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CourierReviewService service = new CourierReviewService(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));

    private void order(String status, String type, Long courierId, Instant deliveredAt) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 40L); row.put("courier_id", courierId); row.put("restaurant_id", 3L); row.put("status", status);
        row.put("order_type", type); row.put("courier_name", "Bia");
        row.put("delivered_at", deliveredAt == null ? null : Timestamp.from(deliveredAt));
        when(jdbc.queryForList(startsWith("SELECT o.id, o.courier_id"), any(Object[].class))).thenReturn(List.of(row));
    }

    @Test
    void customerReviewsADeliveredOrderOfHis() {
        order("delivered", "delivery", 9L, NOW.minusSeconds(3600));
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.ResultSetExtractor.class), any(Object[].class))).thenReturn(null);
        when(jdbc.queryForMap(startsWith("SELECT id, order_id, rating"), any(Object[].class))).thenReturn(Map.of("rating", 5));

        assertThat(service.create(CUSTOMER, 40, 5, " ótimo ")).containsEntry("rating", 5);
        verify(jdbc).update("INSERT INTO courier_reviews (order_id, customer_id, courier_id, restaurant_id, rating, comment) VALUES (?, ?, ?, ?, ?, ?)",
            40L, 8L, 9L, 3L, 5, "ótimo");
    }

    @Test
    void refusesOrdersThatAreNotDeliveredDeliveriesWithACourier() {
        order("picked_up", "delivery", 9L, null);
        assertThatThrownBy(() -> service.create(CUSTOMER, 40, 5, null)).isInstanceOf(ApiException.class).hasMessage("Só é possível avaliar uma entrega já concluída");
        order("delivered", "take_away", null, NOW.minusSeconds(60));
        assertThatThrownBy(() -> service.create(CUSTOMER, 40, 5, null)).isInstanceOf(ApiException.class).hasMessage("Só é possível avaliar uma entrega já concluída");
        order("delivered", "delivery", null, NOW.minusSeconds(60));
        assertThatThrownBy(() -> service.create(CUSTOMER, 40, 5, null)).isInstanceOf(ApiException.class).hasMessage("Só é possível avaliar uma entrega já concluída");
        verify(jdbc, never()).update(startsWith("INSERT INTO courier_reviews"), any(Object[].class));
    }

    @Test
    void refusesAfterSevenDays() {
        order("delivered", "delivery", 9L, NOW.minusSeconds(7 * 86400 + 1));
        assertThatThrownBy(() -> service.create(CUSTOMER, 40, 5, null)).isInstanceOf(ApiException.class).hasMessage("O prazo para avaliar a entrega terminou");
    }

    @Test
    void refusesASecondReview() {
        order("delivered", "delivery", 9L, NOW.minusSeconds(60));
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.ResultSetExtractor.class), any(Object[].class))).thenReturn(1);
        assertThatThrownBy(() -> service.create(CUSTOMER, 40, 5, null)).isInstanceOf(ApiException.class).hasMessage("Esta entrega já foi avaliada");
    }

    @Test
    void invalidRatingAndAnotherCustomersOrderAreRefused() {
        assertThatThrownBy(() -> service.create(CUSTOMER, 40, 6, null)).isInstanceOf(ApiException.class).hasMessage("A nota deve ser de 1 a 5");
        when(jdbc.queryForList(startsWith("SELECT o.id, o.courier_id"), any(Object[].class))).thenReturn(List.of());
        assertThatThrownBy(() -> service.create(CUSTOMER, 40, 5, null)).isInstanceOf(ApiException.class).hasMessage("Pedido não encontrado");
    }

    @Test
    void forOrderSaysIfTheCustomerCanStillReview() {
        order("delivered", "delivery", 9L, NOW.minusSeconds(60));
        when(jdbc.queryForList(startsWith("SELECT rating, comment FROM courier_reviews"), any(Object[].class))).thenReturn(List.of());
        assertThat(service.forOrder(CUSTOMER, 40)).containsEntry("canReview", true).containsEntry("courierName", "Bia");

        when(jdbc.queryForList(startsWith("SELECT rating, comment FROM courier_reviews"), any(Object[].class)))
            .thenReturn(List.of(Map.of("rating", 4, "comment", "")));
        assertThat(service.forOrder(CUSTOMER, 40)).containsEntry("canReview", false);

        order("delivered", "delivery", 9L, NOW.minusSeconds(8 * 86400));
        when(jdbc.queryForList(startsWith("SELECT rating, comment FROM courier_reviews"), any(Object[].class))).thenReturn(List.of());
        assertThat(service.forOrder(CUSTOMER, 40)).containsEntry("canReview", false);
    }
}
