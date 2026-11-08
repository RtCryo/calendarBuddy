package com.calendarbuddy.api.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Business metrics for the central homeBuddy monitoring. Labels come from fixed sets ({@code
 * operation} and {@code outcome}), and the stored totals count rows across all accounts, so the
 * series count never depends on accounts, members, or appointments.
 */
@Component
public class CalendarMetrics {
    public enum Operation {
        CREATE,
        UPDATE,
        DELETE
    }

    public enum Outcome {
        SUCCESS,
        CONFLICT,
        INVALID
    }

    private static final Duration TOTALS_CACHE = Duration.ofSeconds(15);

    private final Map<Operation, Map<Outcome, Counter>> appointmentOperations;
    private final Map<Operation, Map<Outcome, Counter>> memberOperations;
    private final Timer recurrenceExpansion;
    private final CachedCount appointments;
    private final CachedCount members;

    public CalendarMetrics(MeterRegistry registry, JdbcTemplate jdbc) {
        appointmentOperations =
                counters(
                        registry,
                        "calendarbuddy.appointment.operations",
                        "Appointment operations by operation and outcome");
        memberOperations =
                counters(
                        registry,
                        "calendarbuddy.family.member.operations",
                        "Family member operations by operation and outcome");
        recurrenceExpansion =
                Timer.builder("calendarbuddy.recurrence.expansion")
                        .description(
                                "Time spent expanding recurring appointments for one requested range")
                        .register(registry);
        // Detached occurrences are rows of their series, so only series heads and one-offs count as
        // appointments.
        appointments =
                new CachedCount(jdbc, "SELECT COUNT(*) FROM appointments WHERE series_id IS NULL");
        members = new CachedCount(jdbc, "SELECT COUNT(*) FROM family_members");
        Gauge.builder("calendarbuddy.appointments", appointments, CachedCount::get)
                .description("Stored appointments across all accounts")
                .register(registry);
        Gauge.builder("calendarbuddy.family.members", members, CachedCount::get)
                .description("Stored family members across all accounts")
                .register(registry);
    }

    private static Map<Operation, Map<Outcome, Counter>> counters(
            MeterRegistry registry, String name, String description) {
        Map<Operation, Map<Outcome, Counter>> result = new EnumMap<>(Operation.class);
        for (Operation operation : Operation.values()) {
            Map<Outcome, Counter> byOutcome = new EnumMap<>(Outcome.class);
            for (Outcome outcome : Outcome.values()) {
                byOutcome.put(
                        outcome,
                        Counter.builder(name)
                                .description(description)
                                .tag("operation", operation.name().toLowerCase())
                                .tag("outcome", outcome.name().toLowerCase())
                                .register(registry));
            }
            result.put(operation, byOutcome);
        }
        return result;
    }

    public <T> T appointment(Operation operation, Supplier<T> action) {
        return record(appointmentOperations.get(operation), action);
    }

    public <T> T member(Operation operation, Supplier<T> action) {
        return record(memberOperations.get(operation), action);
    }

    public void recurrenceExpansion(long nanos) {
        recurrenceExpansion.record(Duration.ofNanos(nanos));
    }

    private static <T> T record(Map<Outcome, Counter> counters, Supplier<T> action) {
        try {
            T result = action.get();
            counters.get(Outcome.SUCCESS).increment();
            return result;
        } catch (ResponseStatusException error) {
            boolean conflict = error.getStatusCode().value() == HttpStatus.CONFLICT.value();
            counters.get(conflict ? Outcome.CONFLICT : Outcome.INVALID).increment();
            throw error;
        }
    }

    /**
     * A row count that is read at most once per cache period, so scraping cannot load the database.
     */
    private static final class CachedCount {
        private final JdbcTemplate jdbc;
        private final String sql;
        private long expiresAtNanos;
        private double value = Double.NaN;

        CachedCount(JdbcTemplate jdbc, String sql) {
            this.jdbc = jdbc;
            this.sql = sql;
        }

        synchronized double get() {
            long now = System.nanoTime();
            if (Double.isNaN(value) || now - expiresAtNanos >= 0) {
                try {
                    Long count = jdbc.queryForObject(sql, Long.class);
                    value = count == null ? Double.NaN : count;
                } catch (RuntimeException unavailable) {
                    value = Double.NaN;
                }
                expiresAtNanos = now + TOTALS_CACHE.toNanos();
            }
            return value;
        }
    }
}
