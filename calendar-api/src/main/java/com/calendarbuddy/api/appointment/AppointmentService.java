package com.calendarbuddy.api.appointment;

import com.calendarbuddy.api.member.FamilyMemberRepository;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AppointmentService {
    private final AppointmentRepository appointments;
    private final FamilyMemberRepository members;
    private final RecurrenceEngine recurrence;
    private final JdbcTemplate jdbc;

    public AppointmentService(AppointmentRepository appointments, FamilyMemberRepository members,
                              RecurrenceEngine recurrence, JdbcTemplate jdbc) {
        this.appointments = appointments;
        this.members = members;
        this.recurrence = recurrence;
        this.jdbc = jdbc;
    }

    /** Longest span of one appointment in days, which bounds how far before a range an occurrence can start. */
    private static final int MAX_SPAN_DAYS = 31;

    private record Candidate(AppointmentRecord row, LocalDate date, Instant start, Instant end,
                             LocalDate startDate, LocalDate endDate, long version) {}

    /**
     * Expands the range with a fixed number of queries: one-offs near the range, the account's series, their
     * detached occurrences and skipped dates near the range, and the participants of every candidate.
     */
    @Transactional(readOnly = true)
    public List<AppointmentResponse> range(UUID accountId, ZoneId zone, LocalDate from, LocalDate to, Long memberId) {
        if (from == null || to == null || !to.isAfter(from) || ChronoUnit.DAYS.between(from, to) > 100)
            bad("Range must contain 1 to 100 days");
        if (memberId != null && !members.existsByIdAndAccountId(memberId, accountId)) notFound();
        Instant lower = from.atStartOfDay(zone).toInstant();
        Instant upper = to.atStartOfDay(zone).toInstant();
        LocalDate windowStart = from.minusDays(MAX_SPAN_DAYS);
        List<Candidate> candidates = new ArrayList<>();
        for (AppointmentRecord row : appointments.oneOffsNear(accountId, lower, upper, from, to)) {
            Candidate item = new Candidate(row, localStart(row, zone), row.startAt(), row.endAt(),
                    row.startDate(), row.endDate(), row.version());
            if (overlaps(item, from, to, lower, upper)) candidates.add(item);
        }
        Map<Long, Map<LocalDate, AppointmentRecord>> exceptions = new HashMap<>();
        for (AppointmentRecord row : appointments.exceptionsNear(accountId, windowStart, to, lower, upper, from))
            exceptions.computeIfAbsent(row.seriesId(), key -> new HashMap<>()).put(row.originalDate(), row);
        Map<Long, Set<LocalDate>> exdates = appointments.exdates(accountId, windowStart, to);
        for (AppointmentRecord row : appointments.series(accountId)) {
            LocalDate start = localStart(row, zone);
            int span = spanDays(row, zone);
            Map<LocalDate, AppointmentRecord> own = exceptions.getOrDefault(row.id(), Map.of());
            Set<LocalDate> skipped = exdates.getOrDefault(row.id(), Set.of());
            for (LocalDate date : recurrence.expand(row, from, to, start, span)) {
                if (skipped.contains(date)) continue;
                AppointmentRecord exception = own.get(date);
                Candidate item = exception != null ? detached(exception, date, row.version())
                        : occurrence(row, date, zone);
                if (overlaps(item, from, to, lower, upper)) candidates.add(item);
            }
            // A moved exception can overlap this range even when its original date does not.
            for (AppointmentRecord exception : own.values()) {
                if (exception.originalDate().isBefore(from.minusDays(span))
                        || !exception.originalDate().isBefore(to)) {
                    Candidate item = detached(exception, exception.originalDate(), row.version());
                    if (overlaps(item, from, to, lower, upper)) candidates.add(item);
                }
            }
        }
        Map<Long, List<Long>> participants = appointments.participantIds(accountId,
                candidates.stream().map(item -> item.row().id()).collect(Collectors.toSet()));
        List<AppointmentResponse> result = new ArrayList<>();
        for (Candidate item : candidates) {
            List<Long> ids = participants.getOrDefault(item.row().id(), List.of());
            if (memberId == null || ids.contains(memberId)) result.add(response(item, ids));
        }
        result.sort(Comparator.comparing((AppointmentResponse item) -> item.allDay()
                ? item.startDate().atStartOfDay(zone).toInstant() : item.startAt())
                .thenComparing(AppointmentResponse::title));
        return result;
    }

    @Transactional(readOnly = true)
    public AppointmentResponse get(UUID accountId, ZoneId zone, long id) {
        AppointmentRecord row = require(accountId, id);
        return single(row, localStart(row, zone));
    }

    @Transactional
    public AppointmentResponse create(UUID accountId, ZoneId zone, AppointmentRequest request) {
        AppointmentRecord input = validated(accountId, zone, 0, request, null, null);
        AppointmentRecord saved = appointments.insert(input);
        appointments.replaceParticipants(accountId, saved.id(), sorted(request.participantIds()));
        return get(accountId, zone, saved.id());
    }

    @Transactional
    public AppointmentResponse update(UUID accountId, ZoneId zone, long id, AppointmentRequest request,
                                      String scope, LocalDate occurrenceDate) {
        AppointmentRecord current = require(accountId, id);
        if (request.version() == null) bad("Version is required");
        AppointmentRecord series = current.seriesId() == null ? current : require(accountId, current.seriesId());
        if (series.recurrenceFreq() != null && "this".equals(scope))
            return updateOccurrence(accountId, zone, series, current, occurrenceDate, request);
        if (series.recurrenceFreq() != null && !"all".equals(scope)) bad("Choose this or all occurrences");
        if (current.seriesId() != null && !"all".equals(scope)) bad("Choose this or all occurrences");
        if (current.seriesId() != null) current = series;
        if (current.version() != request.version()) conflict();
        AppointmentRecord input = validated(accountId, zone, current.id(), request, null, null);
        boolean scheduleChanged = scheduleChanged(current, input);
        AppointmentRecord saved = appointments.update(input, request.version()).orElseThrow(() -> conflictException());
        appointments.replaceParticipants(accountId, saved.id(), sorted(request.participantIds()));
        if (scheduleChanged && current.recurrenceFreq() != null) {
            jdbc.update("DELETE FROM appointments WHERE account_id=? AND series_id=?", accountId, saved.id());
            jdbc.update("DELETE FROM appointment_exdates WHERE account_id=? AND series_id=?", accountId, saved.id());
        }
        return get(accountId, zone, saved.id());
    }

    @Transactional
    public void delete(UUID accountId, ZoneId zone, long id, long version, String scope, LocalDate occurrenceDate) {
        AppointmentRecord current = require(accountId, id);
        AppointmentRecord series = current.seriesId() == null ? current : require(accountId, current.seriesId());
        if (series.recurrenceFreq() != null && "this".equals(scope)) {
            LocalDate date = occurrenceDate == null ? current.originalDate() : occurrenceDate;
            checkOccurrence(series, zone, date, version);
            jdbc.update("DELETE FROM appointments WHERE account_id=? AND series_id=? AND original_date=?",
                    accountId, series.id(), date);
            jdbc.update("INSERT INTO appointment_exdates(series_id,account_id,occurrence_date) VALUES (?,?,?)"
                    + " ON CONFLICT DO NOTHING", series.id(), accountId, date);
            bump(series);
            return;
        }
        if (series.recurrenceFreq() != null && !"all".equals(scope)) bad("Choose this or all occurrences");
        if (series.version() != version) conflict();
        if (!appointments.delete(accountId, series.id(), version)) conflict();
    }

    private AppointmentResponse updateOccurrence(UUID accountId, ZoneId zone, AppointmentRecord series,
                                                  AppointmentRecord current, LocalDate date,
                                                  AppointmentRequest request) {
        if (date == null) date = current.originalDate();
        checkOccurrence(series, zone, date, request.version());
        AppointmentRecord input = validated(accountId, zone, 0, request, series.id(), date);
        AppointmentRecord existing = appointments.exception(accountId, series.id(), date).orElse(null);
        AppointmentRecord saved;
        if (existing == null) saved = appointments.insert(input);
        else {
            input = withId(input, existing.id());
            saved = appointments.update(input, existing.version()).orElseThrow(() -> conflictException());
        }
        appointments.replaceParticipants(accountId, saved.id(), sorted(request.participantIds()));
        jdbc.update("DELETE FROM appointment_exdates WHERE account_id=? AND series_id=? AND occurrence_date=?",
                accountId, series.id(), date);
        bump(series);
        return single(saved, date);
    }

    private void checkOccurrence(AppointmentRecord series, ZoneId zone, LocalDate date, long version) {
        if (series.version() != version) conflict();
        if (date == null || !recurrence.occursOn(series, localStart(series, zone), date)) notFound();
    }

    private void bump(AppointmentRecord series) {
        int changed = jdbc.update("UPDATE appointments SET version=version+1,updated_at=now()"
                + " WHERE id=? AND account_id=? AND version=?", series.id(), series.accountId(), series.version());
        if (changed != 1) conflict();
    }

    private AppointmentRecord validated(UUID accountId, ZoneId zone, long id, AppointmentRequest request,
                                         Long seriesId, LocalDate originalDate) {
        if (request == null) bad("Appointment is required");
        String title = request.title() == null ? "" : request.title().trim();
        if (title.isEmpty() || title.length() > 200) bad("title: 1 to 200 characters required");
        if (request.location() != null && request.location().length() > 200) bad("location: at most 200 characters");
        if (request.notes() != null && request.notes().length() > 2000) bad("notes: at most 2000 characters");
        if (request.participantIds() == null || request.participantIds().isEmpty()) bad("participantIds: at least one required");
        for (Long memberId : request.participantIds()) {
            if (memberId == null || !members.existsByIdAndAccountId(memberId, accountId)) notFound();
        }
        Instant startAt = null, endAt = null;
        LocalDate startDate = null, endDate = null;
        if (request.allDay()) {
            startDate = request.startDate(); endDate = request.endDate();
            if (startDate == null || endDate == null || endDate.isBefore(startDate)
                    || ChronoUnit.DAYS.between(startDate, endDate) > 30) bad("endDate: invalid all-day range");
            if (request.startAt() != null || request.endAt() != null) bad("Timed fields must be empty for all-day events");
        } else {
            if (request.startAt() == null || request.endAt() == null) bad("startAt and endAt are required");
            startAt = request.startAt().toInstant(); endAt = request.endAt().toInstant();
            if (endAt.isBefore(startAt) || Duration.between(startAt, endAt).compareTo(Duration.ofDays(31)) > 0)
                bad("endAt: invalid timed range");
            if (request.startDate() != null || request.endDate() != null) bad("All-day fields must be empty for timed events");
        }
        RecurrenceRequest rule = seriesId == null ? request.recurrence() : null;
        String freq = null, weekdays = null;
        Integer interval = null, count = null;
        LocalDate until = null;
        if (rule != null) {
            freq = rule.frequency() == null ? "" : rule.frequency().toUpperCase(Locale.ROOT);
            if (!Set.of("DAILY", "WEEKLY", "MONTHLY", "YEARLY").contains(freq)) bad("recurrence.frequency: invalid");
            interval = rule.interval() == null ? 1 : rule.interval();
            if (interval < 1 || interval > 99) bad("recurrence.interval: 1 to 99 required");
            until = rule.until(); count = rule.count();
            if (until != null && count != null) bad("recurrence: choose one end");
            if (count != null && (count < 1 || count > 730)) bad("recurrence.count: 1 to 730 required");
            LocalDate first = request.allDay() ? startDate : startAt.atZone(zone).toLocalDate();
            if (until != null && until.isBefore(first)) bad("recurrence.until: before start");
            if ("WEEKLY".equals(freq)) {
                Set<DayOfWeek> days = rule.weekdays() == null || rule.weekdays().isEmpty()
                        ? Set.of(first.getDayOfWeek()) : rule.weekdays();
                if (!days.contains(first.getDayOfWeek())) bad("recurrence.weekdays: must include start day");
                weekdays = days.stream().sorted().map(day -> day.name().substring(0, 3))
                        .collect(java.util.stream.Collectors.joining(","));
            } else if (rule.weekdays() != null && !rule.weekdays().isEmpty()) bad("recurrence.weekdays: weekly only");
        }
        return new AppointmentRecord(id, accountId, title, request.location(), request.notes(),
                request.allDay(), startAt, endAt, startDate, endDate,
                freq, interval, weekdays, until, count, seriesId, originalDate, 0, null, null);
    }

    private Candidate occurrence(AppointmentRecord row, LocalDate date, ZoneId zone) {
        if (row.allDay()) {
            long span = ChronoUnit.DAYS.between(row.startDate(), row.endDate());
            return new Candidate(row, date, null, null, date, date.plusDays(span), row.version());
        }
        LocalDateTime localStart = row.startAt().atZone(zone).toLocalDateTime();
        LocalDateTime localEnd = row.endAt().atZone(zone).toLocalDateTime();
        Instant start = date.atTime(localStart.toLocalTime()).atZone(zone).toInstant();
        Instant end = date.atTime(localStart.toLocalTime()).plus(Duration.between(localStart, localEnd))
                .atZone(zone).toInstant();
        return new Candidate(row, date, start, end, null, null, row.version());
    }

    /** A detached occurrence carries its own schedule and the version of its parent series. */
    private Candidate detached(AppointmentRecord exception, LocalDate date, long seriesVersion) {
        return new Candidate(exception, date, exception.startAt(), exception.endAt(),
                exception.startDate(), exception.endDate(), seriesVersion);
    }

    /** Builds the response of one stored row, loading its participants and, for an exception, its series version. */
    private AppointmentResponse single(AppointmentRecord row, LocalDate date) {
        long version = row.seriesId() == null ? row.version() : require(row.accountId(), row.seriesId()).version();
        return response(new Candidate(row, date, row.startAt(), row.endAt(), row.startDate(), row.endDate(), version),
                appointments.participantIds(row.id()));
    }

    private AppointmentResponse response(Candidate item, List<Long> participantIds) {
        AppointmentRecord row = item.row();
        RecurrenceRequest rule = row.recurrenceFreq() == null ? null : new RecurrenceRequest(
                row.recurrenceFreq(), row.recurrenceInterval(), weekdays(row.recurrenceWeekdays()),
                row.recurrenceUntil(), row.recurrenceCount());
        Long seriesId = row.seriesId() == null && rule != null ? Long.valueOf(row.id()) : row.seriesId();
        return new AppointmentResponse(row.id(), item.date(), seriesId,
                rule != null || row.seriesId() != null, row.title(), row.location(), row.notes(), row.allDay(),
                item.start(), item.end(), item.startDate(), item.endDate(), participantIds, rule, item.version());
    }

    private Set<DayOfWeek> weekdays(String text) {
        if (text == null) return Set.of();
        return java.util.Arrays.stream(text.split(","))
                .map(day -> switch (day) {
                    case "MON" -> DayOfWeek.MONDAY; case "TUE" -> DayOfWeek.TUESDAY;
                    case "WED" -> DayOfWeek.WEDNESDAY; case "THU" -> DayOfWeek.THURSDAY;
                    case "FRI" -> DayOfWeek.FRIDAY; case "SAT" -> DayOfWeek.SATURDAY;
                    default -> DayOfWeek.SUNDAY;
                }).collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Ranges are half-open: a timed item overlaps when it starts before the upper bound and ends after the lower
     * bound, or is a zero-length item at or after the lower bound. All-day end dates are inclusive.
     */
    private boolean overlaps(Candidate item, LocalDate from, LocalDate to, Instant lower, Instant upper) {
        if (item.row().allDay()) return item.startDate().isBefore(to) && !item.endDate().isBefore(from);
        return item.start().isBefore(upper) && (item.end().isAfter(lower) || !item.start().isBefore(lower));
    }

    private int spanDays(AppointmentRecord row, ZoneId zone) {
        return row.allDay() ? (int) ChronoUnit.DAYS.between(row.startDate(), row.endDate())
                : (int) ChronoUnit.DAYS.between(row.startAt().atZone(zone).toLocalDate(),
                        row.endAt().atZone(zone).toLocalDate());
    }

    private LocalDate localStart(AppointmentRecord row, ZoneId zone) {
        return row.allDay() ? row.startDate() : row.startAt().atZone(zone).toLocalDate();
    }

    private boolean scheduleChanged(AppointmentRecord old, AppointmentRecord changed) {
        return old.allDay() != changed.allDay()
                || !java.util.Objects.equals(old.startAt(), changed.startAt())
                || !java.util.Objects.equals(old.endAt(), changed.endAt())
                || !java.util.Objects.equals(old.startDate(), changed.startDate())
                || !java.util.Objects.equals(old.endDate(), changed.endDate())
                || !java.util.Objects.equals(old.recurrenceFreq(), changed.recurrenceFreq())
                || !java.util.Objects.equals(old.recurrenceInterval(), changed.recurrenceInterval())
                || !java.util.Objects.equals(old.recurrenceWeekdays(), changed.recurrenceWeekdays())
                || !java.util.Objects.equals(old.recurrenceUntil(), changed.recurrenceUntil())
                || !java.util.Objects.equals(old.recurrenceCount(), changed.recurrenceCount());
    }

    private AppointmentRecord withId(AppointmentRecord row, long id) {
        return new AppointmentRecord(id, row.accountId(), row.title(), row.location(), row.notes(),
                row.allDay(), row.startAt(), row.endAt(), row.startDate(), row.endDate(),
                row.recurrenceFreq(), row.recurrenceInterval(), row.recurrenceWeekdays(),
                row.recurrenceUntil(), row.recurrenceCount(), row.seriesId(), row.originalDate(),
                row.version(), row.createdAt(), row.updatedAt());
    }

    private List<Long> sorted(Set<Long> ids) { return ids.stream().sorted().toList(); }
    private AppointmentRecord require(UUID accountId, long id) {
        return appointments.find(accountId, id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    private void bad(String reason) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason); }
    private void conflict() { throw conflictException(); }
    private ResponseStatusException conflictException() { return new ResponseStatusException(HttpStatus.CONFLICT, "Appointment changed; reload and retry"); }
    private void notFound() { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
}
