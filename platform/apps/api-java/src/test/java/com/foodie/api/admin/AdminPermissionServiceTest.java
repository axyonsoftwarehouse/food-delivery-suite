package com.foodie.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import com.foodie.api.auth.User;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdminPermissionServiceTest {
    private final AdminRoleRepository roles = mock(AdminRoleRepository.class);
    private final AdminPermissionService service = new AdminPermissionService(roles);

    @Test
    void fullAdminHasEveryPermission() {
        User admin = new User(1, "Admin", "admin@demo.local", "admin", null);
        when(roles.findRoleForUser(1)).thenReturn(Optional.empty());
        assertThat(service.has(admin, AdminPermissions.ADMIN_MANAGE)).isTrue();
        assertThat(service.has(admin, AdminPermissions.SETTINGS_MANAGE)).isTrue();
    }

    @Test
    void restrictedRoleLimitsPermissions() {
        User employee = new User(2, "Suporte", "suporte@demo.local", "admin", null);
        when(roles.findRoleForUser(2)).thenReturn(Optional.of(
            new AdminRoleRepository.Role(9, "Suporte", "", List.of(AdminPermissions.ORDERS_MANAGE), true)));
        assertThat(service.has(employee, AdminPermissions.ORDERS_MANAGE)).isTrue();
        assertThat(service.has(employee, AdminPermissions.ADMIN_MANAGE)).isFalse();
    }

    @Test
    void inactiveRoleRevokesAccess() {
        User employee = new User(3, "Ex-funcionário", "ex@demo.local", "admin", null);
        when(roles.findRoleForUser(3)).thenReturn(Optional.of(
            new AdminRoleRepository.Role(10, "Antigo", "", List.of(AdminPermissions.ORDERS_MANAGE), false)));
        assertThat(service.effective(employee)).isEmpty();
    }

    @Test
    void nonAdminHasNoAdminPermissions() {
        User customer = new User(4, "Cliente", "cliente@demo.local", "customer", null);
        assertThat(service.effective(customer)).isEmpty();
    }

    @Test
    void requireThrowsForbiddenWhenMissing() {
        User employee = new User(2, "Suporte", "suporte@demo.local", "admin", null);
        when(roles.findRoleForUser(2)).thenReturn(Optional.of(
            new AdminRoleRepository.Role(9, "Suporte", "", List.of(AdminPermissions.ORDERS_MANAGE), true)));
        assertThatThrownBy(() -> service.require(employee, AdminPermissions.ADMIN_MANAGE))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).status()).isEqualTo(403));
    }
}
