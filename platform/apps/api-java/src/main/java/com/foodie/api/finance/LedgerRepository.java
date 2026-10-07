package com.foodie.api.finance;

import java.sql.Types;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LedgerRepository {
    private final JdbcTemplate jdbc;

    public LedgerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean orderReversed(long orderId) {
        Integer found = jdbc.query("SELECT 1 FROM ledger_entries WHERE order_id = ? AND kind = 'refund' LIMIT 1", rs -> rs.next() ? 1 : null, orderId);
        return found != null;
    }

    public void insert(String party, Long partyId, Long orderId, String kind, long amountCents, String description) {
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                "INSERT INTO ledger_entries (party, party_id, order_id, kind, amount_cents, description) VALUES (?, ?, ?, ?, ?, ?)");
            statement.setString(1, party);
            if (partyId == null) statement.setNull(2, Types.BIGINT); else statement.setLong(2, partyId);
            if (orderId == null) statement.setNull(3, Types.BIGINT); else statement.setLong(3, orderId);
            statement.setString(4, kind);
            statement.setLong(5, amountCents);
            statement.setString(6, description == null ? "" : description);
            return statement;
        });
    }

    public long sum(String party, Long partyId) {
        Long total = jdbc.queryForObject(
            "SELECT COALESCE(SUM(amount_cents),0) FROM ledger_entries WHERE party = ? AND (party_id <=> ?)",
            Long.class, party, partyId);
        return total == null ? 0L : total;
    }

    public List<Map<String, Object>> list(String party, Long partyId, String from, String to, int limit) {
        return jdbc.queryForList(
            "SELECT id, party, party_id, order_id, kind, amount_cents, currency, description, created_at "
                + "FROM ledger_entries WHERE party = ? AND (party_id <=> ?) "
                + "AND (? IS NULL OR created_at >= ?) AND (? IS NULL OR created_at < DATE_ADD(?, INTERVAL 1 DAY)) "
                + "ORDER BY id DESC LIMIT ?",
            party, partyId, from, from, to, to, limit);
    }

    public List<Map<String, Object>> forOrder(long orderId) {
        return jdbc.queryForList("SELECT party, party_id, amount_cents FROM ledger_entries WHERE order_id = ? AND kind <> 'refund'", orderId);
    }
}
