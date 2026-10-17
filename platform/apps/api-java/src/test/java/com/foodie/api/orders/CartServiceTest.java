package com.foodie.api.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.api.auth.User;
import com.foodie.api.catalog.AddonService;
import com.foodie.api.hours.RestaurantHoursService;
import com.foodie.api.settings.SettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class CartServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final RestaurantHoursService hours = mock(RestaurantHoursService.class);
    private final CartService service = new CartService(jdbc, mock(OrderService.class), mock(AddonService.class),
        new ObjectMapper(), mock(SettingsService.class), mock(CampaignService.class), hours);

    @Test
    void readingTheCartDoesNotLockOrDeleteAnything() {
        when(hours.localTimeSql(anyString(), any())).thenReturn("?");
        doReturn(List.of()).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));

        var snapshot = service.get(new User(7, "Cliente", "cliente@demo.local", "customer", null));

        assertThat(snapshot.items()).isEmpty();
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verify(jdbc, never()).query(org.mockito.ArgumentMatchers.startsWith("SELECT id FROM users"), any(RowMapper.class), any(Object[].class));
    }
}
