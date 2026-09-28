package com.foodie.api.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import com.foodie.api.auth.User;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PayoutServiceTest {
    private final PayoutRepository payouts = mock(PayoutRepository.class);
    private final LedgerRepository ledger = mock(LedgerRepository.class);
    private final LedgerService ledgerService = mock(LedgerService.class);
    private final PayoutService service = new PayoutService(payouts, ledger, ledgerService);

    private final User restaurant = new User(3, "Dono", "dono@demo.local", "restaurant", 1L);
    private final User admin = new User(1, "Admin", "admin@demo.local", "admin", null);

    @Test
    void rejectsNewRestaurantRequest() {
        assertThatThrownBy(() -> service.createRequest(restaurant, 2000, null, null))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).status()).isEqualTo(403));
    }

    @Test
    void adminCannotHaveWallet() {
        assertThatThrownBy(() -> service.wallet(admin))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).status()).isEqualTo(403));
    }

    @Test
    void payingPayoutDebitsLedger() {
        Map<String, Object> request = new HashMap<>();
        request.put("id", 7L);
        request.put("party", "restaurant");
        request.put("party_id", 1L);
        request.put("amount_cents", 3000L);
        request.put("status", "requested");
        request.put("method_id", null);
        when(payouts.findRequest(7)).thenReturn(Optional.of(request));
        when(payouts.updateStatus(7, "paid", 1L, "")).thenReturn(1);

        Map<String, Object> result = service.decide(admin, 7, "paid", null);

        assertThat(result.get("status")).isEqualTo("paid");
        verify(ledger).insert("restaurant", 1L, null, "payout", -3000, "Repasse #7");
    }
}
