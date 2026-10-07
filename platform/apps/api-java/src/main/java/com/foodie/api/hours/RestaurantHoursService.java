package com.foodie.api.hours;

import com.foodie.api.ApiException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Time;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;

@Service
public class RestaurantHoursService {
    private final JdbcTemplate jdbc;

    private static final String PAUSE_SQL =
        "SELECT DATE_FORMAT(support_paused_until, '%Y-%m-%dT%H:%i:%sZ') FROM restaurants "
            + "WHERE id = ? AND support_paused_until > UTC_TIMESTAMP()";

    public RestaurantHoursService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Instant> pausedUntil(long restaurantId) {
        List<String> rows = jdbc.query(PAUSE_SQL, (rs, row) -> rs.getString(1), restaurantId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(Instant.parse(rows.get(0)));
    }

    public Map<String, Object> pauseInfo(long restaurantId) {
        Optional<Instant> until = pausedUntil(restaurantId);
        if (until.isEmpty()) return null;
        String reason = jdbc.queryForObject("SELECT support_pause_reason FROM restaurants WHERE id = ?", String.class, restaurantId);
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("until", until.get().toString());
        info.put("reason", reason);
        return info;
    }

    public Map<String, Object> pause(long restaurantId, int minutes, String reason) {
        jdbc.update("UPDATE restaurants SET support_paused_until = DATE_ADD(UTC_TIMESTAMP(), INTERVAL ? MINUTE), support_pause_reason = ? WHERE id = ?",
            minutes, reason, restaurantId);
        return pauseInfo(restaurantId);
    }

    public void resume(long restaurantId) {
        if (pausedUntil(restaurantId).isEmpty()) throw new ApiException(409, "A loja não está pausada");
        jdbc.update("UPDATE restaurants SET support_paused_until = NULL, support_pause_reason = NULL WHERE id = ?", restaurantId);
    }

    public boolean isOpen(long restaurantId, String timezone) {
        if (pausedUntil(restaurantId).isPresent()) return false;
        if (timezone == null || timezone.isBlank()) return true;
        LocalDateTime now;
        try { now = LocalDateTime.now(ZoneId.of(timezone)); }
        catch (RuntimeException error) { return false; }
        return RestaurantSchedule.isOpen(intervals(restaurantId), RestaurantSchedule.dayOfWeek(now), now.toLocalTime());
    }

    /** Verifica se o restaurante está aberto em um horário local específico (para pedidos agendados). */
    public void requireOpenAt(long restaurantId, String timezone, LocalDateTime local) {
        Optional<Instant> paused = pausedUntil(restaurantId);
        if (paused.isPresent()) {
            ZoneId zone = timezone == null || timezone.isBlank() ? ZoneOffset.UTC : ZoneId.of(timezone);
            if (local.atZone(zone).toInstant().isBefore(paused.get())) {
                String end = DateTimeFormatter.ofPattern("dd/MM HH:mm").format(paused.get().atZone(zone));
                throw new ApiException(409, "A loja está pausada pelo suporte até " + end);
            }
        }
        if (timezone == null || timezone.isBlank()) return;
        if (!RestaurantSchedule.isOpen(intervals(restaurantId), RestaurantSchedule.dayOfWeek(local), local.toLocalTime())) {
            throw new ApiException(409, "O restaurante não abre nesse horário");
        }
    }

    /** Mesmo padrão da coluna {@code restaurants.timezone}; usado quando o valor está vazio ou inválido. */
    public static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Fortaleza");

    public static ZoneId zone(String timezone) {
        if (timezone == null || timezone.isBlank()) return DEFAULT_ZONE;
        try { return ZoneId.of(timezone); }
        catch (RuntimeException error) { return DEFAULT_ZONE; }
    }

    /**
     * Expressão SQL com a hora local de cada loja, para comparar com {@code available_from/until}.
     * {@code CURTIME()} é a hora do banco (UTC), não a da loja; e o MariaDB do Docker não traz as tabelas
     * de fuso que o {@code CONVERT_TZ} precisaria. A API calcula a hora de cada fuso cadastrado e monta
     * {@code CASE <coluna> WHEN ? THEN ? ... ELSE ? END}; {@code bind} registra cada valor e devolve o
     * marcador ({@code ?} ou {@code :nome}).
     */
    public String localTimeSql(String timezoneColumn, java.util.function.Function<Object, String> bind) {
        List<String> zones = jdbc.queryForList("SELECT DISTINCT timezone FROM restaurants", String.class);
        // `bind` é chamado na mesma ordem em que os marcadores aparecem no SQL (importa para `?`).
        if (zones.isEmpty()) return bind.apply(Time.valueOf(LocalTime.now(DEFAULT_ZONE).withNano(0)));
        StringBuilder sql = new StringBuilder("CASE ").append(timezoneColumn);
        for (String timezone : zones) {
            sql.append(" WHEN ").append(bind.apply(timezone))
                .append(" THEN ").append(bind.apply(Time.valueOf(LocalTime.now(zone(timezone)).withNano(0))));
        }
        return sql.append(" ELSE ").append(bind.apply(Time.valueOf(LocalTime.now(DEFAULT_ZONE).withNano(0)))).append(" END").toString();
    }

    public String timezone(long restaurantId) {
        List<String> values = jdbc.query("SELECT timezone FROM restaurants WHERE id = ?", (rs, row) -> rs.getString("timezone"), restaurantId);
        if (values.isEmpty()) throw new ApiException(404, "Restaurante não encontrado");
        return values.get(0);
    }

    public void updateTimezone(long restaurantId, String timezone) {
        try { ZoneId.of(timezone); }
        catch (RuntimeException error) { throw new ApiException(400, "Fuso horário inválido"); }
        if (jdbc.update("UPDATE restaurants SET timezone = ? WHERE id = ?", timezone, restaurantId) == 0) {
            throw new ApiException(404, "Restaurante não encontrado");
        }
    }

    public List<Map<String, Object>> list(long restaurantId) {
        return jdbc.query(
            "SELECT id, day_of_week, opens_at, closes_at FROM restaurant_hours WHERE restaurant_id = ? ORDER BY day_of_week, opens_at",
            (rs, row) -> {
                LocalTime opensAt = rs.getObject("opens_at", LocalTime.class);
                LocalTime closesAt = rs.getObject("closes_at", LocalTime.class);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", rs.getLong("id"));
                item.put("dayOfWeek", rs.getInt("day_of_week"));
                item.put("opensAt", format(opensAt));
                item.put("closesAt", format(closesAt));
                item.put("overnight", closesAt.isBefore(opensAt));
                return item;
            }, restaurantId
        );
    }

    public long add(long restaurantId, int dayOfWeek, LocalTime opensAt, LocalTime closesAt) {
        if (opensAt.equals(closesAt)) throw new ApiException(400, "A abertura e o fechamento não podem ser iguais");
        if (jdbc.query("SELECT 1 FROM restaurants WHERE id = ?", rs -> rs.next() ? 1 : null, restaurantId) == null) {
            throw new ApiException(404, "Restaurante não encontrado");
        }
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO restaurant_hours (restaurant_id, day_of_week, opens_at, closes_at) VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, restaurantId);
            statement.setInt(2, dayOfWeek);
            statement.setTime(3, Time.valueOf(opensAt));
            statement.setTime(4, Time.valueOf(closesAt));
            return statement;
        }, key);
        return key.getKey().longValue();
    }

    public void remove(long restaurantId, long id) {
        if (jdbc.update("DELETE FROM restaurant_hours WHERE id = ? AND restaurant_id = ?", id, restaurantId) == 0) {
            throw new ApiException(404, "Horário não encontrado");
        }
    }

    private List<RestaurantSchedule.Interval> intervals(long restaurantId) {
        return jdbc.query(
            "SELECT day_of_week, opens_at, closes_at FROM restaurant_hours WHERE restaurant_id = ? ORDER BY day_of_week, opens_at",
            (rs, row) -> new RestaurantSchedule.Interval(rs.getInt("day_of_week"), rs.getObject("opens_at", LocalTime.class), rs.getObject("closes_at", LocalTime.class)),
            restaurantId
        );
    }

    private static String format(LocalTime time) {
        return String.format("%02d:%02d", time.getHour(), time.getMinute());
    }
}
