package com.calendarbuddy.api.appointment;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AppointmentRepository {
    private static final RowMapper<AppointmentRecord> MAPPER = AppointmentRepository::map;
    private final JdbcTemplate jdbc;

    public AppointmentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /**
     * One-off appointments that may overlap a range. The SQL bound is inclusive at the lower end; callers apply the
     * exact half-open overlap rule.
     */
    public List<AppointmentRecord> oneOffsNear(UUID accountId, Instant lower, Instant upper,
                                               LocalDate from, LocalDate to) {
        return jdbc.query("SELECT * FROM appointments WHERE account_id=? AND series_id IS NULL"
                + " AND recurrence_freq IS NULL AND ((NOT all_day AND start_at<? AND end_at>=?)"
                + " OR (all_day AND start_date<? AND end_date>=?)) ORDER BY id",
                MAPPER, accountId, sqlTime(upper), sqlTime(lower), to, from);
    }

    /** Every repeating series of an account; detached occurrences can move far from their original dates. */
    public List<AppointmentRecord> series(UUID accountId) {
        return jdbc.query("SELECT * FROM appointments WHERE account_id=? AND series_id IS NULL"
                + " AND recurrence_freq IS NOT NULL ORDER BY id", MAPPER, accountId);
    }

    /** Detached occurrences whose original date lies in a window or whose own schedule may overlap a range. */
    public List<AppointmentRecord> exceptionsNear(UUID accountId, LocalDate windowStart, LocalDate to,
                                                  Instant lower, Instant upper, LocalDate from) {
        return jdbc.query("SELECT * FROM appointments WHERE account_id=? AND series_id IS NOT NULL"
                + " AND ((original_date>=? AND original_date<?)"
                + " OR (NOT all_day AND start_at<? AND end_at>=?)"
                + " OR (all_day AND start_date<? AND end_date>=?)) ORDER BY id",
                MAPPER, accountId, windowStart, to, sqlTime(upper), sqlTime(lower), to, from);
    }

    public Optional<AppointmentRecord> exception(UUID accountId, long seriesId, LocalDate originalDate) {
        return jdbc.query("SELECT * FROM appointments WHERE account_id=? AND series_id=? AND original_date=?",
                MAPPER, accountId, seriesId, originalDate).stream().findFirst();
    }

    /** Skipped occurrence dates per series within {@code [from, to)}. */
    public Map<Long, Set<LocalDate>> exdates(UUID accountId, LocalDate from, LocalDate to) {
        Map<Long, Set<LocalDate>> result = new HashMap<>();
        jdbc.query("SELECT series_id, occurrence_date FROM appointment_exdates WHERE account_id=?"
                        + " AND occurrence_date>=? AND occurrence_date<?",
                (RowCallbackHandler) rs -> result.computeIfAbsent(rs.getLong("series_id"), key -> new HashSet<>())
                        .add(rs.getObject("occurrence_date", LocalDate.class)),
                accountId, from, to);
        return result;
    }

    /** Participant ids of several appointments in one query, each list ordered by member id. */
    public Map<Long, List<Long>> participantIds(UUID accountId, Collection<Long> appointmentIds) {
        Map<Long, List<Long>> result = new HashMap<>();
        if (appointmentIds.isEmpty()) return result;
        new NamedParameterJdbcTemplate(jdbc).query(
                "SELECT appointment_id, member_id FROM appointment_participants WHERE account_id=:account"
                        + " AND appointment_id IN (:ids) ORDER BY member_id",
                Map.of("account", accountId, "ids", appointmentIds),
                (RowCallbackHandler) rs -> result.computeIfAbsent(rs.getLong("appointment_id"),
                        key -> new ArrayList<>()).add(rs.getLong("member_id")));
        return result;
    }

    public Optional<AppointmentRecord> find(UUID accountId, long id) {
        return jdbc.query("SELECT * FROM appointments WHERE account_id=? AND id=?", MAPPER, accountId, id)
                .stream().findFirst();
    }

    public AppointmentRecord insert(AppointmentRecord value) {
        return jdbc.queryForObject(
                "INSERT INTO appointments(account_id,title,location,notes,all_day,start_at,end_at,"
                        + "start_date,end_date,recurrence_freq,recurrence_interval,recurrence_weekdays,"
                        + "recurrence_until,recurrence_count,series_id,original_date,version)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0) RETURNING *",
                MAPPER,
                value.accountId(), value.title(), value.location(), value.notes(), value.allDay(),
                sqlTime(value.startAt()), sqlTime(value.endAt()), value.startDate(), value.endDate(),
                value.recurrenceFreq(), value.recurrenceInterval(), value.recurrenceWeekdays(),
                value.recurrenceUntil(), value.recurrenceCount(), value.seriesId(), value.originalDate());
    }

    public Optional<AppointmentRecord> update(AppointmentRecord value, long expectedVersion) {
        return jdbc.query(
                "UPDATE appointments SET title=?,location=?,notes=?,all_day=?,start_at=?,end_at=?,"
                        + "start_date=?,end_date=?,recurrence_freq=?,recurrence_interval=?,"
                        + "recurrence_weekdays=?,recurrence_until=?,recurrence_count=?,"
                        + "version=version+1,updated_at=now()"
                        + " WHERE id=? AND account_id=? AND version=? RETURNING *",
                MAPPER,
                value.title(), value.location(), value.notes(), value.allDay(), sqlTime(value.startAt()),
                sqlTime(value.endAt()), value.startDate(), value.endDate(), value.recurrenceFreq(),
                value.recurrenceInterval(), value.recurrenceWeekdays(), value.recurrenceUntil(),
                value.recurrenceCount(), value.id(), value.accountId(), expectedVersion)
                .stream().findFirst();
    }

    public boolean delete(UUID accountId, long id, long expectedVersion) {
        return jdbc.update("DELETE FROM appointments WHERE account_id=? AND id=? AND version=?",
                accountId, id, expectedVersion) == 1;
    }

    public List<Long> participantIds(long appointmentId) {
        return jdbc.queryForList("SELECT member_id FROM appointment_participants WHERE appointment_id=?"
                + " ORDER BY member_id", Long.class, appointmentId);
    }

    public void replaceParticipants(UUID accountId, long appointmentId, List<Long> ids) {
        jdbc.update("DELETE FROM appointment_participants WHERE appointment_id=?", appointmentId);
        for (Long id : ids) {
            jdbc.update("INSERT INTO appointment_participants(appointment_id,member_id,account_id)"
                    + " VALUES (?,?,?)", appointmentId, id, accountId);
        }
    }

    private static AppointmentRecord map(ResultSet rs, int row) throws SQLException {
        return new AppointmentRecord(
                rs.getLong("id"), rs.getObject("account_id", UUID.class), rs.getString("title"),
                rs.getString("location"), rs.getString("notes"), rs.getBoolean("all_day"),
                instant(rs, "start_at"), instant(rs, "end_at"),
                rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class),
                rs.getString("recurrence_freq"), (Integer) rs.getObject("recurrence_interval"),
                rs.getString("recurrence_weekdays"), rs.getObject("recurrence_until", LocalDate.class),
                (Integer) rs.getObject("recurrence_count"), (Long) rs.getObject("series_id"),
                rs.getObject("original_date", LocalDate.class), rs.getLong("version"),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private static Instant instant(ResultSet rs, String name) throws SQLException {
        var timestamp = rs.getTimestamp(name);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static OffsetDateTime sqlTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
