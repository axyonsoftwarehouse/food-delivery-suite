package com.foodie.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/** Teste de carga de 12/11: a sessão deixa de custar duas consultas em toda chamada. */
class AuthRepositorySessionCacheTest {
    private static final String HASH = "h".repeat(64);

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AuthRepository repository = new AuthRepository(jdbc, 3, 1440, 10);
    private final AtomicBoolean touchDue = new AtomicBoolean();
    private final AtomicBoolean sessionExists = new AtomicBoolean(true);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void sessionRow() {
        when(jdbc.query(startsWith("SELECT u.id"), any(RowMapper.class), anyString(), any())).thenAnswer(invocation -> {
            if (!sessionExists.get()) return List.of();
            ResultSet rs = mock(ResultSet.class);
            when(rs.getLong("id")).thenReturn(7L);
            when(rs.getString("name")).thenReturn("Loja");
            when(rs.getString("email")).thenReturn("loja@demo.local");
            when(rs.getString("role")).thenReturn("restaurant");
            when(rs.getLong("restaurant_id")).thenReturn(3L);
            when(rs.getBoolean("touch_due")).thenReturn(touchDue.get());
            RowMapper<Object> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(rs, 0));
        });
    }

    @SuppressWarnings("unchecked")
    private void verifySessionReads(int times) {
        verify(jdbc, times(times)).query(startsWith("SELECT u.id"), any(RowMapper.class), anyString(), any());
    }

    @Test
    void repeatedCallsReadTheSessionOnce() {
        assertThat(repository.findSessionUser(HASH)).get().extracting(User::id).isEqualTo(7L);
        assertThat(repository.findSessionUser(HASH)).get().extracting(User::restaurantId).isEqualTo(3L);
        verifySessionReads(1);
    }

    @Test
    void renewsTheIdleTimeoutOnlyWhenItIsDue() {
        repository.findSessionUser(HASH);
        verify(jdbc, never()).update(startsWith("UPDATE sessions"), eq(HASH));

        touchDue.set(true);
        repository.findSessionUser("t".repeat(64));
        verify(jdbc).update(startsWith("UPDATE sessions"), eq("t".repeat(64)));
    }

    @Test
    void logoutIsImmediate() {
        repository.findSessionUser(HASH);
        repository.deleteSession(HASH);
        sessionExists.set(false);
        assertThat(repository.findSessionUser(HASH)).isEmpty();
    }

    @Test
    void revokingTheUserSessionsIsImmediate() {
        // Suspensão, troca de senha e "sair de todos" passam por aqui.
        repository.findSessionUser(HASH);
        repository.deleteSessionsForUser(7);
        sessionExists.set(false);
        assertThat(repository.findSessionUser(HASH)).isEmpty();
    }

    @Test
    void forgettingTheUserRereadsIt() {
        repository.findSessionUser(HASH);
        repository.forgetUser(7);
        repository.findSessionUser(HASH);
        verifySessionReads(2);
    }

    @Test
    void unknownTokensAreNotRemembered() {
        sessionExists.set(false);
        repository.findSessionUser(HASH);
        sessionExists.set(true);
        assertThat(repository.findSessionUser(HASH)).isPresent();
    }

    @Test
    void theConvenienceConstructorDoesNotCache() {
        AuthRepository uncached = new AuthRepository(jdbc);
        uncached.findSessionUser(HASH);
        uncached.findSessionUser(HASH);
        verifySessionReads(2);
    }
}
