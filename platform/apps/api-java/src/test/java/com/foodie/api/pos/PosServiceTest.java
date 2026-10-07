package com.foodie.api.pos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import com.foodie.api.auth.PasswordVerifier;
import com.foodie.api.auth.User;
import com.foodie.api.orders.OrderService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class PosServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final OrderService orders = mock(OrderService.class);
    private final PosService pos = new PosService(jdbc, orders, new PasswordVerifier());

    @Test
    void searchIsLimitedToCustomersOfTheStore() {
        pos.customers(3L, "ana");

        verify(jdbc).queryForList(contains("o.restaurant_id = ?"), eq("%ana%"), eq("%ana%"), eq(3L));
    }

    @Test
    void searchEscapesLikeWildcards() {
        assertThat(PosService.escapeLike("50%_off\\")).isEqualTo("50\\%\\_off\\\\");
        pos.customers(3L, "%%");
        verify(jdbc).queryForList(anyString(), eq("%\\%\\%%"), eq("%\\%\\%%"), eq(3L));
    }

    @Test
    void shortSearchReturnsNothing() {
        assertThat(pos.customers(3L, "a")).isEmpty();
        verify(jdbc, never()).queryForList(anyString(), any(Object[].class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void cannotSellInTheNameOfACustomerFromAnotherStore() {
        when(jdbc.query(contains("o.restaurant_id = ?"), any(RowMapper.class), eq(42L), eq(3L))).thenReturn(List.of());
        User operator = new User(9, "Caixa", "caixa@demo.local", "restaurant", 3L);
        PosController.PosRequest request = new PosController.PosRequest(
            List.of(new PosController.PosItem(1L, null, 1, null)), "cash", null, "take_away", null, null, 42L);

        assertThatThrownBy(() -> pos.createOrder(operator, request))
            .isInstanceOf(ApiException.class)
            .hasMessage("Cliente não encontrado");
        verify(orders, never()).create(any(), any());
    }
}
