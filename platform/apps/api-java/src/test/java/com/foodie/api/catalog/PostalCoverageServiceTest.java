package com.foodie.api.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.api.ApiException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

/** CEP → bairro: cache, consulta externa, CEP sem bairro, erros e conferência do endereço. */
@SuppressWarnings("unchecked")
class PostalCoverageServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PostalDirectory directory = mock(PostalDirectory.class);
    private final PostalLookupLimiter limiter = mock(PostalLookupLimiter.class);
    private final PostalCoverageService service = new PostalCoverageService(jdbc, directory, limiter);

    private void cache(Object result) {
        when(jdbc.query(contains("FROM postal_code_cache"), any(RowMapper.class), any(Object[].class)))
            .thenAnswer(invocation -> result == null ? List.of() : List.of(result));
    }

    private Object cachedPlace(Long zoneId, String city, String state) throws Exception {
        // O registro Place é privado: monta pelo próprio mapeador da consulta.
        java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
        when(rs.getObject("zone_id")).thenReturn(zoneId);
        when(rs.getLong("zone_id")).thenReturn(zoneId == null ? 0 : zoneId);
        when(rs.getString("city")).thenReturn(city);
        when(rs.getString("state")).thenReturn(state);
        Object[] holder = new Object[1];
        when(jdbc.query(contains("FROM postal_code_cache"), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
            RowMapper<Object> mapper = invocation.getArgument(1);
            holder[0] = mapper.mapRow(rs, 0);
            return List.of(holder[0]);
        });
        return holder;
    }

    private void zoneRow(long id, String name) {
        when(jdbc.queryForMap(contains("FROM zones WHERE id"), eq(id))).thenReturn(Map.of("id", id, "name", name));
    }

    private void coveredZone(boolean covered) {
        when(jdbc.query(contains("FROM restaurant_zones rz"), any(ResultSetExtractor.class), any(Object[].class))).thenReturn(covered ? 1 : null);
    }

    @Test
    void cachedCepAnswersWithoutCallingTheExternalService() throws Exception {
        cachedPlace(7L, "Fortaleza", "CE");
        zoneRow(7L, "Aldeota");
        coveredZone(true);

        Map<String, Object> out = service.resolve("60150-160", "1.2.3.4");

        assertEquals(7L, out.get("id"));
        assertEquals("Aldeota", out.get("name"));
        assertEquals(true, out.get("covered"));
        verify(directory, never()).lookup(anyString());
        verify(limiter, never()).allow(anyString());
    }

    @Test
    void newCepIsLookedUpCreatesNoDuplicateZoneAndIsCached() {
        cache(null);
        when(limiter.allow("1.2.3.4")).thenReturn(true);
        when(directory.lookup("60150160")).thenReturn(new PostalDirectory.Found("Fortaleza", "CE", "Aldeôta"));
        // O bairro já existe (outra grafia, mesma chave): reaproveita.
        when(jdbc.query(contains("neighborhood_key = ?"), any(RowMapper.class), eq("CE"), eq("fortaleza"), eq("aldeota"))).thenReturn(List.of(7L));
        zoneRow(7L, "Aldeota");
        coveredZone(false);

        Map<String, Object> out = service.resolve("60150160", "1.2.3.4");

        assertEquals(7L, out.get("id"));
        assertEquals(false, out.get("covered"));
        verify(jdbc, never()).update(contains("INSERT IGNORE INTO zones"), any(Object[].class));
        verify(jdbc).update(contains("INSERT INTO postal_code_cache"), eq("60150160"), eq(7L), eq("Fortaleza"), eq("CE"), eq("Aldeôta"));
    }

    @Test
    void cepWithoutNeighborhoodOffersTheCityChoices() {
        cache(null);
        when(limiter.allow(any())).thenReturn(true);
        when(directory.lookup("62900000")).thenReturn(new PostalDirectory.Found("Russas", "CE", ""));
        when(jdbc.queryForList(contains("SELECT DISTINCT z.id, z.name"), eq("CE"), eq("russas")))
            .thenReturn(List.of(Map.of("id", 30L, "name", "Centro"), Map.of("id", 31L, "name", "Planalto")));

        Map<String, Object> out = service.resolve("62900000", "1.2.3.4");

        assertNull(out.get("id"));
        assertEquals(2, ((List<?>) out.get("choices")).size());
        assertEquals(true, out.get("covered"));
        verify(jdbc).update(contains("INSERT INTO postal_code_cache"), eq("62900000"), eq(null), eq("Russas"), eq("CE"), eq(""));
    }

    @Test
    void unknownCepIs404AndServiceDownIs503() {
        cache(null);
        when(limiter.allow(any())).thenReturn(true);
        when(directory.lookup("99999999")).thenReturn(new PostalDirectory.NotFound());
        when(directory.lookup("60150160")).thenReturn(new PostalDirectory.Unavailable());

        assertEquals(404, assertThrows(ApiException.class, () -> service.resolve("99999999", "ip")).status());
        assertEquals(503, assertThrows(ApiException.class, () -> service.resolve("60150160", "ip")).status());
    }

    @Test
    void tooManyLookupsFromTheSameIpIs429WithoutCallingOut() {
        cache(null);
        when(limiter.allow("9.9.9.9")).thenReturn(false);

        assertEquals(429, assertThrows(ApiException.class, () -> service.resolve("60150160", "9.9.9.9")).status());
        verify(directory, never()).lookup(anyString());
    }

    @Test
    void malformedCepIs400() {
        assertEquals(400, assertThrows(ApiException.class, () -> service.resolve("123")).status());
    }

    @Test
    void addressInAnotherNeighborhoodIsRejected() throws Exception {
        cachedPlace(7L, "Fortaleza", "CE");
        zoneRow(7L, "Aldeota");
        coveredZone(true);

        assertEquals(409, assertThrows(ApiException.class, () -> service.requireAddressZone("60150160", 8L)).status());
        service.requireAddressZone("60150160", 7L);
    }

    @Test
    void chosenNeighborhoodMustComeFromTheList() {
        Map<String, Object> place = Map.of("city", "Russas", "state", "CE",
            "choices", List.of(Map.of("id", 30L, "name", "Centro")));
        java.util.HashMap<String, Object> withNullId = new java.util.HashMap<>(place);
        withNullId.put("id", null);

        assertEquals(30L, service.zoneForAddress(withNullId, 30L));
        assertEquals(400, assertThrows(ApiException.class, () -> service.zoneForAddress(withNullId, 99L)).status());
        assertEquals(400, assertThrows(ApiException.class, () -> service.zoneForAddress(withNullId, null)).status());
        assertEquals(7L, service.zoneForAddress(Map.of("id", 7L), 99L));
    }
}
