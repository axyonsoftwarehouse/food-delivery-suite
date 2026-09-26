package com.foodie.api.hours;

import com.foodie.api.ApiException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Time;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;

@Service
public class RestaurantHoursService {
    private final JdbcTemplate jdbc;

    public RestaurantHoursService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isOpen(long restaurantId, String timezone) {
        if (timezone == null || timezone.isBlank()) return true;
        LocalDateTime now;
        try { now = LocalDateTime.now(ZoneId.of(timezone)); }
        catch (RuntimeException error) { return false; }
        return RestaurantSchedule.isOpen(intervals(restaurantId), RestaurantSchedule.dayOfWeek(now), now.toLocalTime());
    }

    /** Verifica se o restaurante está aberto em um horário local específico (para pedidos agendados). */
    public void requireOpenAt(long restaurantId, String timezone, LocalDateTime local) {
        if (timezone == null || timezone.isBlank()) return;
        if (!RestaurantSchedule.isOpen(intervals(restaurantId), RestaurantSchedule.dayOfWeek(local), local.toLocalTime())) {
            throw new ApiException(409, "O restaurante não abre nesse horário");
        }
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
