package com.foodie.api.courier;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class CourierShiftRunnerTest {
    @Test
    void closesStaleShiftsAndSurvivesAFailure() {
        CourierShiftService shifts = mock(CourierShiftService.class);
        when(shifts.closeStale()).thenThrow(new IllegalStateException("banco fora"));
        new CourierShiftRunner(shifts).run();
        verify(shifts).closeStale();
    }
}
