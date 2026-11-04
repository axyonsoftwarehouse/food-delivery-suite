package com.foodie.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.foodie.api.ApiException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;

/** Exclusão e exportação contra o schema real: cada tabela e coluna tocada precisa existir. */
@EnabledIfEnvironmentVariable(named = "FOODIE_INTEGRATION_DB", matches = "1")
class PrivacyDatabaseIntegrationTest {
    private JdbcTemplate jdbc;
    private PrivacyService privacy;
    private long restaurantId;
    private long zoneId;
    private long customerId;
    private long orderId;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource("jdbc:mariadb://127.0.0.1:3308/foodie_platform",
            "foodie", Objects.requireNonNull(System.getenv("DB_PASSWORD")));
        jdbc = new JdbcTemplate(dataSource);
        privacy = new PrivacyService(jdbc, new AuthRepository(jdbc), new PasswordVerifier(), Mockito.mock(MailService.class));
        String suffix = UUID.randomUUID().toString();
        restaurantId = insert("INSERT INTO restaurants (name, slug) VALUES (?, ?)", "Privacidade", "privacidade-" + suffix);
        zoneId = insert("INSERT INTO zones (name, slug, city, state, delivery_fee_cents) VALUES ('Centro', ?, 'Fortaleza', 'CE', 500)",
            "privacidade-" + suffix);
        customerId = new AuthRepository(jdbc).createCustomer("Maria Titular", "maria-" + suffix + "@demo.local", "hash").id();
        jdbc.update("UPDATE users SET phone = ? WHERE id = ?", "+5585" + (int) (Math.random() * 1_0000_0000), customerId);
        long addressId = insert("INSERT INTO addresses (user_id, zone_id, label, street, number, neighborhood) "
            + "VALUES (?, ?, 'Casa', 'Rua das Flores', '10', 'Centro')", customerId, zoneId);
        orderId = insert("INSERT INTO orders (customer_id, restaurant_id, status, total_cents, zone_id, address_id, "
            + "delivery_address_text, contact_phone, customer_note) VALUES (?, ?, 'delivered', 3000, ?, ?, "
            + "'Rua das Flores, 10', '+5585999990000', 'tocar o interfone')", customerId, restaurantId, zoneId, addressId);
        jdbc.update("INSERT INTO sessions (token_hash, user_id, expires_at) VALUES (?, ?, DATE_ADD(NOW(), INTERVAL 1 DAY))",
            Tokens.hash(suffix), customerId);
        jdbc.update("INSERT INTO loyalty_transactions (user_id, order_id, points, kind) VALUES (?, ?, 30, 'earn')", customerId, orderId);
    }

    @AfterEach
    void cleanUp() {
        if (jdbc == null || restaurantId == 0) return;
        jdbc.update("DELETE FROM orders WHERE restaurant_id = ?", restaurantId);
        jdbc.update("DELETE FROM loyalty_transactions WHERE user_id = ?", customerId);
        jdbc.update("DELETE FROM sessions WHERE user_id = ?", customerId);
        jdbc.update("DELETE FROM addresses WHERE user_id = ?", customerId);
        jdbc.update("DELETE FROM users WHERE id = ?", customerId);
        jdbc.update("DELETE FROM zones WHERE id = ?", zoneId);
        jdbc.update("DELETE FROM restaurants WHERE id = ?", restaurantId);
    }

    @Test
    void signupRecordsTheTermsVersion() {
        Map<String, Object> row = jdbc.queryForMap("SELECT terms_version, terms_accepted_at FROM users WHERE id = ?", customerId);
        assertEquals(LegalTerms.VERSION, row.get("terms_version"));
        assertNotNull(row.get("terms_accepted_at"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void exportBringsAccountAddressesAndOrders() {
        Map<String, Object> data = privacy.export(new User(customerId, "Maria Titular", "", "customer", null));

        assertEquals("Maria Titular", ((Map<String, Object>) data.get("account")).get("name"));
        assertEquals(1, ((List<?>) data.get("addresses")).size());
        assertEquals("Rua das Flores, 10", ((List<Map<String, Object>>) data.get("orders")).getFirst().get("delivery_address_text"));
        assertEquals(1, ((List<?>) data.get("loyaltyPoints")).size());
    }

    @Test
    void deleteAnonymizesTheAccountAndKeepsTheOrderForTheStore() {
        privacy.deleteAccount(new User(customerId, "Maria Titular", "", "customer", null));

        Map<String, Object> user = jdbc.queryForMap(
            "SELECT name, email, phone, deleted_at, suspended_at FROM users WHERE id = ?", customerId);
        assertEquals("Conta excluída", user.get("name"));
        assertEquals("excluida-" + customerId + "@excluida.invalid", user.get("email"));
        assertNull(user.get("phone"));
        assertNotNull(user.get("deleted_at"));
        assertNotNull(user.get("suspended_at"));

        Map<String, Object> order = jdbc.queryForMap(
            "SELECT status, total_cents, address_id, delivery_address_text, contact_phone, customer_note FROM orders WHERE id = ?", orderId);
        assertEquals("delivered", order.get("status"));
        assertEquals(3000L, ((Number) order.get("total_cents")).longValue());
        assertNull(order.get("address_id"));
        assertNull(order.get("contact_phone"));
        assertNull(order.get("customer_note"));
        assertEquals("Endereço removido a pedido do titular", order.get("delivery_address_text"));

        assertEquals(0, count("SELECT COUNT(*) FROM addresses WHERE user_id = ?"));
        assertEquals(0, count("SELECT COUNT(*) FROM sessions WHERE user_id = ?"));
        assertEquals(0, count("SELECT COUNT(*) FROM loyalty_transactions WHERE user_id = ?"));

        // Reativar pela suspensão não traz a conta de volta.
        assertTrue(!new AuthRepository(jdbc).setSuspended(customerId, false, null));
        assertEquals(409, assertThrows(ApiException.class,
            () -> privacy.deleteAccount(new User(customerId, "Conta excluída", "", "customer", null))).status());
    }

    @Test
    void deleteIsBlockedWhileAnOrderIsInProgress() {
        jdbc.update("UPDATE orders SET status = 'accepted' WHERE id = ?", orderId);

        assertEquals(409, assertThrows(ApiException.class,
            () -> privacy.deleteAccount(new User(customerId, "Maria Titular", "", "customer", null))).status());
        assertEquals("Maria Titular", jdbc.queryForObject("SELECT name FROM users WHERE id = ?", String.class, customerId));
    }

    private int count(String sql) {
        return Objects.requireNonNull(jdbc.queryForObject(sql, Integer.class, customerId));
    }

    private long insert(String sql, Object... args) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, key);
        return key.getKey().longValue();
    }
}
