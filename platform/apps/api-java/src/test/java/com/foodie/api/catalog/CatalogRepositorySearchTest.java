package com.foodie.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.api.hours.RestaurantHoursService;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class CatalogRepositorySearchTest {
    private NamedParameterJdbcTemplate named;
    private CatalogRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        RestaurantHoursService hours = mock(RestaurantHoursService.class);
        when(hours.localTimeSql(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn("?");
        repository = new CatalogRepository(mock(JdbcTemplate.class), hours);
        // O repositório cria o NamedParameterJdbcTemplate sozinho; troca por um mock para capturar o SQL com os nomes dos parâmetros.
        named = mock(NamedParameterJdbcTemplate.class);
        when(named.queryForList(anyString(), anyMap())).thenReturn(List.of());
        Field field = CatalogRepository.class.getDeclaredField("namedJdbc");
        field.setAccessible(true);
        field.set(repository, named);
    }

    private String capturedSql() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(named).queryForList(sql.capture(), anyMap());
        return sql.getValue();
    }

    @Test
    void searchWithoutZoneStartsFromRestaurantAndKeepsAvailabilityFilters() {
        repository.search(null, "", 3L, null, null, null, 12);

        String sql = capturedSql();
        assertThat(sql).doesNotContain("restaurant_zones").doesNotContain(":zoneId");
        assertThat(sql).contains("FROM restaurants r").contains("r.active = TRUE").contains("r.id = :restaurantId");
        assertThat(sql).contains("p.available = TRUE").contains("BETWEEN p.available_from AND p.available_until").contains("p.stock IS NULL OR p.stock > 0");
    }

    @Test
    void searchWithZoneKeepsZoneJoin() {
        repository.search(5L, "", 3L, null, null, null, 12);

        assertThat(capturedSql()).contains("rz.zone_id = :zoneId").contains("restaurant_zones");
    }
}
