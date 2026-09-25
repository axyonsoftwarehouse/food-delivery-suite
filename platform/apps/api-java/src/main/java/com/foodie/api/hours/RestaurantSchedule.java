package com.foodie.api.hours;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public final class RestaurantSchedule {
    private RestaurantSchedule() {}

    public record Interval(int dayOfWeek, LocalTime opensAt, LocalTime closesAt) {
        public boolean overnight() {
            return closesAt.isBefore(opensAt);
        }
    }

    public static int dayOfWeek(LocalDateTime now) {
        return now.getDayOfWeek().getValue() % 7;
    }

    public static boolean isOpen(List<Interval> intervals, int dayOfWeek, LocalTime time) {
        if (intervals.isEmpty()) return true;
        int previousDay = Math.floorMod(dayOfWeek - 1, 7);
        for (Interval interval : intervals) {
            if (interval.dayOfWeek() == dayOfWeek) {
                boolean started = !time.isBefore(interval.opensAt());
                if (interval.overnight() ? started : started && time.isBefore(interval.closesAt())) return true;
            } else if (interval.dayOfWeek() == previousDay && interval.overnight() && time.isBefore(interval.closesAt())) {
                return true;
            }
        }
        return false;
    }
}
