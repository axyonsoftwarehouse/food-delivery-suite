package com.foodie.api.rewards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import com.foodie.api.notifications.NotificationService;
import com.foodie.api.orders.CouponService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

/** Indicação como cupom da loja: regras 1 a 8 da spec de 08/10/2026. */
class ReferralServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CouponService coupons = mock(CouponService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final ReferralService service = new ReferralService(jdbc, coupons, notifications);

    private static final long REFERRER = 7, REFERRED = 8, STORE = 3;

    @BeforeEach
    void setUp() {
        when(jdbc.queryForList(startsWith("SELECT id FROM users WHERE referral_code"), eq(Long.class), eq("ANA12345"))).thenReturn(List.of(REFERRER));
        when(coupons.issuePersonal(anyLong(), anyLong(), anyString(), anyString(), anyLong(), anyLong(), anyInt()))
            .thenReturn(new CouponService.Issued(90, "INDABC1234"));
    }

    private void activeProgram() {
        Map<String, Object> program = new HashMap<>();
        program.put("referrer_type", "fixed"); program.put("referrer_value", 1500);
        program.put("referred_type", "percent"); program.put("referred_value", 10);
        program.put("min_order_cents", 3000); program.put("valid_days", 30);
        when(jdbc.queryForList(contains("FROM restaurant_referral_programs WHERE restaurant_id = ? AND active = TRUE"), eq(STORE))).thenReturn(List.of(program));
    }

    @SuppressWarnings("unchecked")
    private void exists(String sqlFragment, boolean found) {
        when(jdbc.query(contains(sqlFragment), any(ResultSetExtractor.class), any(Object[].class))).thenReturn(found ? 1 : null);
    }

    // ----- Regra 1: programa -----

    @Test
    void programValidatesTheStoreValues() {
        assertThatThrownBy(() -> service.saveProgram(STORE, new ReferralService.Program(true, "percent", 150, "fixed", 1000, 0, 30)))
            .hasMessageContaining("até 100%");
        assertThatThrownBy(() -> service.saveProgram(STORE, new ReferralService.Program(true, "fixed", 100_001, "fixed", 1000, 0, 30)))
            .hasMessageContaining("até R$ 1.000");
        assertThatThrownBy(() -> service.saveProgram(STORE, new ReferralService.Program(true, "fixed", 1000, "fixed", 1000, 0, 0)))
            .hasMessageContaining("1 a 365 dias");
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void storeWithoutProgramShowsItTurnedOff() {
        when(jdbc.queryForList(contains("FROM restaurant_referral_programs"), eq(STORE))).thenReturn(List.of());
        assertThat(service.program(STORE)).containsEntry("active", false).containsEntry("validDays", 30);
    }

    // ----- Regras 2 a 4: registrar a indicação -----

    @Test
    void registeringGivesTheWelcomeCouponWithTheProgramValues() {
        activeProgram();

        Map<String, Object> result = service.register(REFERRED, "ana12345", STORE);

        assertThat(result).containsEntry("couponCode", "INDABC1234").containsEntry("discountType", "percent");
        verify(coupons).issuePersonal(STORE, REFERRED, "referral_welcome", "percent", 10L, 3000L, 30);
        // Regra 6: a indicação guarda os valores do programa do momento.
        verify(jdbc).update(startsWith("INSERT INTO referrals"), eq(REFERRER), eq(REFERRED), eq(STORE), eq("ANA12345"),
            eq("fixed"), eq(1500L), eq("percent"), eq(10L), eq(3000L), eq(30), eq(30), eq(90L));
    }

    @Test
    void nobodyRefersThemselves() {
        activeProgram();
        assertThatThrownBy(() -> service.register(REFERRER, "ANA12345", STORE)).isInstanceOf(ApiException.class).hasMessageContaining("próprio código");
    }

    @Test
    void unknownCodeIsRefused() {
        activeProgram();
        assertThatThrownBy(() -> service.register(REFERRED, "XXXX", STORE)).hasMessage("Código de indicação inválido");
    }

    @Test
    void storeWithoutActiveProgramRefusesNewReferrals() {
        when(jdbc.queryForList(contains("AND active = TRUE"), eq(STORE))).thenReturn(List.of());
        assertThatThrownBy(() -> service.register(REFERRED, "ANA12345", STORE)).hasMessageContaining("não tem programa de indicação ativo");
        verify(coupons, never()).issuePersonal(anyLong(), anyLong(), anyString(), anyString(), anyLong(), anyLong(), anyInt());
    }

    @Test
    void onePersonIsReferredOncePerStore() {
        activeProgram();
        exists("FROM referrals WHERE referred_id = ? AND restaurant_id = ?", true);
        assertThatThrownBy(() -> service.register(REFERRED, "ANA12345", STORE)).hasMessage("Você já foi indicado para esta loja");
    }

    @Test
    void customerOfTheStoreCannotBeReferredToIt() {
        // Conta antiga no app vale; já ter pedido (que não morreu) nesta loja não vale.
        activeProgram();
        exists("FROM orders WHERE customer_id = ? AND restaurant_id = ? AND status NOT IN ('rejected','cancelled','expired')", true);
        assertThatThrownBy(() -> service.register(REFERRED, "ANA12345", STORE)).hasMessageContaining("ainda não pediu nesta loja");
        verify(coupons, never()).issuePersonal(anyLong(), anyLong(), anyString(), anyString(), anyLong(), anyLong(), anyInt());
    }

    // ----- Regras 5 e 7: prêmio de quem indicou -----

    private void pendingReferral(boolean overdue) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 50L); row.put("referrer_id", REFERRER); row.put("referrer_type", "fixed"); row.put("referrer_value", 1500);
        row.put("min_order_cents", 3000); row.put("valid_days", 30); row.put("overdue", overdue ? 1L : 0L); row.put("restaurant_name", "Cozinha Demo");
        when(jdbc.queryForList(contains("FROM referrals r JOIN restaurants s"), eq(REFERRED), eq(STORE))).thenReturn(List.of(row));
    }

    @Test
    void firstCompletedOrderRewardsTheReferrerWithTheFrozenValues() {
        pendingReferral(false);

        service.onOrderCompleted(200, REFERRED, STORE);

        verify(coupons).issuePersonal(STORE, REFERRER, "referral_reward", "fixed", 1500L, 3000L, 30);
        verify(jdbc).update(startsWith("UPDATE referrals SET status = 'rewarded'"), eq(90L), eq(200L), eq(50L));
        verify(notifications).notifyUser(eq(REFERRER), eq("referral_reward"), contains("Cozinha Demo"), contains("INDABC1234"), eq(null));
    }

    @Test
    void referralPastItsDeadlineExpiresWithoutReward() {
        pendingReferral(true);

        service.onOrderCompleted(200, REFERRED, STORE);

        verify(jdbc).update("UPDATE referrals SET status = 'expired' WHERE id = ?", 50L);
        verify(coupons, never()).issuePersonal(anyLong(), anyLong(), anyString(), anyString(), anyLong(), anyLong(), anyInt());
    }

    @Test
    void orderWithoutPendingReferralDoesNothing() {
        when(jdbc.queryForList(contains("FROM referrals r JOIN restaurants s"), eq(REFERRED), eq(STORE))).thenReturn(List.of());
        service.onOrderCompleted(200, REFERRED, STORE);
        verify(coupons, never()).issuePersonal(anyLong(), anyLong(), anyString(), anyString(), anyLong(), anyLong(), anyInt());
    }

    // ----- Regra 8: estorno -----

    private void rewardedBy(long orderId, long usedCount) {
        when(jdbc.queryForList(contains("WHERE r.reward_order_id = ?"), eq(orderId)))
            .thenReturn(List.of(Map.of("id", 50L, "reward_coupon_id", 91L, "used_count", usedCount)));
    }

    @Test
    void refundDeactivatesAnUnusedRewardCoupon() {
        rewardedBy(200, 0);

        service.onOrderRefunded(200);

        verify(jdbc).update("UPDATE coupons SET active = FALSE WHERE id = ?", 91L);
        verify(jdbc).update("UPDATE referrals SET status = 'expired' WHERE id = ?", 50L);
    }

    @Test
    void refundKeepsARewardCouponAlreadyUsed() {
        rewardedBy(200, 1);

        service.onOrderRefunded(200);

        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
