package com.calendarbuddy.api.appointment;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RecurrenceEngine {
    public List<LocalDate> expand(AppointmentRecord series, LocalDate from, LocalDate to,
                                  LocalDate start, int spanDays) {
        if (series.recurrenceFreq() == null) return List.of();
        int interval = series.recurrenceInterval();
        int limit = series.recurrenceCount() == null ? Integer.MAX_VALUE : series.recurrenceCount();
        LocalDate until = series.recurrenceUntil();
        List<LocalDate> found = new ArrayList<>();
        LocalDate earliest = from.minusDays(spanDays);
        int generated = 0;
        // A bounded query never iterates more than the dates needed to reach its end.
        for (LocalDate day = start; day.isBefore(to); day = day.plusDays(1)) {
            if (until != null && day.isAfter(until)) break;
            if (!matches(series, start, day, interval)) continue;
            if (++generated > limit) break;
            if (!day.isBefore(earliest)) found.add(day);
        }
        return found;
    }

    public boolean occursOn(AppointmentRecord series, LocalDate start, LocalDate date) {
        return !date.isBefore(start) && (series.recurrenceUntil() == null || !date.isAfter(series.recurrenceUntil()))
                && expand(series, date, date.plusDays(1), start, 0).contains(date);
    }

    private boolean matches(AppointmentRecord series, LocalDate start, LocalDate date, int interval) {
        if (date.isBefore(start)) return false;
        return switch (series.recurrenceFreq()) {
            case "DAILY" -> java.time.temporal.ChronoUnit.DAYS.between(start, date) % interval == 0;
            case "WEEKLY" -> {
                long weeks = java.time.temporal.ChronoUnit.WEEKS.between(
                        start.with(DayOfWeek.MONDAY), date.with(DayOfWeek.MONDAY));
                yield weeks % interval == 0 && weekdays(series).contains(date.getDayOfWeek());
            }
            case "MONTHLY" -> {
                long months = java.time.temporal.ChronoUnit.MONTHS.between(
                        YearMonth.from(start), YearMonth.from(date));
                yield months % interval == 0 && date.getDayOfMonth() == start.getDayOfMonth();
            }
            case "YEARLY" -> (date.getYear() - start.getYear()) % interval == 0
                    && date.getMonth() == start.getMonth() && date.getDayOfMonth() == start.getDayOfMonth();
            default -> false;
        };
    }

    private EnumSet<DayOfWeek> weekdays(AppointmentRecord series) {
        EnumSet<DayOfWeek> result = EnumSet.noneOf(DayOfWeek.class);
        for (String day : series.recurrenceWeekdays().split(",")) {
            result.add(DayOfWeek.valueOf(switch (day) {
                case "MON" -> "MONDAY"; case "TUE" -> "TUESDAY"; case "WED" -> "WEDNESDAY";
                case "THU" -> "THURSDAY"; case "FRI" -> "FRIDAY"; case "SAT" -> "SATURDAY";
                default -> "SUNDAY";
            }));
        }
        return result;
    }
}
