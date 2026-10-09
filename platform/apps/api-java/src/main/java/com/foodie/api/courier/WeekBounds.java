package com.foodie.api.courier;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/** Semana de segunda a domingo no fuso da loja: `from` inclusivo, `to` exclusivo. */
public record WeekBounds(LocalDate weekStart, LocalDate weekEnd, Instant from, Instant to) {
    public static WeekBounds of(Instant now, ZoneId zone) {
        LocalDate monday = now.atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new WeekBounds(monday, monday.plusDays(6), monday.atStartOfDay(zone).toInstant(), monday.plusDays(7).atStartOfDay(zone).toInstant());
    }
}
