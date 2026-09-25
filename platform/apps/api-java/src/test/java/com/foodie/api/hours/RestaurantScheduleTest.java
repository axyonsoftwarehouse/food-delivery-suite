package com.foodie.api.hours;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.foodie.api.hours.RestaurantSchedule.Interval;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class RestaurantScheduleTest {
    @Test
    void openWhenNoIntervalsAreConfigured() {
        assertTrue(RestaurantSchedule.isOpen(List.of(), 1, LocalTime.of(3, 0)));
    }

    @Test
    void respectsSameDayInterval() {
        List<Interval> intervals = List.of(new Interval(1, LocalTime.of(8, 0), LocalTime.of(18, 0)));
        assertFalse(RestaurantSchedule.isOpen(intervals, 1, LocalTime.of(7, 59)));
        assertTrue(RestaurantSchedule.isOpen(intervals, 1, LocalTime.of(8, 0)));
        assertTrue(RestaurantSchedule.isOpen(intervals, 1, LocalTime.of(17, 59)));
        assertFalse(RestaurantSchedule.isOpen(intervals, 1, LocalTime.of(18, 0)));
        assertFalse(RestaurantSchedule.isOpen(intervals, 2, LocalTime.of(12, 0)));
    }

    @Test
    void keepsOvernightIntervalOpenPastMidnight() {
        List<Interval> intervals = List.of(new Interval(5, LocalTime.of(18, 0), LocalTime.of(2, 0)));
        assertFalse(RestaurantSchedule.isOpen(intervals, 5, LocalTime.of(17, 0)));
        assertTrue(RestaurantSchedule.isOpen(intervals, 5, LocalTime.of(23, 0)));
        assertTrue(RestaurantSchedule.isOpen(intervals, 6, LocalTime.of(1, 59)));
        assertFalse(RestaurantSchedule.isOpen(intervals, 6, LocalTime.of(2, 0)));
        assertFalse(RestaurantSchedule.isOpen(intervals, 6, LocalTime.of(12, 0)));
    }

    @Test
    void combinesIntervalsAcrossTheWeek() {
        List<Interval> intervals = List.of(
            new Interval(1, LocalTime.of(11, 0), LocalTime.of(15, 0)),
            new Interval(1, LocalTime.of(18, 0), LocalTime.of(23, 0))
        );
        assertTrue(RestaurantSchedule.isOpen(intervals, 1, LocalTime.of(12, 0)));
        assertFalse(RestaurantSchedule.isOpen(intervals, 1, LocalTime.of(16, 0)));
        assertTrue(RestaurantSchedule.isOpen(intervals, 1, LocalTime.of(19, 0)));
        assertFalse(RestaurantSchedule.isOpen(intervals, 1, LocalTime.of(23, 0)));
    }
}
