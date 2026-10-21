package com.foodie.api.subscriptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import com.foodie.api.admin.AdminAuditService;
import com.foodie.api.admin.AdminPermissionService;
import com.foodie.api.auth.AuthService;
import com.foodie.api.auth.EmailVerificationGuard;
import com.foodie.api.auth.User;
import com.foodie.api.permissions.PermissionService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final PermissionService restaurantPermissions = mock(PermissionService.class);
    private final SubscriptionBillingService billing = mock(SubscriptionBillingService.class);
    private final SubscriptionBillingAccount account = mock(SubscriptionBillingAccount.class);

    private SubscriptionController controller() {
        return new SubscriptionController(auth, verification, permissions, audit, jdbc, charges, restaurantPermissions, billing, account, 15);
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
    void aStaffMemberWithoutPaymentsPermissionCannotSeeThePlan() {
        when(auth.requireUser("t", "restaurant")).thenReturn(new User(6, "Atendente", "atendente@loja.com", "restaurant", 3L));
        doThrow(new ApiException(403, "Acesso não autorizado"))
            .when(restaurantPermissions).require(any(User.class), any(String[].class));

        ApiException error = assertThrows(ApiException.class, () -> controller().mySubscription("t"));

        assertEquals(403, error.status());
    }

    @Test
    void theStoreSeesItsPlanAndTheLivePixCharge() {
        when(auth.requireUser("t", "restaurant")).thenReturn(new User(5, "Dono", "dono@loja.com", "restaurant", 3L));
        when(jdbc.queryForList(any(String.class), any(Object[].class)))
            .thenReturn(List.of(Map.of("id", 7L, "package_name", "Plano Essencial", "status", "active")));
        when(charges.findLiveByRestaurant(3L)).thenReturn(Optional.of(Map.of("id", 9L, "status", "pending", "qr_code", "000201")));
        when(account.publicKey()).thenReturn("pk-test");
        when(account.cardEnabled()).thenReturn(true);

        Map<String, Object> result = controller().mySubscription("t");

        assertEquals("Plano Essencial", ((Map<?, ?>) result.get("subscription")).get("package_name"));
        assertEquals("pending", ((Map<?, ?>) result.get("charge")).get("status"));
        assertEquals("pk-test", result.get("publicKey"));
        assertEquals(true, result.get("cardEnabled"));
    }

    @Test
    void theStoreWithoutSubscriptionGetsAnEmptyPlan() {
        when(auth.requireUser("t", "restaurant")).thenReturn(new User(5, "Dono", "dono@loja.com", "restaurant", 3L));
        when(jdbc.queryForList(any(String.class), any(Object[].class))).thenReturn(List.of());
        when(charges.findLiveByRestaurant(3L)).thenReturn(Optional.empty());

        Map<String, Object> result = controller().mySubscription("t");

        assertNull(result.get("subscription"));
        assertNull(result.get("charge"));
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
