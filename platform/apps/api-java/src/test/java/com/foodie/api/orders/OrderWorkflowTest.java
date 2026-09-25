package com.foodie.api.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.foodie.api.ApiException;
import org.junit.jupiter.api.Test;

class OrderWorkflowTest {
    @Test
    void followsAllRolesInOrder() {
        String status = OrderWorkflow.nextStatus("placed", "accept", "restaurant");
        status = OrderWorkflow.nextStatus(status, "ready", "restaurant");
        status = OrderWorkflow.nextStatus(status, "assign", "admin");
        status = OrderWorkflow.nextStatus(status, "pickup", "courier");
        assertEquals("delivered", OrderWorkflow.nextStatus(status, "deliver", "courier"));
    }

    @Test
    void rejectsSkippedStepAndWrongRole() {
        assertEquals(409, assertThrows(ApiException.class, () -> OrderWorkflow.nextStatus("placed", "deliver", "courier")).status());
        assertEquals(409, assertThrows(ApiException.class, () -> OrderWorkflow.nextStatus("ready", "assign", "customer")).status());
    }

    @Test
    void respectsMinimumAndCapsTotal() {
        assertEquals(3099, OrderWorkflow.total(2500, 599, 1500));
        assertEquals(400, assertThrows(ApiException.class, () -> OrderWorkflow.total(1000, 599, 1500)).status());
        assertEquals(400, assertThrows(ApiException.class, () -> OrderWorkflow.total(100_000_000, 1, 0)).status());
    }
}
