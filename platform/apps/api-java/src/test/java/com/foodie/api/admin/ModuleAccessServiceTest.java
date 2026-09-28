package com.foodie.api.admin;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ModuleAccessServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ModuleAccessService service = new ModuleAccessService(jdbc);

    @Test
    void disabledModuleIsForbidden() {
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.eq(Integer.class),
            org.mockito.ArgumentMatchers.eq(7L),
            org.mockito.ArgumentMatchers.eq("inventory"))).thenReturn(0);
        assertThrows(ApiException.class, () -> service.require(7L, "inventory"));
    }

    @Test
    void enabledModuleIsAllowed() {
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.eq(Integer.class),
            org.mockito.ArgumentMatchers.eq(7L),
            org.mockito.ArgumentMatchers.eq("inventory"))).thenReturn(1);
        assertDoesNotThrow(() -> service.require(7L, "inventory"));
    }
}
