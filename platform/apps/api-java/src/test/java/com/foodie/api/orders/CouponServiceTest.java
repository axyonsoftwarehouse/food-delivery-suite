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

    @Test
    void rejectsCustomerWhoReachedThePerCustomerLimit() {
        couponWithPerCustomerLimit(1);
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("FROM orders WHERE customer_id = ?"), eq(Integer.class), eq(9L), eq("CANTINA15")))
            .thenReturn(1);
        assertThatThrownBy(() -> coupons.validate("CANTINA15", 3, 4000, 9L))
            .isInstanceOf(ApiException.class)
            .hasMessage("Você já usou este cupom o número máximo de vezes");
    }

    @Test
    void acceptsCustomerBelowThePerCustomerLimit() {
        couponWithPerCustomerLimit(2);
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("FROM orders WHERE customer_id = ?"), eq(Integer.class), eq(9L), eq("CANTINA15")))
            .thenReturn(1);
        assertThat(coupons.validate("CANTINA15", 3, 4000, 9L).discountCents()).isEqualTo(600L);
    }

    @Test
    void consumeFailsWhenTheLastUseWasTakenConcurrently() {
        when(jdbc.update(org.mockito.ArgumentMatchers.contains("used_count < max_uses"), eq(1L))).thenReturn(0);
        assertThatThrownBy(() -> coupons.consume(1L))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).status()).isEqualTo(409));
    }

    @Test
    void consumeIncrementsWhileThereIsBalance() {
        when(jdbc.update(org.mockito.ArgumentMatchers.contains("used_count < max_uses"), eq(1L))).thenReturn(1);
        coupons.consume(1L);
    }

    private void couponWithPerCustomerLimit(int limit) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 1L);
        row.put("restaurant_id", 3L);
        row.put("code", "CANTINA15");
        row.put("discount_type", "percent");
        row.put("discount_value", 15);
        row.put("min_order_cents", 0);
        row.put("max_uses", null);
        row.put("max_uses_per_customer", limit);
        row.put("used_count", 0);
        row.put("active", true);
        row.put("expires_at", null);
        when(jdbc.queryForList(anyString(), eq("CANTINA15"))).thenReturn(List.of(row));
    }

    @Test
    void releaseGivesTheUseBackToTheCoupon() {
        // Revisão de 08/10/2026: pedido morto (recusado, cancelado, expirado) já não contava no limite por
        // cliente, mas continuava gastando o limite total do cupom.
        org.springframework.jdbc.core.JdbcTemplate db = org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
        new CouponService(db).release("BEMVINDO");
        org.mockito.Mockito.verify(db).update("UPDATE coupons SET used_count = used_count - 1 WHERE code = ? AND used_count > 0", "BEMVINDO");
    }

    @Test
    void personalCouponWorksOnlyForItsOwner() {
        // Spec de 08/10/2026 (indicação): cupom com dono só vale para o dono; para os outros, é inexistente.
        coupon(3L);
        when(jdbc.queryForList(anyString(), eq("CANTINA15"))).thenAnswer(call -> {
            Map<String, Object> row = new HashMap<>();
            row.put("id", 1L); row.put("restaurant_id", 3L); row.put("customer_id", 7L); row.put("code", "CANTINA15");
            row.put("discount_type", "fixed"); row.put("discount_value", 1000); row.put("min_order_cents", 0);
            row.put("max_uses", 1); row.put("max_uses_per_customer", null); row.put("used_count", 0); row.put("active", true); row.put("expires_at", null);
            return List.of(row);
        });

        assertThat(coupons.validate("CANTINA15", 3, 4000, 7L).discountCents()).isEqualTo(1000L);
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(com.foodie.api.ApiException.class,
            () -> coupons.validate("CANTINA15", 3, 4000, 8L)).getMessage()).isEqualTo("Cupom inválido");
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(com.foodie.api.ApiException.class,
            () -> coupons.validate("CANTINA15", 3, 4000)).status()).isEqualTo(404);
    }

    @Test
    void issuesAPersonalSingleUseCoupon() {
        when(jdbc.queryForObject(eq("SELECT id FROM coupons WHERE code = ?"), eq(Long.class), anyString())).thenReturn(55L);

        CouponService.Issued issued = coupons.issuePersonal(3, 7, "referral_welcome", "fixed", 1000, 0, 30);

        assertThat(issued.couponId()).isEqualTo(55L);
        assertThat(issued.code()).startsWith("IND").hasSize(10);
        Mockito.verify(jdbc).update(org.mockito.ArgumentMatchers.contains("max_uses, max_uses_per_customer, expires_at"),
            eq(3L), eq(7L), eq("referral_welcome"), eq(issued.code()), eq("fixed"), eq(1000L), eq(0L), eq(30));
    }
}
