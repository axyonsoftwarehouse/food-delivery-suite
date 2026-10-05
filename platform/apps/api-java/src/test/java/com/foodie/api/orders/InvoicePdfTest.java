package com.foodie.api.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InvoicePdfTest {
    private Map<String, Object> order() {
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("id", 42L);
        order.put("restaurant_name", "Cozinha Demo");
        order.put("created_at", Timestamp.valueOf(LocalDateTime.of(2026, 9, 27, 10, 0)));
        order.put("status", "delivered");
        order.put("order_type", "delivery");
        order.put("delivery_address_text", "Rua 1, 100");
        order.put("subtotal_cents", 10000L);
        order.put("delivery_fee_cents", 500L);
        order.put("service_fee_cents", 300L);
        order.put("tip_cents", 200L);
        order.put("discount_cents", 1000L);
        order.put("total_cents", 10000L);
        order.put("items", List.of(Map.of(
            "name", "Prato", "variation_name", "Grande", "addons", "Queijo, Bacon",
            "quantity", 2, "unit_price_cents", 5000L)));
        return order;
    }

    @Test
    void generatesAPdfWithTheOrderData() {
        byte[] pdf = InvoicePdf.build(order());
        assertTrue(pdf.length > 500);
        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII));
        String tail = new String(pdf, Math.max(0, pdf.length - 64), Math.min(64, pdf.length), StandardCharsets.US_ASCII);
        assertTrue(tail.contains("%%EOF"));
    }

    @Test
    void toleratesMissingOptionalFieldsAndNoItems() {
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("id", 1L);
        order.put("status", "placed");
        order.put("items", List.of());
        byte[] pdf = InvoicePdf.build(order);
        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII));
    }
}
