package com.foodie.api.permissions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class RoleRepositoryCacheTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final RoleRepository roles = new RoleRepository(jdbc, 10);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void staffRole() {
        when(jdbc.query(startsWith("SELECT r.id"), any(RowMapper.class), anyLong()))
            .thenReturn(List.of(new RoleRepository.Role(4, 3, "Caixa", List.of(Permissions.ORDERS_VIEW))));
    }

    @SuppressWarnings("unchecked")
    private void verifyRoleReads(int times) {
        verify(jdbc, times(times)).query(startsWith("SELECT r.id"), any(RowMapper.class), anyLong());
    }

    @Test
    void repeatedPermissionChecksReadTheRoleOnce() {
        roles.findForUser(9);
        roles.findForUser(9);
        verifyRoleReads(1);
    }

    @Test
    void changingARoleOrItsAssignmentRereadsRightAway() {
        roles.findForUser(9);
        roles.update(3, 4, "Caixa", List.of());
        roles.findForUser(9);
        roles.assignStaffRole(3, 9, null);
        roles.findForUser(9);
        roles.delete(3, 4);
        roles.findForUser(9);
        verifyRoleReads(4);
    }
}
