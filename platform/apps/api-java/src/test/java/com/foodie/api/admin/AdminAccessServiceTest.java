package com.foodie.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import com.foodie.api.auth.PasswordVerifier;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdminAccessServiceTest {
    private final AdminRoleRepository roles = mock(AdminRoleRepository.class);
    private final PasswordVerifier passwords = mock(PasswordVerifier.class);
    private final AdminAccessService service = new AdminAccessService(roles, passwords);
    private final AdminRoleRepository.Role full = new AdminRoleRepository.Role(1, "Acesso total", "", List.of(), true, true);
    private final AdminRoleRepository.Role support = new AdminRoleRepository.Role(2, "Suporte", "", List.of(AdminPermissions.ORDERS_MANAGE), true, false);

    @Test
    void creatingWithoutARoleUsesTheExplicitFullAccessRole() {
        when(roles.findFullAccessRole()).thenReturn(Optional.of(full));
        when(roles.findRoleForUser(1)).thenReturn(Optional.of(full));
        when(passwords.hash(anyString())).thenReturn("hash");
        when(roles.createEmployee(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.eq(1L))).thenReturn(9L);

        AdminRoleRepository.Employee employee = service.createEmployee(1L, "Maria", "maria@demo.local", "senha-bem-longa", null);

        assertThat(employee.adminRoleId()).isEqualTo(1L);
    }

    @Test
    void restrictedAdminCannotGrantFullAccess() {
        when(roles.findFullAccessRole()).thenReturn(Optional.of(full));
        when(roles.findRoleForUser(2)).thenReturn(Optional.of(support));

        assertThatThrownBy(() -> service.createEmployee(2L, "Maria", "maria@demo.local", "senha-bem-longa", null))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).status()).isEqualTo(403));
    }

    @Test
    void adminCannotChangeItsOwnRole() {
        when(roles.findEmployee(8)).thenReturn(Optional.of(new AdminRoleRepository.Employee(8, "Admin", "a@demo.local", 1L, false)));
        when(roles.findRole(2)).thenReturn(Optional.of(support));
        when(roles.findRoleForUser(8)).thenReturn(Optional.of(full));

        assertThatThrownBy(() -> service.updateEmployee(8L, 8L, "Admin", 2L))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).status()).isEqualTo(409));
    }

    @Test
    void systemRoleCannotBeEditedOrDeleted() {
        when(roles.findRole(1)).thenReturn(Optional.of(full));

        assertThatThrownBy(() -> service.updateRole(2L, 1L, "Outro", "", List.of(), true))
            .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.deleteRole(1L))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void restrictedAdminCannotCreateARoleWithPermissionsItDoesNotHave() {
        when(roles.findRoleForUser(2)).thenReturn(Optional.of(support));
        when(roles.roleNameExists("Financeiro")).thenReturn(false);

        assertThatThrownBy(() -> service.createRole(2L, "Financeiro", "", List.of(AdminPermissions.FINANCE_MANAGE), true))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).status()).isEqualTo(403));
    }

    @Test
    void adminCannotEditTheRoleAssignedToItself() {
        when(roles.findRole(2)).thenReturn(Optional.of(support));
        when(roles.roleAssignedTo(2, 8)).thenReturn(true);

        assertThatThrownBy(() -> service.updateRole(8L, 2L, "Suporte", "", List.of(AdminPermissions.ADMIN_MANAGE), true))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).status()).isEqualTo(409));
    }
}
