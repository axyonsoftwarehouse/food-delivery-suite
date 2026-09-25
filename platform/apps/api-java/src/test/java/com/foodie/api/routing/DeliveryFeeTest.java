package com.foodie.api.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DeliveryFeeTest {
    @Test
    void keepsFixedFeeWithoutDistanceRuleOrDistance() {
        assertEquals(599, DeliveryFee.compute(599, null, null, 5000L));
        assertEquals(599, DeliveryFee.compute(599, null, 100, null));
        assertEquals(599, DeliveryFee.compute(599, 300, 0, 5000L));
    }

    @Test
    void appliesBasePlusPerKilometerRoundingUp() {
        assertEquals(800, DeliveryFee.compute(599, 300, 100, 5000L));
        assertEquals(900, DeliveryFee.compute(599, 300, 100, 5001L));
        assertEquals(799, DeliveryFee.compute(599, null, 100, 1500L));
    }
}
