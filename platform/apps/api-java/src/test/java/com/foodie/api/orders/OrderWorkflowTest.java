package com.foodie.api.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.foodie.api.ApiException;
import org.junit.jupiter.api.Test;

class OrderWorkflowTest {
    private static String next(String current, String action, String role) {
        return OrderWorkflow.resolve(current, action, role).nextStatus();
    }

    @Test
    void followsAllRolesInOrder() {
        String status = next("placed", "accept", "restaurant");
        status = next(status, "ready", "restaurant");
        status = next(status, "assign", "admin");
        status = next(status, "pickup", "courier");
        assertEquals("delivered", next(status, "deliver", "courier"));
    }

    @Test
    void rejectsSkippedStepAndWrongRole() {
        assertEquals(409, assertThrows(ApiException.class, () -> OrderWorkflow.resolve("placed", "deliver", "courier")).status());
        assertEquals(409, assertThrows(ApiException.class, () -> OrderWorkflow.resolve("ready", "assign", "customer")).status());
    }

    @Test
    void restaurantRejectsAndCustomerCancelsBeforeAccept() {
        assertEquals("rejected", next("placed", "reject", "restaurant"));
        assertEquals(409, assertThrows(ApiException.class, () -> OrderWorkflow.resolve("accepted", "reject", "restaurant")).status());
        assertEquals("cancelled", next("placed", "cancel", "customer"));
        assertEquals(409, assertThrows(ApiException.class, () -> OrderWorkflow.resolve("accepted", "cancel", "customer")).status());
    }

    @Test
    void adminCancelsAnyActiveOrderAndReassignsCourier() {
        assertEquals("cancelled", next("picked_up", "cancel", "admin"));
        assertEquals(409, assertThrows(ApiException.class, () -> OrderWorkflow.resolve("delivered", "cancel", "admin")).status());
        assertEquals("assigned", next("assigned", "assign", "admin"));
        assertTrue(OrderWorkflow.resolve("assigned", "unassign", "admin").clearsCourier());
        assertEquals("ready", next("assigned", "unassign", "admin"));
    }

    @Test
    void exceptionActionsRequireReason() {
        assertTrue(OrderWorkflow.resolve("placed", "reject", "restaurant").requiresReason());
        assertTrue(OrderWorkflow.resolve("placed", "cancel", "customer").requiresReason());
        assertTrue(OrderWorkflow.resolve("picked_up", "fail", "courier").requiresReason());
        assertEquals("failed", next("assigned", "fail", "courier"));
        assertFalse(OrderWorkflow.resolve("placed", "accept", "restaurant").requiresReason());
    }

    @Test
    void kitchenRunsTheSameRestaurantTransitions() {
        assertEquals("accepted", next("placed", "accept", "kitchen"));
        assertEquals("ready", next("accepted", "ready", "kitchen"));
        assertEquals("rejected", next("placed", "reject", "kitchen"));
        assertTrue(OrderWorkflow.resolve("placed", "reject", "kitchen").requiresReason());
    }

    @Test
    void kitchenCannotAssignPickupDeliverOrCancel() {
        assertEquals(409, assertThrows(ApiException.class, () -> OrderWorkflow.resolve("ready", "assign", "kitchen")).status());
        assertEquals(409, assertThrows(ApiException.class, () -> OrderWorkflow.resolve("assigned", "pickup", "kitchen")).status());
        assertEquals(409, assertThrows(ApiException.class, () -> OrderWorkflow.resolve("picked_up", "deliver", "kitchen")).status());
        assertEquals(409, assertThrows(ApiException.class, () -> OrderWorkflow.resolve("ready", "cancel", "kitchen")).status());
        assertEquals(409, assertThrows(ApiException.class, () -> OrderWorkflow.resolve("accepted", "reject", "kitchen")).status());
    }

    @Test
    void restaurantServesAndCompletesLocalOrders() {
        assertEquals("served", next("ready", "serve", "restaurant"));
        assertEquals("completed", next("served", "complete", "restaurant"));
        assertEquals("completed", next("ready", "complete", "restaurant"));
        assertEquals(409, assertThrows(ApiException.class, () -> OrderWorkflow.resolve("accepted", "serve", "restaurant")).status());
    }

    @Test
    void respectsMinimumAndCapsTotal() {
        assertEquals(3099, OrderWorkflow.total(2500, 599, 1500));
        assertEquals(400, assertThrows(ApiException.class, () -> OrderWorkflow.total(1000, 599, 1500)).status());
        assertEquals(400, assertThrows(ApiException.class, () -> OrderWorkflow.total(100_000_000, 1, 0)).status());
    }
}
