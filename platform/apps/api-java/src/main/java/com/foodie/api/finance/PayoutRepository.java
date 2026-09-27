package com.foodie.api.finance;

import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PayoutRepository {
    private final JdbcTemplate jdbc;

    public PayoutRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Method(long id, String party, long partyId, String type, String details, boolean active) {}

    public List<Method> methods(String party, long partyId) {
        return jdbc.query(
            "SELECT id, party, party_id, type, details, active FROM payout_methods WHERE party = ? AND party_id = ? ORDER BY id DESC",
            (rs, row) -> new Method(rs.getLong("id"), rs.getString("party"), rs.getLong("party_id"), rs.getString("type"), rs.getString("details"), rs.getBoolean("active")),
            party, partyId);
    }

    public long createMethod(String party, long partyId, String type, String details) {
        var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO payout_methods (party, party_id, type, details) VALUES (?, ?, ?, ?)",
                java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, party);
            statement.setLong(2, partyId);
            statement.setString(3, type);
            statement.setString(4, details);
            return statement;
        }, key);
        return key.getKey().longValue();
    }

    public Optional<Method> findMethod(long id) {
        return jdbc.query("SELECT id, party, party_id, type, details, active FROM payout_methods WHERE id = ?",
            (rs, row) -> new Method(rs.getLong("id"), rs.getString("party"), rs.getLong("party_id"), rs.getString("type"), rs.getString("details"), rs.getBoolean("active")),
            id).stream().findFirst();
    }

    public int deleteMethod(String party, long partyId, long id) {
        return jdbc.update("DELETE FROM payout_methods WHERE id = ? AND party = ? AND party_id = ?", id, party, partyId);
    }

    public long reserved(String party, long partyId) {
        Long total = jdbc.queryForObject(
            "SELECT COALESCE(SUM(amount_cents),0) FROM payout_requests WHERE party = ? AND party_id = ? AND status IN ('requested','approved')",
            Long.class, party, partyId);
        return total == null ? 0L : total;
    }

    public long createRequest(String party, long partyId, long amountCents, Long methodId, String note) {
        var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO payout_requests (party, party_id, amount_cents, method_id, note) VALUES (?, ?, ?, ?, ?)",
                java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, party);
            statement.setLong(2, partyId);
            statement.setLong(3, amountCents);
            if (methodId == null) statement.setNull(4, Types.BIGINT); else statement.setLong(4, methodId);
            statement.setString(5, note == null ? "" : note);
            return statement;
        }, key);
        return key.getKey().longValue();
    }

    public List<Map<String, Object>> requests(String party, long partyId) {
        return jdbc.queryForList(
            "SELECT p.id, p.amount_cents, p.status, p.note, p.method_id, p.created_at, p.decided_at, p.paid_at, m.type AS method_type, m.details AS method_details "
                + "FROM payout_requests p LEFT JOIN payout_methods m ON m.id = p.method_id "
                + "WHERE p.party = ? AND p.party_id = ? ORDER BY p.id DESC", party, partyId);
    }

    public List<Map<String, Object>> adminRequests(String status) {
        return jdbc.queryForList(
            "SELECT p.id, p.party, p.party_id, p.amount_cents, p.status, p.note, p.method_id, p.created_at, p.decided_at, p.paid_at, "
                + "CASE p.party WHEN 'restaurant' THEN (SELECT name FROM restaurants WHERE id = p.party_id) ELSE (SELECT name FROM users WHERE id = p.party_id) END AS party_name "
                + "FROM payout_requests p WHERE (? IS NULL OR p.status = ?) ORDER BY p.id DESC LIMIT 200", status, status);
    }

    public Optional<Map<String, Object>> findRequest(long id) {
        return jdbc.queryForList("SELECT id, party, party_id, amount_cents, status, method_id FROM payout_requests WHERE id = ?", id).stream().findFirst();
    }

    public int updateStatus(long id, String status, Long decidedBy, String note) {
        String sql = switch (status) {
            case "approved" -> "UPDATE payout_requests SET status = 'approved', decided_by = ?, decided_at = NOW(), note = ? WHERE id = ? AND status = 'requested'";
            case "rejected" -> "UPDATE payout_requests SET status = 'rejected', decided_by = ?, decided_at = NOW(), note = ? WHERE id = ? AND status IN ('requested','approved')";
            case "paid" -> "UPDATE payout_requests SET status = 'paid', decided_by = ?, decided_at = COALESCE(decided_at, NOW()), paid_at = NOW(), note = ? WHERE id = ? AND status IN ('requested','approved')";
            default -> throw new IllegalArgumentException("status inválido: " + status);
        };
        return jdbc.update(sql, decidedBy, note, id);
    }
}
