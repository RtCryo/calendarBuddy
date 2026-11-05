package com.calendarbuddy.api.appointment;

import com.calendarbuddy.api.account.AccountRepository;
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
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AppointmentService {
    private final AppointmentRepository appointments;
    private final FamilyMemberRepository members;
    private final AccountRepository accounts;
    private final RecurrenceEngine recurrence;
    private final JdbcTemplate jdbc;

    public AppointmentService(AppointmentRepository appointments, FamilyMemberRepository members,
                              AccountRepository accounts, RecurrenceEngine recurrence, JdbcTemplate jdbc) {
        this.appointments = appointments;
        this.members = members;
        this.accounts = accounts;
        this.recurrence = recurrence;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> range(UUID accountId, LocalDate from, LocalDate to, Long memberId) {
        if (from == null || to == null || !to.isAfter(from) || ChronoUnit.DAYS.between(from, to) > 100)
            bad("Range must contain 1 to 100 days");
        if (memberId != null && !members.existsByIdAndAccountId(memberId, accountId)) notFound();
        ZoneId zone = zone(accountId);
        List<AppointmentRecord> rows = appointments.all(accountId);
        List<AppointmentResponse> result = new ArrayList<>();
        for (AppointmentRecord row : rows) {
            if (row.seriesId() != null) continue;
            if (row.recurrenceFreq() == null) {
                if (overlaps(row, from, to, zone) && matchesMember(row.id(), memberId))
                    result.add(response(row, localStart(row, zone), row.startAt(), row.endAt()));
                continue;
            }
            LocalDate start = localStart(row, zone);
            int span = spanDays(row, zone);
            for (LocalDate date : recurrence.expand(row, from, to, start, span)) {
                if (exdated(row.id(), date)) continue;
                AppointmentRecord exception = exception(accountId, row.id(), date);
                if (exception != null) {
                    if (overlaps(exception, from, to, zone) && matchesMember(exception.id(), memberId))
                        result.add(response(exception, date, exception.startAt(), exception.endAt()));
                } else if (matchesMember(row.id(), memberId)) {
                    AppointmentResponse occurrence = occurrence(row, date, zone);
                    if (overlaps(occurrence, from, to, zone)) result.add(occurrence);
                }
            }
            // A moved exception can overlap this range even when its original date does not.
            for (AppointmentRecord exception : exceptions(accountId, row.id())) {
                if (exception.originalDate().isBefore(from.minusDays(span))
                        || !exception.originalDate().isBefore(to)) {
                    if (overlaps(exception, from, to, zone) && matchesMember(exception.id(), memberId))
                        result.add(response(exception, exception.originalDate(),
                                exception.startAt(), exception.endAt()));
                }
            }
        }
        result.sort(Comparator.comparing((AppointmentResponse item) -> item.allDay()
                ? item.startDate().atStartOfDay(zone).toInstant() : item.startAt())
                .thenComparing(AppointmentResponse::title));
        return result;
    }

    @Transactional(readOnly = true)
    public AppointmentResponse get(UUID accountId, long id) {
        AppointmentRecord row = require(accountId, id);
        return response(row, localStart(row, zone(accountId)), row.startAt(), row.endAt());
    }

    @Transactional
    public AppointmentResponse create(UUID accountId, AppointmentRequest request) {
        AppointmentRecord input = validated(accountId, 0, request, null, null);
        AppointmentRecord saved = appointments.insert(input);
        appointments.replaceParticipants(accountId, saved.id(), sorted(request.participantIds()));
        return get(accountId, saved.id());
    }

    @Transactional
    public AppointmentResponse update(UUID accountId, long id, AppointmentRequest request,
                                      String scope, LocalDate occurrenceDate) {
        AppointmentRecord current = require(accountId, id);
        if (request.version() == null) bad("Version is required");
        AppointmentRecord series = current.seriesId() == null ? current : require(accountId, current.seriesId());
        if (series.recurrenceFreq() != null && "this".equals(scope))
            return updateOccurrence(accountId, series, current, occurrenceDate, request);
        if (series.recurrenceFreq() != null && !"all".equals(scope)) bad("Choose this or all occurrences");
        if (current.seriesId() != null && !"all".equals(scope)) bad("Choose this or all occurrences");
        if (current.seriesId() != null) current = series;
        if (current.version() != request.version()) conflict();
        AppointmentRecord input = validated(accountId, current.id(), request, null, null);
        boolean scheduleChanged = scheduleChanged(current, input);
        AppointmentRecord saved = appointments.update(input, request.version()).orElseThrow(() -> conflictException());
        appointments.replaceParticipants(accountId, saved.id(), sorted(request.participantIds()));
        if (scheduleChanged && current.recurrenceFreq() != null) {
            jdbc.update("DELETE FROM appointments WHERE account_id=? AND series_id=?", accountId, saved.id());
            jdbc.update("DELETE FROM appointment_exdates WHERE account_id=? AND series_id=?", accountId, saved.id());
        }
        return get(accountId, saved.id());
    }

    @Transactional
    public void delete(UUID accountId, long id, long version, String scope, LocalDate occurrenceDate) {
        AppointmentRecord current = require(accountId, id);
        AppointmentRecord series = current.seriesId() == null ? current : require(accountId, current.seriesId());
        if (series.recurrenceFreq() != null && "this".equals(scope)) {
            LocalDate date = occurrenceDate == null ? current.originalDate() : occurrenceDate;
            checkOccurrence(accountId, series, date, version);
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

    private AppointmentResponse updateOccurrence(UUID accountId, AppointmentRecord series,
                                                  AppointmentRecord current, LocalDate date,
                                                  AppointmentRequest request) {
        if (date == null) date = current.originalDate();
        checkOccurrence(accountId, series, date, request.version());
        AppointmentRecord input = validated(accountId, 0, request, series.id(), date);
        AppointmentRecord existing = exception(accountId, series.id(), date);
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
        return response(saved, date, saved.startAt(), saved.endAt());
    }

    private void checkOccurrence(UUID accountId, AppointmentRecord series, LocalDate date, long version) {
        if (series.version() != version) conflict();
        if (date == null || !recurrence.occursOn(series, localStart(series, zone(accountId)), date)) notFound();
    }

    private void bump(AppointmentRecord series) {
        int changed = jdbc.update("UPDATE appointments SET version=version+1,updated_at=now()"
                + " WHERE id=? AND account_id=? AND version=?", series.id(), series.accountId(), series.version());
        if (changed != 1) conflict();
    }

    private AppointmentRecord validated(UUID accountId, long id, AppointmentRequest request,
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
            LocalDate first = request.allDay() ? startDate : startAt.atZone(zone(accountId)).toLocalDate();
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

    private AppointmentResponse occurrence(AppointmentRecord row, LocalDate date, ZoneId zone) {
        if (row.allDay()) {
            long span = ChronoUnit.DAYS.between(row.startDate(), row.endDate());
            return response(row, date, null, null, date, date.plusDays(span));
        }
        LocalDateTime localStart = row.startAt().atZone(zone).toLocalDateTime();
        LocalDateTime localEnd = row.endAt().atZone(zone).toLocalDateTime();
        Instant start = date.atTime(localStart.toLocalTime()).atZone(zone).toInstant();
        Instant end = date.atTime(localStart.toLocalTime()).plus(Duration.between(localStart, localEnd))
                .atZone(zone).toInstant();
        return response(row, date, start, end);
    }

    private AppointmentResponse response(AppointmentRecord row, LocalDate date, Instant start, Instant end) {
        return response(row, date, start, end, row.startDate(), row.endDate());
    }

    private AppointmentResponse response(AppointmentRecord row, LocalDate date, Instant start, Instant end,
                                         LocalDate startDate, LocalDate endDate) {
        RecurrenceRequest rule = row.recurrenceFreq() == null ? null : new RecurrenceRequest(
                row.recurrenceFreq(), row.recurrenceInterval(), weekdays(row.recurrenceWeekdays()),
                row.recurrenceUntil(), row.recurrenceCount());
        Long seriesId = row.seriesId() == null && rule != null ? Long.valueOf(row.id()) : row.seriesId();
        long version = row.seriesId() == null ? row.version()
                : require(row.accountId(), row.seriesId()).version();
        return new AppointmentResponse(row.id(), date, seriesId,
                rule != null || row.seriesId() != null, row.title(), row.location(), row.notes(), row.allDay(),
                start, end, startDate, endDate, appointments.participantIds(row.id()), rule, version);
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

    private boolean overlaps(AppointmentRecord row, LocalDate from, LocalDate to, ZoneId zone) {
        return overlaps(response(row, localStart(row, zone), row.startAt(), row.endAt()), from, to, zone);
    }

    private boolean overlaps(AppointmentResponse item, LocalDate from, LocalDate to, ZoneId zone) {
        if (item.allDay()) return item.startDate().isBefore(to) && !item.endDate().isBefore(from);
        Instant lower = from.atStartOfDay(zone).toInstant();
        Instant upper = to.atStartOfDay(zone).toInstant();
        return item.startAt().isBefore(upper) && !item.endAt().isBefore(lower);
    }

    private boolean matchesMember(long appointmentId, Long memberId) {
        return memberId == null || appointments.participantIds(appointmentId).contains(memberId);
    }

    private boolean exdated(long seriesId, LocalDate date) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM appointment_exdates"
                + " WHERE series_id=? AND occurrence_date=?", Integer.class, seriesId, date);
        return count != null && count > 0;
    }

    private AppointmentRecord exception(UUID accountId, long seriesId, LocalDate date) {
        return appointments.all(accountId).stream()
                .filter(row -> row.seriesId() != null && row.seriesId() == seriesId
                        && row.originalDate().equals(date)).findFirst().orElse(null);
    }

    private List<AppointmentRecord> exceptions(UUID accountId, long seriesId) {
        return appointments.all(accountId).stream()
                .filter(row -> row.seriesId() != null && row.seriesId() == seriesId).toList();
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
    private ZoneId zone(UUID accountId) {
        return ZoneId.of(accounts.findById(accountId).orElseThrow().getTimeZone());
    }
    private void bad(String reason) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason); }
    private void conflict() { throw conflictException(); }
    private ResponseStatusException conflictException() { return new ResponseStatusException(HttpStatus.CONFLICT, "Appointment changed; reload and retry"); }
    private void notFound() { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
}
