package com.foodie.api.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import com.foodie.api.auth.User;
import com.foodie.api.catalog.AddonService;
import com.foodie.api.catalog.PostalCoverageService;
import com.foodie.api.finance.LedgerService;
import com.foodie.api.hours.RestaurantHoursService;
import com.foodie.api.notifications.NotificationService;
import com.foodie.api.rewards.RewardsService;
import com.foodie.api.routing.DeliveryService;
import com.foodie.api.support.SupportActionService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class OrderServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final OrderService orders = new OrderService(jdbc, mock(NamedParameterJdbcTemplate.class), mock(PostalCoverageService.class),
        mock(RestaurantHoursService.class), mock(PaymentService.class), mock(DeliveryService.class), mock(NotificationService.class),
        mock(AddonService.class), mock(CouponService.class), mock(CampaignService.class), mock(LedgerService.class),
        mock(RewardsService.class), mock(SupportActionService.class));

    private static final User ADMIN = new User(1, "Admin", "admin@demo.local", "admin", null);
    private static final User RESTAURANT = new User(2, "Loja", "loja@demo.local", "restaurant", 3L);

    @Test
    void lookupFindsAnyOrderForAdmin() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of("id", 26L, "customer_name", "Ana")));

        assertThat(orders.lookup(ADMIN, 26)).containsEntry("id", 26L).containsEntry("customer_name", "Ana");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForList(sql.capture(), args.capture());
        assertThat(sql.getValue()).contains("WHERE 1 = 1 AND o.id = ?");
        assertThat(args.getValue()).containsExactly(26L);
    }

    @Test
    void lookupKeepsRestaurantScope() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        assertThatThrownBy(() -> orders.lookup(RESTAURANT, 26))
            .isInstanceOf(ApiException.class)
            .hasMessage("Pedido não encontrado");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForList(sql.capture(), args.capture());
        assertThat(sql.getValue()).contains("WHERE o.restaurant_id = ? AND o.id = ?");
        assertThat(args.getValue()).containsExactly(3L, 26L);
    }
}
