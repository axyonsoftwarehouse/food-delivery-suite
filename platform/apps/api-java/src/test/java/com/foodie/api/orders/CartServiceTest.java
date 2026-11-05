package com.foodie.api.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
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
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

class CartServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final RestaurantHoursService hours = mock(RestaurantHoursService.class);
    private final AddonService addons = mock(AddonService.class);
    private final CartService service = new CartService(jdbc, mock(OrderService.class), addons,
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

    private static final User CUSTOMER = new User(7, "Cliente", "cliente@demo.local", "customer", null);
    private static final String INSERT = "INSERT INTO cart_items";

    private void cartRows(boolean filled) {
        when(hours.localTimeSql(anyString(), any())).thenReturn("?");
        List<CartService.CartRow> rows = filled
            ? List.of(new CartService.CartRow(1, 0, "", "", 1, 5, "Prato", 1000, null, 0, true, null)) : List.of();
        doReturn(rows).when(jdbc).query(startsWith("SELECT ci.product_id"), any(RowMapper.class), any(Object[].class));
    }

    private void productsFrom(long... restaurantIds) {
        Long first = restaurantIds[0];
        Long[] rest = new Long[restaurantIds.length - 1];
        for (int i = 1; i < restaurantIds.length; i++) rest[i - 1] = restaurantIds[i];
        doReturn(first, (Object[]) rest).when(jdbc).query(startsWith("SELECT p.restaurant_id"), any(ResultSetExtractor.class), any(Object[].class));
    }

    private static CartController.ImportItem item(long productId, String note) {
        return new CartController.ImportItem(productId, null, null, 1, note);
    }

    @Test
    void importWithoutReplaceKeepsNonEmptyCart() {
        cartRows(true);
        var result = service.importItems(CUSTOMER, List.of(item(1, null)), false);
        assertThat(result.imported()).isZero();
        assertThat(result.skipped()).isZero();
        assertThat(result.replaced()).isFalse();
        verify(jdbc, never()).update(startsWith(INSERT), any(Object[].class));
    }

    @Test
    void replaceClearsAndImportsWhenAnItemIsValid() {
        cartRows(true);
        productsFrom(5L);
        var result = service.importItems(CUSTOMER, List.of(item(1, "  sem cebola ")), true);
        verify(jdbc, org.mockito.Mockito.times(1)).update("DELETE FROM cart_items WHERE user_id = ?", 7L);
        verify(jdbc, org.mockito.Mockito.times(1)).update(startsWith(INSERT), any(Object[].class));
        verify(jdbc).update(startsWith(INSERT), org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(1L),
            org.mockito.ArgumentMatchers.eq(0L), org.mockito.ArgumentMatchers.eq(""), org.mockito.ArgumentMatchers.eq("sem cebola"), org.mockito.ArgumentMatchers.eq(1));
        assertThat(result.replaced()).isTrue();
        assertThat(result.imported()).isEqualTo(1);
    }

    @Test
    void replaceKeepsCartWhenNothingIsValid() {
        cartRows(true);
        var items = List.of(item(1, null), item(2, null));
        var result = service.importItems(CUSTOMER, items, true);
        verify(jdbc, never()).update(startsWith("DELETE FROM cart_items"), any(Object[].class));
        verify(jdbc, never()).update(startsWith(INSERT), any(Object[].class));
        assertThat(result.skipped()).isEqualTo(items.size());
        assertThat(result.replaced()).isFalse();
    }

    @Test
    void importKeepsLinesThatDifferOnlyByNote() {
        cartRows(false);
        productsFrom(5L, 5L);
        var result = service.importItems(CUSTOMER, List.of(item(1, "sem cebola"), item(1, "")), false);
        verify(jdbc, org.mockito.Mockito.times(2)).update(startsWith(INSERT), any(Object[].class));
        assertThat(result.skipped()).isZero();
        assertThat(result.imported()).isEqualTo(2);
        assertThat(result.replaced()).isFalse();
    }

    @Test
    void importCountsItemsFromAnotherRestaurantAsSkipped() {
        cartRows(false);
        productsFrom(5L, 6L);
        var result = service.importItems(CUSTOMER, List.of(item(1, null), item(2, null)), false);
        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
    }
}
