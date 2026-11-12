package com.foodie.api.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;

/**
 * Teste de carga de 11/11/2026: a lista de pedidos encerrados da loja (`GET /orders`, a cada 8 s em
 * cada tela de loja) varria a tabela inteira pela chave primária — 10.269 linhas lidas para achar 100.
 * Com o índice (restaurant_id, id) da V081 o banco anda só pelos pedidos da própria loja.
 */
@EnabledIfEnvironmentVariable(named = "FOODIE_INTEGRATION_DB", matches = "1")
class OrderListIndexIntegrationTest {
    private JdbcTemplate jdbc;
    private long storeId;
    private long otherStoreId;
    private long zoneId;
    private long customerId;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource("jdbc:mariadb://127.0.0.1:3308/foodie_platform",
            "foodie", Objects.requireNonNull(System.getenv("DB_PASSWORD")));
        jdbc = new JdbcTemplate(dataSource);
        String suffix = UUID.randomUUID().toString();
        storeId = insert("INSERT INTO restaurants (name, slug) VALUES ('Índice A', ?)", "indice-a-" + suffix);
        otherStoreId = insert("INSERT INTO restaurants (name, slug) VALUES ('Índice B', ?)", "indice-b-" + suffix);
        zoneId = insert("INSERT INTO zones (name, neighborhood_key, slug, city, city_key, state) VALUES ('Índice', ?, ?, 'Fortaleza', 'fortaleza', 'CE')",
            "indice " + suffix, "indice-" + suffix);
        customerId = insert("INSERT INTO users (name, email, password_hash, role) VALUES ('Cliente Índice', ?, 'hash', 'customer')",
            "indice-" + suffix + "@demo.local");
        long addressId = insert("INSERT INTO addresses (user_id, zone_id, label, street, number, neighborhood) VALUES (?, ?, 'Casa', 'Rua A', '1', 'Centro')",
            customerId, zoneId);
        // Como no staging: a loja medida tem centenas de pedidos, e a tabela, muitos mais de outras lojas.
        bulkOrders(storeId, addressId, 300);
        bulkOrders(otherStoreId, addressId, 4000);
        jdbc.execute("ANALYZE TABLE orders");
    }

    @AfterEach
    void cleanUp() {
        if (jdbc == null || storeId == 0) return;
        jdbc.update("DELETE FROM orders WHERE restaurant_id IN (?, ?)", storeId, otherStoreId);
        jdbc.update("DELETE FROM addresses WHERE user_id = ?", customerId);
        jdbc.update("DELETE FROM users WHERE id = ?", customerId);
        jdbc.update("DELETE FROM zones WHERE id = ?", zoneId);
        jdbc.update("DELETE FROM restaurants WHERE id IN (?, ?)", storeId, otherStoreId);
    }

    @Test
    void listaDeEncerradosDaLojaUsaOIndiceDaLojaEmVezDeVarrerATabela() {
        Map<String, Object> plan = ordersPlan(OrderService.terminalListSql("o.restaurant_id = ?"), storeId);
        assertEquals("ix_orders_restaurant_recent", plan.get("key"), "plano: " + plan);
        long rows = Long.parseLong(String.valueOf(plan.get("rows")));
        assertTrue(rows <= 1000, "o banco estima ler " + rows + " linhas; com o índice são só as da loja");
    }

    @Test
    void aListaContinuaTrazendoOsUltimosEncerradosDaLoja() {
        List<Map<String, Object>> rows = jdbc.queryForList(OrderService.terminalListSql("o.restaurant_id = ?"), storeId);
        assertEquals(100, rows.size());
        long previous = Long.MAX_VALUE;
        for (Map<String, Object> row : rows) {
            assertEquals(storeId, ((Number) row.get("restaurant_id")).longValue());
            long id = ((Number) row.get("id")).longValue();
            assertTrue(id < previous, "do mais novo para o mais antigo");
            previous = id;
        }
    }

    private Map<String, Object> ordersPlan(String sql, Object... args) {
        return jdbc.queryForList("EXPLAIN " + sql, args).stream()
            .filter(row -> "o".equals(row.get("table")))
            .findFirst().orElseThrow();
    }

    private void bulkOrders(long restaurantId, long addressId, int count) {
        jdbc.update("INSERT INTO orders (customer_id, restaurant_id, status, total_cents, zone_id, address_id, delivery_address_text) "
            + "SELECT ?, ?, 'delivered', 3000, ?, ?, 'Rua A, 1' FROM seq_1_to_" + count, customerId, restaurantId, zoneId, addressId);
    }

    private long insert(String sql, Object... args) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
}
