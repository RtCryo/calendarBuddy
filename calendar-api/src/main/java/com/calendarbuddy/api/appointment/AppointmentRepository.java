package com.calendarbuddy.api.appointment;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class AppointmentRepository {
    private static final RowMapper<AppointmentRecord> MAPPER = AppointmentRepository::map;
    private final JdbcTemplate jdbc;

    public AppointmentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<AppointmentRecord> all(UUID accountId) {
        return jdbc.query("SELECT * FROM appointments WHERE account_id=? ORDER BY id", MAPPER, accountId);
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
