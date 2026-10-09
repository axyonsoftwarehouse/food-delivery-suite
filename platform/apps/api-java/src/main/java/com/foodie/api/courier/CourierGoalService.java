package com.foodie.api.courier;

import com.foodie.api.ApiException;
import com.foodie.api.hours.RestaurantHoursService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Meta semanal de entregas do entregador (parte C): definida por ele; semana de segunda a domingo no fuso da loja. */
@Service
public class CourierGoalService {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public CourierGoalService(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    CourierGoalService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public Map<String, Object> get(long courierId) {
        Map<String, Object> row = jdbc.queryForMap(
            "SELECT u.weekly_delivery_goal, r.timezone FROM users u LEFT JOIN restaurants r ON r.id = u.restaurant_id WHERE u.id = ?", courierId);
        ZoneId zone = RestaurantHoursService.zone((String) row.get("timezone"));
        WeekBounds week = WeekBounds.of(Instant.now(clock), zone);
        Long done = jdbc.queryForObject(
            "SELECT COUNT(DISTINCT o.id) FROM orders o JOIN order_events e ON e.order_id = o.id AND e.to_status = 'delivered' "
                + "WHERE o.courier_id = ? AND o.status = 'delivered' AND e.created_at >= ? AND e.created_at < ?",
            Long.class, courierId, Timestamp.from(week.from()), Timestamp.from(week.to()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("weeklyDeliveries", row.get("weekly_delivery_goal") instanceof Number goal ? goal.intValue() : null);
        result.put("doneThisWeek", done == null ? 0L : done);
        result.put("weekStart", week.weekStart().toString());
        result.put("weekEnd", week.weekEnd().toString());
        return result;
    }

    public Map<String, Object> set(long courierId, Integer weeklyDeliveries) {
        if (weeklyDeliveries != null && (weeklyDeliveries < 1 || weeklyDeliveries > 200)) {
            throw new ApiException(400, "A meta deve ser de 1 a 200 entregas por semana");
        }
        jdbc.update("UPDATE users SET weekly_delivery_goal = ? WHERE id = ? AND role = 'courier'", weeklyDeliveries, courierId);
        return get(courierId);
    }
}
