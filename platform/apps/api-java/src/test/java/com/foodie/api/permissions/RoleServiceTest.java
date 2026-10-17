package com.foodie.api.permissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.foodie.api.ApiException;
import com.foodie.api.auth.PasswordVerifier;
import org.junit.jupiter.api.Test;

class RoleServiceTest {
    @Test
    void concurrentAssignmentDuringDeletionReturnsConflict() {
        RoleRepository repository = mock(RoleRepository.class);
        when(repository.find(7, 3)).thenReturn(java.util.Optional.of(new RoleRepository.Role(3, 7, "Equipe", java.util.List.of())));
        when(repository.delete(7, 3)).thenThrow(new org.springframework.dao.DataIntegrityViolationException("FK restrict"));
        assertEquals(409, assertThrows(ApiException.class, () -> new RoleService(repository, mock(PasswordVerifier.class)).delete(7, 3)).status());
    }
    @Test
    void creatingPanelStaffWithoutAPermissionRoleCannotCreateAnotherOwner() {
        RoleRepository repository = mock(RoleRepository.class);
        PasswordVerifier passwords = mock(PasswordVerifier.class);
        RoleService service = new RoleService(repository, passwords);
        assertEquals(400, assertThrows(ApiException.class, () ->
            service.createStaff(7, "Equipe", "e@demo.local", "senha-teste-12345", "restaurant", null)).status());
        verifyNoInteractions(repository, passwords);
    }
}
