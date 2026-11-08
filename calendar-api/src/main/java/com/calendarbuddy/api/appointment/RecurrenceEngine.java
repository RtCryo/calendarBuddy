package com.calendarbuddy.api.appointment;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Expands recurrence rules period by period (day, week, month, or year), so the cost of a query
 * depends on the requested range and, for count-limited series, on the bounded count, never on the
 * age of an open-ended series.
 */
@Component
public class RecurrenceEngine {
    public List<LocalDate> expand(
            AppointmentRecord series, LocalDate from, LocalDate to, LocalDate start, int spanDays) {
        if (series.recurrenceFreq() == null) return List.of();
        String freq = series.recurrenceFreq();
        int interval = series.recurrenceInterval();
        Integer count = series.recurrenceCount();
        LocalDate until = series.recurrenceUntil();
        LocalDate earliest = from.minusDays(spanDays);
        List<DayOfWeek> days = "WEEKLY".equals(freq) ? weekdays(series) : List.of();
        List<LocalDate> found = new ArrayList<>();
        // Open-ended series jump straight to the period containing the earliest relevant date. A
        // count needs the
        // occurrences before the range, but it is capped at 730, so counting from the start stays
        // bounded.
        long period = count == null ? firstPeriod(freq, start, earliest, interval) : 0;
        int generated = 0;
        for (; ; period++) {
            LocalDate periodStart = periodStart(freq, start, period * interval);
            if (!periodStart.isBefore(to) || (until != null && periodStart.isAfter(until)))
                return found;
            for (LocalDate day : candidates(freq, start, periodStart, days)) {
                if (day.isBefore(start)) continue;
                if (!day.isBefore(to) || (until != null && day.isAfter(until))) return found;
                if (count != null && ++generated > count) return found;
                if (!day.isBefore(earliest)) found.add(day);
            }
        }
    }

    public boolean occursOn(AppointmentRecord series, LocalDate start, LocalDate date) {
        return expand(series, date, date.plusDays(1), start, 0).contains(date);
    }

    private long firstPeriod(String freq, LocalDate start, LocalDate earliest, int interval) {
        if (!earliest.isAfter(start)) return 0;
        long elapsed =
                switch (freq) {
                    case "DAILY" -> ChronoUnit.DAYS.between(start, earliest);
                    case "WEEKLY" ->
                            ChronoUnit.WEEKS.between(
                                    start.with(DayOfWeek.MONDAY), earliest.with(DayOfWeek.MONDAY));
                    case "MONTHLY" ->
                            ChronoUnit.MONTHS.between(
                                    YearMonth.from(start), YearMonth.from(earliest));
                    case "YEARLY" -> earliest.getYear() - start.getYear();
                    default -> 0;
                };
        return elapsed / interval;
    }

    private LocalDate periodStart(String freq, LocalDate start, long offset) {
        return switch (freq) {
            case "DAILY" -> start.plusDays(offset);
            case "WEEKLY" -> start.with(DayOfWeek.MONDAY).plusWeeks(offset);
            case "MONTHLY" -> start.withDayOfMonth(1).plusMonths(offset);
            case "YEARLY" -> LocalDate.of(start.getYear(), 1, 1).plusYears(offset);
            default -> throw new IllegalArgumentException("Unknown frequency " + freq);
        };
    }

    /**
     * Occurrence dates inside one period in ascending order; months or years without the start day
     * yield none.
     */
    private List<LocalDate> candidates(
            String freq, LocalDate start, LocalDate periodStart, List<DayOfWeek> days) {
        return switch (freq) {
            case "DAILY" -> List.of(periodStart);
            case "WEEKLY" -> days.stream().map(day -> periodStart.plusDays(day.ordinal())).toList();
            case "MONTHLY" -> {
                YearMonth month = YearMonth.from(periodStart);
                yield month.isValidDay(start.getDayOfMonth())
                        ? List.of(month.atDay(start.getDayOfMonth()))
                        : List.of();
            }
            case "YEARLY" -> {
                MonthDay day = MonthDay.from(start);
                yield day.isValidYear(periodStart.getYear())
                        ? List.of(day.atYear(periodStart.getYear()))
                        : List.of();
            }
            default -> List.of();
        };
    }

    private List<DayOfWeek> weekdays(AppointmentRecord series) {
        EnumSet<DayOfWeek> result = EnumSet.noneOf(DayOfWeek.class);
        for (String day : series.recurrenceWeekdays().split(",")) {
            result.add(
                    switch (day) {
                        case "MON" -> DayOfWeek.MONDAY;
                        case "TUE" -> DayOfWeek.TUESDAY;
                        case "WED" -> DayOfWeek.WEDNESDAY;
                        case "THU" -> DayOfWeek.THURSDAY;
                        case "FRI" -> DayOfWeek.FRIDAY;
                        case "SAT" -> DayOfWeek.SATURDAY;
                        default -> DayOfWeek.SUNDAY;
                    });
        }
        return List.copyOf(result);
    }
}
