package com.foodie.api.courier;

import com.foodie.api.ApiException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Turno do entregador (parte D): "Estou disponível" abre, "Encerrar" fecha. A posição só é aceita em turno ou com
 * entrega em mãos; o quadro diz à loja quem está livre e onde. Um turno aberto por entregador é garantido pelo
 * índice único da V067.
 */
@Service
public class CourierShiftService {
    static final Duration STALE = Duration.ofHours(12);
    static final String OPEN_SINCE = "SELECT started_at FROM courier_shifts WHERE courier_id = ? AND ended_at IS NULL";
    static final String OPEN_IDS = "SELECT id FROM courier_shifts WHERE courier_id = ? AND ended_at IS NULL";
    static final String INSERT = "INSERT INTO courier_shifts (courier_id, restaurant_id, started_at) VALUES (?, ?, ?)";
    static final String CLOSE = "UPDATE courier_shifts SET ended_at = ? WHERE courier_id = ? AND ended_at IS NULL";
    static final String ACTIVE_COUNT = "SELECT COUNT(*) FROM orders WHERE courier_id = ? AND status IN ('assigned','picked_up')";
    static final String DELETE_LOCATION = "DELETE FROM courier_locations WHERE courier_id = ?";
    static final String CLOSE_STALE = "UPDATE courier_shifts SET ended_at = DATE_ADD(started_at, INTERVAL 12 HOUR) "
        + "WHERE ended_at IS NULL AND started_at < ?";
    /** Posição de quem não está em turno nem entregando (ex.: encerrou com entrega e depois entregou) é apagada. */
    static final String DELETE_ORPHAN_LOCATIONS = "DELETE FROM courier_locations WHERE NOT EXISTS "
        + "(SELECT 1 FROM courier_shifts s WHERE s.courier_id = courier_locations.courier_id AND s.ended_at IS NULL) "
        + "AND NOT EXISTS (SELECT 1 FROM orders o WHERE o.courier_id = courier_locations.courier_id AND o.status IN ('assigned','picked_up'))";
    static final String BOARD = "SELECT u.id, u.name, s.started_at AS shift_started_at, l.latitude, l.longitude, l.updated_at AS location_at, "
        + "(SELECT COUNT(*) FROM orders o WHERE o.courier_id = u.id AND o.status IN ('assigned','picked_up')) AS active_deliveries "
        + "FROM users u LEFT JOIN courier_shifts s ON s.courier_id = u.id AND s.ended_at IS NULL "
        + "LEFT JOIN courier_locations l ON l.courier_id = u.id "
        + "WHERE u.role = 'courier' AND u.restaurant_id = ? AND u.suspended_at IS NULL AND u.courier_approved_at IS NOT NULL";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public CourierShiftService(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    CourierShiftService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public record Opened(boolean created, Map<String, Object> shift) {}

    public record SupportShift(long id, boolean created) {}

    public Instant openSince(long courierId) {
        List<Timestamp> rows = jdbc.queryForList(OPEN_SINCE, Timestamp.class, courierId);
        return rows.isEmpty() ? null : rows.getFirst().toInstant();
    }

    public boolean isOnShift(long courierId) {
        return openSince(courierId) != null;
    }

    public Map<String, Object> status(long courierId) {
        Instant since = openSince(courierId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("open", since != null);
        if (since != null) result.put("startedAt", since.toString());
        return result;
    }

    public Opened open(long courierId) {
        Map<String, Object> courier = jdbc.queryForMap(
            "SELECT restaurant_id, courier_approved_at, suspended_at FROM users WHERE id = ? AND role = 'courier'", courierId);
        if (courier.get("restaurant_id") == null || courier.get("courier_approved_at") == null || courier.get("suspended_at") != null) {
            throw new ApiException(409, "Seu acesso não está liberado por uma loja: fale com a loja ou com o suporte");
        }
        if (openSince(courierId) != null) return new Opened(false, status(courierId));
        try {
            jdbc.update(INSERT, courierId, courier.get("restaurant_id"), Timestamp.from(Instant.now(clock)));
        } catch (DuplicateKeyException race) {
            // Dois toques (ou duas abas) ao mesmo tempo: o índice único da V067 deixa passar só um.
            return new Opened(false, status(courierId));
        }
        return new Opened(true, status(courierId));
    }

    public Map<String, Object> close(long courierId) {
        jdbc.update(CLOSE, Timestamp.from(Instant.now(clock)), courierId);
        Long active = jdbc.queryForObject(ACTIVE_COUNT, Long.class, courierId);
        boolean keepsSharing = active != null && active > 0;
        // Com entrega em mãos a posição continua (regra da parte A); sem entrega, a loja deixa de ver onde ele está.
        if (!keepsSharing) jdbc.update(DELETE_LOCATION, courierId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("keepsSharing", keepsSharing);
        return result;
    }

    public void closeForSuspension(long courierId) {
        jdbc.update(CLOSE, Timestamp.from(Instant.now(clock)), courierId);
        jdbc.update(DELETE_LOCATION, courierId);
    }

    public int closeStale() {
        int closed = jdbc.update(CLOSE_STALE, Timestamp.from(Instant.now(clock).minus(STALE)));
        jdbc.update(DELETE_ORPHAN_LOCATIONS);
        return closed;
    }

    public SupportShift openBySupport(long courierId) {
        List<Long> open = jdbc.queryForList(OPEN_IDS, Long.class, courierId);
        if (!open.isEmpty()) return new SupportShift(open.getFirst(), false);
        Long restaurantId = jdbc.queryForObject("SELECT restaurant_id FROM users WHERE id = ?", Long.class, courierId);
        boolean created = true;
        try {
            jdbc.update(INSERT, courierId, restaurantId, Timestamp.from(Instant.now(clock)));
        } catch (DuplicateKeyException race) {
            created = false;
        }
        return new SupportShift(jdbc.queryForList(OPEN_IDS, Long.class, courierId).getFirst(), created);
    }

    public Map<String, Object> board(long restaurantId) {
        Map<String, Object> store = jdbc.queryForMap("SELECT latitude, longitude FROM restaurants WHERE id = ?", restaurantId);
        Double storeLat = decimal(store.get("latitude"));
        Double storeLng = decimal(store.get("longitude"));
        List<CourierBoard.Row> rows = jdbc.query(BOARD, (rs, index) -> new CourierBoard.Row(
            rs.getLong("id"), rs.getString("name"), instant(rs.getTimestamp("shift_started_at")), rs.getLong("active_deliveries"),
            decimal(rs.getBigDecimal("latitude")), decimal(rs.getBigDecimal("longitude")), instant(rs.getTimestamp("location_at"))),
            restaurantId);
        Map<String, Object> restaurant = new LinkedHashMap<>();
        restaurant.put("latitude", storeLat);
        restaurant.put("longitude", storeLng);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("restaurant", restaurant);
        result.put("couriers", CourierBoard.build(rows, storeLat, storeLng, Instant.now(clock)));
        return result;
    }

    public Map<String, Object> history(long restaurantId, long courierId, int days) {
        if (days != 7 && days != 30) throw new ApiException(400, "Período inválido: use 7 ou 30 dias");
        Integer own = jdbc.query("SELECT 1 FROM users WHERE id = ? AND role = 'courier' AND restaurant_id = ?",
            rs -> rs.next() ? 1 : null, courierId, restaurantId);
        if (own == null) throw new ApiException(404, "Entregador não encontrado");
        Instant now = Instant.now(clock);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT started_at, ended_at FROM courier_shifts WHERE courier_id = ? AND restaurant_id = ? AND started_at >= ? ORDER BY started_at DESC",
            courierId, restaurantId, Timestamp.from(now.minus(Duration.ofDays(days))));
        List<Map<String, Object>> shifts = new ArrayList<>();
        long total = 0;
        for (Map<String, Object> row : rows) {
            Instant start = ((Timestamp) row.get("started_at")).toInstant();
            Instant end = row.get("ended_at") instanceof Timestamp ended ? ended.toInstant() : null;
            long minutes = minutes(start, end, now);
            total += minutes;
            Map<String, Object> shift = new LinkedHashMap<>();
            shift.put("startedAt", start.toString());
            shift.put("endedAt", end == null ? null : end.toString());
            shift.put("minutes", minutes);
            shifts.add(shift);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalMinutes", total);
        result.put("shifts", shifts);
        return result;
    }

    /** Duração em minutos; turno aberto conta até agora. */
    static long minutes(Instant start, Instant end, Instant now) {
        return Math.max(0, Duration.between(start, end == null ? now : end).toMinutes());
    }

    private static Double decimal(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
