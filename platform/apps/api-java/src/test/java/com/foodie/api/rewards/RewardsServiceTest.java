package com.foodie.api.rewards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import com.foodie.api.finance.LedgerRepository;
import com.foodie.api.settings.SettingsCatalog;
import com.foodie.api.settings.SettingsService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class RewardsServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final LedgerRepository ledger = mock(LedgerRepository.class);
    private final SettingsService settings = mock(SettingsService.class);
    private final RewardsService service = new RewardsService(jdbc, ledger, settings);

    @Test
    void walletAndManualCreditDebit() {
        when(ledger.sum("customer", 7L)).thenReturn(2000L);
        assertThat(service.wallet(7L).get("balanceCents")).isEqualTo(2000L);

        service.credit(7L, 500, "bônus manual");
        verify(ledger).insert("customer", 7L, null, "adjustment", 500, "bônus manual");

        when(ledger.sum("customer", 7L)).thenReturn(100L);
        assertThatThrownBy(() -> service.debit(7L, 500, null))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).status()).isEqualTo(409));
    }

    @Test
    void awardsLoyaltyWhenOrderCompleted() {
        when(settings.bool(SettingsCatalog.LOYALTY_ENABLED)).thenReturn(true);
        when(settings.intValue(SettingsCatalog.LOYALTY_POINTS_PER_REAL)).thenReturn(1);
        when(settings.bool(SettingsCatalog.CASHBACK_ENABLED)).thenReturn(false);
        when(settings.bool(SettingsCatalog.REFERRAL_ENABLED)).thenReturn(false);

        Map<String, Object> order = new HashMap<>();
        order.put("customer_id", 7L);
        order.put("restaurant_id", 1L);
        order.put("total_cents", 10000L);
        order.put("status", "delivered");
        order.put("payment_status", "paid");
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(order));
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        service.onOrderCompleted(10);

        verify(jdbc).update(anyString(), eq(7L), eq(10L), eq(100L), anyString());
    }

    private Map<String, Object> completedOrder() {
        Map<String, Object> order = new HashMap<>();
        order.put("customer_id", 7L);
        order.put("restaurant_id", 1L);
        order.put("subtotal_cents", 10000L);
        order.put("discount_cents", 2000L);
        order.put("total_cents", 11200L); // + frete, gorjeta e taxa de serviço
        order.put("status", "delivered");
        order.put("payment_status", "paid");
        return order;
    }

    @Test
    @SuppressWarnings("unchecked")
    void cashbackUsesTheSubtotalMinusDiscounts() {
        // Decisão de 08/10/2026: cashback sobre o subtotal menos os descontos — sem gorjeta, frete e taxa de
        // serviço. Itens de R$ 100 com cupom de R$ 20: 10% dá R$ 8, não R$ 11,20.
        when(settings.bool(SettingsCatalog.CASHBACK_ENABLED)).thenReturn(true);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(completedOrder()));
        when(jdbc.query(org.mockito.ArgumentMatchers.contains("FROM cashback_rules"), any(RowMapper.class), eq(1L), eq(8000L)))
            .thenReturn(List.of(new java.math.BigDecimal("10.00")));

        service.onOrderCompleted(10);

        verify(ledger).insert("customer", 7L, 10L, "cashback", 800L, "Cashback do pedido #10");
    }

    @Test
    @SuppressWarnings("unchecked")
    void storeWithoutRuleGivesNoCashback() {
        when(settings.bool(SettingsCatalog.CASHBACK_ENABLED)).thenReturn(true);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(completedOrder()));
        when(jdbc.query(org.mockito.ArgumentMatchers.contains("FROM cashback_rules"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        service.onOrderCompleted(10);

        verify(ledger, org.mockito.Mockito.never()).insert(anyString(), any(), any(), eq("cashback"), org.mockito.ArgumentMatchers.anyLong(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void refundReversesTheEarnedPointsOnce() {
        // Revisão de 08/10/2026: o estorno revertia o razão (cashback) mas deixava os pontos com o cliente.
        when(jdbc.query(org.mockito.ArgumentMatchers.contains("FROM loyalty_transactions"), any(RowMapper.class), eq(10L)))
            .thenReturn(List.of(Map.of("user_id", 7L, "points", 100L)));

        service.reverseOrder(10);

        verify(jdbc).update("INSERT INTO loyalty_transactions (user_id, order_id, points, kind, description) VALUES (?, ?, ?, 'reversal', ?)",
            7L, 10L, -100L, "Estorno do pedido #10");
    }

    @Test
    @SuppressWarnings("unchecked")
    void nothingToReverseWhenThePointsWereAlreadyReversedOrNeverEarned() {
        when(jdbc.query(org.mockito.ArgumentMatchers.contains("FROM loyalty_transactions"), any(RowMapper.class), eq(10L))).thenReturn(List.of());

        service.reverseOrder(10);

        verify(jdbc, org.mockito.Mockito.never()).update(anyString(), any(Object[].class));
    }
}
