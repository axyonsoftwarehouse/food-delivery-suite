package com.foodie.api.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

class CouponServiceTest {
    private final JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
    private final CouponService coupons = new CouponService(jdbc);

    private void coupon(Object restaurantId) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 1L);
        row.put("restaurant_id", restaurantId);
        row.put("code", "CANTINA15");
        row.put("discount_type", "percent");
        row.put("discount_value", 15);
        row.put("min_order_cents", 0);
        row.put("max_uses", null);
        row.put("used_count", 0);
        row.put("active", true);
        row.put("expires_at", null);
        when(jdbc.queryForList(anyString(), eq("CANTINA15"))).thenReturn(List.of(row));
    }

    @Test
    void appliesCouponOfTheOrderRestaurant() {
        coupon(3L);
        assertThat(coupons.validate("cantina15", 3, 4000).discountCents()).isEqualTo(600L);
    }

    @Test
    void rejectsCouponOfAnotherRestaurant() {
        coupon(3L);
        assertThatThrownBy(() -> coupons.validate("CANTINA15", 7, 4000))
            .isInstanceOf(ApiException.class)
            .hasMessage("Este cupom não vale para o restaurante do pedido");
    }

    @Test
    void rejectsCouponWithoutRestaurant() {
        coupon(null);
        assertThatThrownBy(() -> coupons.validate("CANTINA15", 3, 4000))
            .isInstanceOf(ApiException.class)
            .hasMessage("Este cupom não vale para o restaurante do pedido");
    }
}
