package com.foodie.api.subscriptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminAuditService;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.EmailVerificationGuard;
import com.foodie.api.auth.User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** As cobranças da assinatura: a loja só vê as próprias; a listagem do admin é de observação. */
class SubscriptionControllerTest {
    private final AuthService auth = mock(AuthService.class);
    private final EmailVerificationGuard verification = mock(EmailVerificationGuard.class);
    private final AdminPermissionService permissions = mock(AdminPermissionService.class);
    private final AdminAuditService audit = mock(AdminAuditService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final SubscriptionChargeRepository charges = mock(SubscriptionChargeRepository.class);

    private SubscriptionController controller() {
        return new SubscriptionController(auth, verification, permissions, audit, jdbc, charges);
    }

    @Test
    void theStoreSeesOnlyItsOwnCharges() {
        when(auth.requireUser("t", "restaurant")).thenReturn(new User(5, "Dono", "dono@loja.com", "restaurant", 3L));
        when(charges.listByRestaurant(3L, 100)).thenReturn(List.of(Map.of("id", 1L, "status", "paid")));

        assertEquals(1, controller().myCharges("t").size());
    }

    @Test
    void theStoreWithoutRestaurantIsForbidden() {
        when(auth.requireUser("t", "restaurant")).thenReturn(new User(5, "Dono", "dono@loja.com", "restaurant", null));

        ApiException error = assertThrows(ApiException.class, () -> controller().myCharges("t"));

        assertEquals(403, error.status());
    }

    @Test
    void theAdminListsTheChargesOfAStore() {
        when(auth.requireUser("t", "admin")).thenReturn(new User(1, "Admin", "admin@foodie.com", "admin", null));
        when(charges.listByRestaurant(9L, 100)).thenReturn(List.of(Map.of("id", 1L, "status", "paid")));

        assertEquals(1, controller().adminCharges("t", 9L).size());
    }

    @Test
    void theAdminCanSuspendASubscription() {
        when(auth.requireUser("t", "admin")).thenReturn(new User(1, "Admin", "admin@foodie.com", "admin", null));
        when(jdbc.update(any(String.class), any(Object[].class))).thenReturn(1);

        assertEquals(Map.of("ok", true), controller().changeStatus("t", 7L, new SubscriptionController.StatusRequest("suspended")));
    }
}
