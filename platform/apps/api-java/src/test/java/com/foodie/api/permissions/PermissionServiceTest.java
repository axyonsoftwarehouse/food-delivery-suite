package com.foodie.api.permissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import com.foodie.api.auth.User;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PermissionServiceTest {
    private final RoleRepository roles = mock(RoleRepository.class);
    private final PermissionService service = new PermissionService(roles);

    @Test
    void adminHasEveryPermission() {
        User admin = new User(1, "Admin", "admin@demo.local", "admin", null);
        assertThat(service.has(admin, Permissions.SETTINGS_MANAGE)).isTrue();
        assertThat(service.has(admin, Permissions.CATALOG_MANAGE)).isTrue();
    }

    @Test
    void ownerKeepsFullAccessWithoutStaffRole() {
        User owner = new User(2, "Dono", "dono@demo.local", "restaurant", 7L);
        when(roles.findForUser(2)).thenReturn(Optional.empty());
        assertThat(service.has(owner, Permissions.CATALOG_MANAGE)).isTrue();
        assertThat(service.has(owner, Permissions.STAFF_MANAGE)).isTrue();
    }

    @Test
    void defaultKitchenIsLimitedToOrderActions() {
        User kitchen = new User(3, "Cozinha", "cozinha@demo.local", "kitchen", 7L);
        when(roles.findForUser(3)).thenReturn(Optional.empty());
        assertThat(service.has(kitchen, Permissions.ORDERS_VIEW)).isTrue();
        assertThat(service.has(kitchen, Permissions.ORDERS_ACCEPT)).isTrue();
        assertThat(service.has(kitchen, Permissions.ORDERS_READY)).isTrue();
        assertThat(service.has(kitchen, Permissions.CATALOG_MANAGE)).isFalse();
        assertThat(service.has(kitchen, Permissions.REPORTS_VIEW)).isFalse();
    }

    @Test
    void customRoleOverridesDefaults() {
        User waiter = new User(4, "Garçom", "garcom@demo.local", "restaurant", 7L);
        when(roles.findForUser(4)).thenReturn(Optional.of(new RoleRepository.Role(9, 7, "Garçom", List.of(Permissions.ORDERS_VIEW))));
        assertThat(service.has(waiter, Permissions.ORDERS_VIEW)).isTrue();
        assertThat(service.has(waiter, Permissions.CATALOG_MANAGE)).isFalse();
        assertThat(service.has(waiter, Permissions.STAFF_MANAGE)).isFalse();
    }

    @Test
    void requireThrowsForbiddenWhenMissing() {
        User kitchen = new User(3, "Cozinha", "cozinha@demo.local", "kitchen", 7L);
        when(roles.findForUser(3)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.require(kitchen, Permissions.STAFF_MANAGE))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).status()).isEqualTo(403));
    }
}
