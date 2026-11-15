package com.calendarbuddy.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calendarbuddy.api.account.AccountPrincipal;
import com.calendarbuddy.api.account.AccountRepository;
import com.calendarbuddy.api.account.AccountResolver;
import com.calendarbuddy.api.appointment.AppointmentRecord;
import com.calendarbuddy.api.appointment.AppointmentRequest;
import com.calendarbuddy.api.appointment.AppointmentService;
import com.calendarbuddy.api.appointment.RecurrenceEngine;
import com.calendarbuddy.api.appointment.RecurrenceRequest;
import com.calendarbuddy.api.member.FamilyMemberService;
import com.calendarbuddy.api.member.MemberRequest;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class CalendarApiApplicationTest {
    private static final java.time.ZoneId BERLIN = java.time.ZoneId.of("Europe/Berlin");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired AccountResolver resolver;
    @Autowired AccountRepository accounts;
    @Autowired FamilyMemberService members;
    @Autowired AppointmentService calendar;
    @Autowired RecurrenceEngine recurrence;

    @Test
    void contextLoadsAgainstPostgres() {
        assertThat(jdbc.queryForObject("select 1", Integer.class)).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from information_schema.columns where table_name = 'accounts'"
                                        + " and column_name in ('id', 'email', 'time_zone', 'created_at')",
                                Integer.class))
                .isEqualTo(4);
        assertThat(
                        jdbc.queryForObject(
                                "select is_nullable from information_schema.columns where table_name = 'accounts'"
                                        + " and column_name = 'time_zone'",
                                String.class))
                .isEqualTo("NO");
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from information_schema.columns where table_name = 'family_members'"
                                        + " and column_name in ('id','account_id','name','color','created_at')",
                                Integer.class))
                .isEqualTo(5);
        assertThat(
                        jdbc.queryForObject(
                                "select indexdef from pg_indexes where tablename='family_members'"
                                        + " and indexname='uk_family_members_account_name_ci'",
                                String.class))
                .contains("lower((name)::text)");
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from information_schema.table_constraints"
                                        + " where table_name='family_members' and constraint_name in"
                                        + " ('fk_family_members_account','ck_family_members_color')",
                                Integer.class))
                .isEqualTo(2);
    }

    @Test
    void provisionsOneAccountAndRefreshesEmail() {
        UUID id = UUID.randomUUID();
        AccountPrincipal first = resolver.resolve(token(id, "First@Example.com"));
        AccountPrincipal changed = resolver.resolve(token(id, "second@example.com"));

        assertThat(first.id()).isEqualTo(id);
        assertThat(first.email()).isEqualTo("first@example.com");
        assertThat(first.timeZone()).isEqualTo("Europe/Berlin");
        assertThat(changed.email()).isEqualTo("second@example.com");
        assertThat(accounts.findById(id)).get().extracting("email").isEqualTo("second@example.com");
    }

    @Test
    void concurrentFirstRequestsCreateOneAccount() throws Exception {
        UUID id = UUID.randomUUID();
        Jwt token = token(id, "family@example.com");
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first =
                    workers.submit(
                            () -> {
                                start.await();
                                return resolver.resolve(token).id();
                            });
            var second =
                    workers.submit(
                            () -> {
                                start.await();
                                return resolver.resolve(token).id();
                            });
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(second.get(10, TimeUnit.SECONDS));
            assertThat(accounts.findById(id)).isPresent();
        }
    }

    @Test
    void appointmentAndRecurrenceConstraintsAreInstalled() {
        assertThat(
                        jdbc.queryForList(
                                "select constraint_name from information_schema.table_constraints"
                                        + " where table_name in ('appointments','appointment_participants','appointment_exdates')",
                                String.class))
                .contains(
                        "ck_appointments_schedule",
                        "ck_appointments_recurrence",
                        "ck_appointments_exception",
                        "fk_participants_appointment_account",
                        "fk_participants_member_account",
                        "fk_exdates_series_account",
                        "pk_appointment_exdates");
        assertThat(
                        jdbc.queryForList(
                                "select indexname from pg_indexes where tablename='appointments'",
                                String.class))
                .contains(
                        "idx_appointments_timed_range",
                        "idx_appointments_all_day_range",
                        "uk_appointments_series_original_date");
        UUID id = UUID.randomUUID();
        resolver.resolve(token(id, "schema@example.com"));
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "insert into appointments(account_id,title,all_day,start_at,end_at,version)"
                                                + " values (?,?,false,?,?,0)",
                                        id,
                                        "Bad range",
                                        Instant.parse("2026-11-10T10:00:00Z"),
                                        Instant.parse("2026-11-10T09:00:00Z")))
                .isInstanceOf(Exception.class);
    }

    @Test
    void familyMembersAreSharedBySessionsAndPrivateToAnAccount() {
        UUID family = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        resolver.resolve(token(family, "family-" + family + "@example.com"));
        resolver.resolve(token(other, "family-" + other + "@example.com"));
        var anna = members.create(family, new MemberRequest(" Anna ", "coral"));
        assertThat(anna.name()).isEqualTo("Anna");
        assertThat(members.list(family)).hasSize(1);
        assertThat(members.list(family)).hasSize(1);
        assertThat(members.list(other)).isEmpty();
        assertThatThrownBy(() -> members.get(other, anna.id())).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> members.update(other, anna.id(), new MemberRequest("X", "sky")))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> members.remove(other, anna.id())).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> members.create(family, new MemberRequest(" anna ", "teal")))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> members.create(family, new MemberRequest("", "teal")))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> members.create(family, new MemberRequest("Ben", "unknown")))
                .isInstanceOf(Exception.class);
        assertThat(members.update(family, anna.id(), new MemberRequest("Annie", "indigo")).color())
                .isEqualTo("indigo");
    }

    @Test
    void aFamilyCannotAddMoreThanTwentyMembers() {
        UUID family = UUID.randomUUID();
        resolver.resolve(token(family, "limit-" + family + "@example.com"));
        for (int number = 1; number <= 20; number++) {
            members.create(family, new MemberRequest("Member " + number, "sky"));
        }
        assertThatThrownBy(() -> members.create(family, new MemberRequest("Member 21", "sky")))
                .isInstanceOf(Exception.class);
        assertThat(members.list(family)).hasSize(20);
    }

    @Test
    void memberRemovalDeletesSoleAppointmentsAndUnlinksSharedOnes() {
        UUID family = UUID.randomUUID();
        resolver.resolve(token(family, "remove-" + family + "@example.com"));
        long anna = members.create(family, new MemberRequest("Anna", "coral")).id();
        long ben = members.create(family, new MemberRequest("Ben", "teal")).id();
        long sole = appointment(family, "Sole", null, null);
        long series = appointment(family, "Series", null, null);
        jdbc.update(
                "update appointments set recurrence_freq='DAILY', recurrence_interval=1 where id=?",
                series);
        long exception = appointment(family, "Exception", series, "2026-11-12");
        long shared = appointment(family, "Shared", null, null);
        for (long id : new long[] {sole, series, exception, shared}) {
            participant(family, id, anna);
        }
        participant(family, shared, ben);
        jdbc.update(
                "insert into appointment_exdates(series_id,account_id,occurrence_date) values (?,?,?)",
                series,
                family,
                java.sql.Date.valueOf("2026-11-13"));

        assertThat(members.impact(family, anna).deleteCount()).isEqualTo(3);
        assertThat(members.impact(family, anna).unlinkCount()).isEqualTo(1);
        members.remove(family, anna);

        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from appointments where account_id=?",
                                Long.class,
                                family))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from appointment_exdates where account_id=?",
                                Long.class,
                                family))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from appointment_participants where appointment_id=? and member_id=?",
                                Long.class,
                                shared,
                                ben))
                .isEqualTo(1);
    }

    @Test
    void failedMemberDeletionRollsBackAppointmentChanges() {
        UUID family = UUID.randomUUID();
        resolver.resolve(token(family, "rollback-" + family + "@example.com"));
        long member = members.create(family, new MemberRequest("Anna", "coral")).id();
        long appointment = appointment(family, "Keep", null, null);
        participant(family, appointment, member);
        jdbc.execute(
                "create function reject_member_delete() returns trigger language plpgsql as $$"
                        + " begin raise exception 'blocked'; end $$");
        jdbc.execute(
                "create trigger reject_member_delete before delete on family_members"
                        + " for each row execute function reject_member_delete()");
        try {
            assertThatThrownBy(() -> members.remove(family, member)).isInstanceOf(Exception.class);
        } finally {
            jdbc.execute("drop trigger reject_member_delete on family_members");
            jdbc.execute("drop function reject_member_delete()");
        }
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from appointments where id=?",
                                Long.class,
                                appointment))
                .isEqualTo(1);
        assertThat(members.get(family, member)).isNotNull();
    }

    @Test
    void appointmentsValidateOwnershipOverlapAndVersions() {
        UUID family = UUID.randomUUID(), other = UUID.randomUUID();
        resolver.resolve(token(family, "events-" + family + "@example.com"));
        resolver.resolve(token(other, "events-" + other + "@example.com"));
        long anna = members.create(family, new MemberRequest("Anna", "coral")).id();
        long ben = members.create(family, new MemberRequest("Ben", "teal")).id();
        long stranger = members.create(other, new MemberRequest("Other", "sky")).id();
        var timed =
                new AppointmentRequest(
                        " Dentist ",
                        null,
                        null,
                        false,
                        OffsetDateTime.parse("2026-10-25T10:00:00+01:00"),
                        OffsetDateTime.parse("2026-10-25T10:30:00+01:00"),
                        null,
                        null,
                        Set.of(anna),
                        null,
                        null);
        var saved = calendar.create(family, BERLIN, timed);
        assertThat(saved.title()).isEqualTo("Dentist");
        assertThat(saved.startAt().atZone(java.time.ZoneId.of("Europe/Berlin")).getHour())
                .isEqualTo(10);
        assertThat(
                        calendar.range(
                                family,
                                BERLIN,
                                LocalDate.parse("2026-10-25"),
                                LocalDate.parse("2026-10-26"),
                                anna))
                .hasSize(1);
        assertThat(
                        calendar.range(
                                family,
                                BERLIN,
                                LocalDate.parse("2026-10-25"),
                                LocalDate.parse("2026-10-26"),
                                ben))
                .isEmpty();
        assertThatThrownBy(() -> calendar.get(other, BERLIN, saved.appointmentId()))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(
                        () ->
                                calendar.create(
                                        family,
                                        BERLIN,
                                        new AppointmentRequest(
                                                "X",
                                                null,
                                                null,
                                                true,
                                                null,
                                                null,
                                                LocalDate.parse("2026-11-01"),
                                                LocalDate.parse("2026-11-01"),
                                                Set.of(stranger),
                                                null,
                                                null)))
                .isInstanceOf(Exception.class);
        var multi =
                calendar.create(
                        family,
                        BERLIN,
                        new AppointmentRequest(
                                "Holiday",
                                null,
                                null,
                                true,
                                null,
                                null,
                                LocalDate.parse("2026-11-01"),
                                LocalDate.parse("2026-11-03"),
                                Set.of(anna, ben),
                                null,
                                null));
        assertThat(
                        calendar.range(
                                family,
                                BERLIN,
                                LocalDate.parse("2026-11-03"),
                                LocalDate.parse("2026-11-04"),
                                null))
                .hasSize(1);
        assertThatThrownBy(
                        () ->
                                calendar.update(
                                        family,
                                        BERLIN,
                                        multi.appointmentId(),
                                        new AppointmentRequest(
                                                "Changed",
                                                null,
                                                null,
                                                true,
                                                null,
                                                null,
                                                LocalDate.parse("2026-11-01"),
                                                LocalDate.parse("2026-11-03"),
                                                Set.of(anna),
                                                null,
                                                12L),
                                        null,
                                        null))
                .isInstanceOf(Exception.class);
        assertThat(calendar.get(family, BERLIN, multi.appointmentId()).title())
                .isEqualTo("Holiday");
        assertThatThrownBy(
                        () ->
                                calendar.range(
                                        family,
                                        BERLIN,
                                        LocalDate.parse("2026-01-01"),
                                        LocalDate.parse("2026-05-01"),
                                        null))
                .isInstanceOf(Exception.class);
    }

    @Test
    void recurrenceAndOccurrenceOverridesWorkAcrossDst() {
        UUID family = UUID.randomUUID();
        resolver.resolve(token(family, "repeat-" + family + "@example.com"));
        long anna = members.create(family, new MemberRequest("Anna", "coral")).id();
        long ben = members.create(family, new MemberRequest("Ben", "teal")).id();
        var request =
                new AppointmentRequest(
                        "Lesson",
                        null,
                        null,
                        false,
                        OffsetDateTime.parse("2026-10-20T10:00:00+02:00"),
                        OffsetDateTime.parse("2026-10-20T11:00:00+02:00"),
                        null,
                        null,
                        Set.of(anna),
                        new RecurrenceRequest(
                                "WEEKLY",
                                1,
                                Set.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
                                null,
                                6),
                        null);
        var series = calendar.create(family, BERLIN, request);
        var occurrences =
                calendar.range(
                        family,
                        BERLIN,
                        LocalDate.parse("2026-10-20"),
                        LocalDate.parse("2026-11-11"),
                        null);
        assertThat(occurrences).hasSize(6);
        assertThat(occurrences)
                .allSatisfy(
                        item ->
                                assertThat(
                                                item.startAt()
                                                        .atZone(
                                                                java.time.ZoneId.of(
                                                                        "Europe/Berlin"))
                                                        .getHour())
                                        .isEqualTo(10));
        LocalDate movedDate = LocalDate.parse("2026-10-29");
        var moved =
                calendar.update(
                        family,
                        BERLIN,
                        series.appointmentId(),
                        new AppointmentRequest(
                                "Lesson",
                                null,
                                null,
                                false,
                                OffsetDateTime.parse("2026-10-29T17:00:00+01:00"),
                                OffsetDateTime.parse("2026-10-29T18:00:00+01:00"),
                                null,
                                null,
                                Set.of(ben),
                                null,
                                series.version()),
                        "this",
                        movedDate);
        assertThat(moved.seriesId()).isEqualTo(series.appointmentId());
        assertThat(calendar.range(family, BERLIN, movedDate, movedDate.plusDays(1), anna))
                .isEmpty();
        assertThat(calendar.range(family, BERLIN, movedDate, movedDate.plusDays(1), ben))
                .hasSize(1);
        var current = calendar.get(family, BERLIN, series.appointmentId());
        calendar.delete(
                family,
                BERLIN,
                series.appointmentId(),
                current.version(),
                "this",
                LocalDate.parse("2026-11-03"));
        assertThat(
                        calendar.range(
                                family,
                                BERLIN,
                                LocalDate.parse("2026-11-03"),
                                LocalDate.parse("2026-11-04"),
                                null))
                .isEmpty();
        var renamed =
                calendar.update(
                        family,
                        BERLIN,
                        series.appointmentId(),
                        new AppointmentRequest(
                                "Swim training",
                                null,
                                null,
                                false,
                                request.startAt(),
                                request.endAt(),
                                null,
                                null,
                                Set.of(anna),
                                request.recurrence(),
                                current.version() + 1),
                        "all",
                        null);
        assertThat(calendar.range(family, BERLIN, movedDate, movedDate.plusDays(1), ben))
                .singleElement()
                .extracting("title")
                .isEqualTo("Lesson");
        var rescheduled =
                calendar.update(
                        family,
                        BERLIN,
                        series.appointmentId(),
                        new AppointmentRequest(
                                "Swim training",
                                null,
                                null,
                                false,
                                OffsetDateTime.parse("2026-10-20T15:00:00+02:00"),
                                OffsetDateTime.parse("2026-10-20T16:00:00+02:00"),
                                null,
                                null,
                                Set.of(anna),
                                request.recurrence(),
                                renamed.version()),
                        "all",
                        null);
        assertThat(calendar.range(family, BERLIN, movedDate, movedDate.plusDays(1), ben)).isEmpty();
        assertThat(
                        calendar.range(
                                family,
                                BERLIN,
                                LocalDate.parse("2026-11-03"),
                                LocalDate.parse("2026-11-04"),
                                anna))
                .hasSize(1);
        calendar.delete(family, BERLIN, series.appointmentId(), rescheduled.version(), "all", null);
        assertThat(
                        calendar.range(
                                family,
                                BERLIN,
                                LocalDate.parse("2026-10-20"),
                                LocalDate.parse("2026-11-11"),
                                null))
                .isEmpty();
    }

    @Test
    void recurrenceSkipsInvalidCalendarDatesAndHonorsCountAndUntil() {
        LocalDate jan31 = LocalDate.parse("2026-01-31");
        var monthly = rule("MONTHLY", 1, null, null);
        assertThat(recurrence.expand(monthly, jan31, LocalDate.parse("2026-06-01"), jan31, 0))
                .containsExactly(
                        jan31, LocalDate.parse("2026-03-31"), LocalDate.parse("2026-05-31"));
        LocalDate leap = LocalDate.parse("2024-02-29");
        assertThat(
                        recurrence.expand(
                                rule("YEARLY", 1, null, null),
                                leap,
                                LocalDate.parse("2033-01-01"),
                                leap,
                                0))
                .containsExactly(
                        leap, LocalDate.parse("2028-02-29"), LocalDate.parse("2032-02-29"));
        LocalDate start = LocalDate.parse("2026-11-01");
        assertThat(
                        recurrence.expand(
                                rule("DAILY", 2, null, 3),
                                start,
                                LocalDate.parse("2026-12-01"),
                                start,
                                0))
                .containsExactly(start, start.plusDays(2), start.plusDays(4));
        assertThat(
                        recurrence.expand(
                                rule("DAILY", 1, start.plusDays(2), null),
                                start,
                                LocalDate.parse("2026-12-01"),
                                start,
                                0))
                .hasSize(3);
        assertThat(
                        recurrence.expand(
                                rule("DAILY", 1, null, null),
                                LocalDate.parse("2026-11-10"),
                                LocalDate.parse("2026-11-11"),
                                start,
                                10))
                .contains(LocalDate.parse("2026-11-01"));
    }

    @Test
    void memberRemovalSkipsDetachedOccurrencesAndCountsCascadedOnes() {
        UUID family = UUID.randomUUID();
        resolver.resolve(token(family, "detach-" + family + "@example.com"));
        long anna = members.create(family, new MemberRequest("Anna", "coral")).id();
        long ben = members.create(family, new MemberRequest("Ben", "teal")).id();
        long shared = appointment(family, "Shared series", null, null);
        jdbc.update(
                "update appointments set recurrence_freq='DAILY', recurrence_interval=1 where id=?",
                shared);
        participant(family, shared, anna);
        participant(family, shared, ben);
        long annaOnly = appointment(family, "Anna only", shared, "2026-11-12");
        participant(family, annaOnly, anna);
        long own = appointment(family, "Own series", null, null);
        jdbc.update(
                "update appointments set recurrence_freq='DAILY', recurrence_interval=1 where id=?",
                own);
        participant(family, own, anna);
        long joined = appointment(family, "Joined", own, "2026-11-12");
        participant(family, joined, anna);
        participant(family, joined, ben);
        long sharedVersion =
                jdbc.queryForObject(
                        "select version from appointments where id=?", Long.class, shared);

        var impact = members.impact(family, anna);
        assertThat(impact.deleteCount()).isEqualTo(3);
        assertThat(impact.unlinkCount()).isEqualTo(1);
        members.remove(family, anna);

        assertThat(
                        jdbc.queryForList(
                                "select id from appointments where account_id=?",
                                Long.class,
                                family))
                .containsExactly(shared);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from appointment_exdates where series_id=?"
                                        + " and occurrence_date=?",
                                Long.class,
                                shared,
                                java.sql.Date.valueOf("2026-11-12")))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select version from appointments where id=?", Long.class, shared))
                .isEqualTo(sharedVersion + 1);
        assertThat(
                        calendar.range(
                                family,
                                BERLIN,
                                LocalDate.parse("2026-11-12"),
                                LocalDate.parse("2026-11-13"),
                                ben))
                .isEmpty();
    }

    @Test
    void rangesAreHalfOpenAndKeepZeroLengthAppointments() {
        UUID family = UUID.randomUUID();
        resolver.resolve(token(family, "bounds-" + family + "@example.com"));
        long anna = members.create(family, new MemberRequest("Anna", "coral")).id();
        calendar.create(
                family,
                BERLIN,
                new AppointmentRequest(
                        "Late",
                        null,
                        null,
                        false,
                        OffsetDateTime.parse("2026-11-08T22:00:00+01:00"),
                        OffsetDateTime.parse("2026-11-09T00:00:00+01:00"),
                        null,
                        null,
                        Set.of(anna),
                        null,
                        null));
        calendar.create(
                family,
                BERLIN,
                new AppointmentRequest(
                        "Reminder",
                        null,
                        null,
                        false,
                        OffsetDateTime.parse("2026-11-09T00:00:00+01:00"),
                        OffsetDateTime.parse("2026-11-09T00:00:00+01:00"),
                        null,
                        null,
                        Set.of(anna),
                        null,
                        null));
        assertThat(
                        calendar.range(
                                family,
                                BERLIN,
                                LocalDate.parse("2026-11-09"),
                                LocalDate.parse("2026-11-16"),
                                null))
                .extracting("title")
                .containsExactly("Reminder");
        assertThat(
                        calendar.range(
                                family,
                                BERLIN,
                                LocalDate.parse("2026-11-02"),
                                LocalDate.parse("2026-11-09"),
                                null))
                .extracting("title")
                .containsExactly("Late");
    }

    @Test
    void openEndedSeriesExpandFromTheRequestedPeriod() {
        LocalDate birthday = LocalDate.parse("1950-03-04");
        AppointmentRecord yearly =
                new AppointmentRecord(
                        1,
                        UUID.randomUUID(),
                        "Birthday",
                        null,
                        null,
                        true,
                        null,
                        null,
                        birthday,
                        birthday,
                        "YEARLY",
                        1,
                        null,
                        null,
                        null,
                        null,
                        null,
                        0,
                        null,
                        null);
        assertThat(
                        recurrence.expand(
                                yearly,
                                LocalDate.parse("2026-03-01"),
                                LocalDate.parse("2026-04-01"),
                                birthday,
                                0))
                .containsExactly(LocalDate.parse("2026-03-04"));
        LocalDate monday = LocalDate.parse("2020-01-06");
        AppointmentRecord fortnightly =
                new AppointmentRecord(
                        2,
                        UUID.randomUUID(),
                        "Training",
                        null,
                        null,
                        true,
                        null,
                        null,
                        monday,
                        monday,
                        "WEEKLY",
                        2,
                        "MON,THU",
                        null,
                        null,
                        null,
                        null,
                        0,
                        null,
                        null);
        assertThat(
                        recurrence.expand(
                                fortnightly,
                                LocalDate.parse("2026-11-02"),
                                LocalDate.parse("2026-11-23"),
                                monday,
                                0))
                .containsExactly(
                        LocalDate.parse("2026-11-02"),
                        LocalDate.parse("2026-11-05"),
                        LocalDate.parse("2026-11-16"),
                        LocalDate.parse("2026-11-19"));
        assertThat(recurrence.occursOn(fortnightly, monday, LocalDate.parse("2026-11-05")))
                .isTrue();
        assertThat(recurrence.occursOn(fortnightly, monday, LocalDate.parse("2026-11-12")))
                .isFalse();
    }

    private AppointmentRecord rule(String frequency, int interval, LocalDate until, Integer count) {
        return new AppointmentRecord(
                1,
                UUID.randomUUID(),
                "Rule",
                null,
                null,
                true,
                null,
                null,
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-01-01"),
                frequency,
                interval,
                null,
                until,
                count,
                null,
                null,
                0,
                null,
                null);
    }

    private long appointment(UUID family, String title, Long series, String originalDate) {
        return jdbc.queryForObject(
                "insert into appointments(account_id,title,all_day,start_date,end_date,version,series_id,original_date)"
                        + " values (?,?,true,?,?,0,?,?) returning id",
                Long.class,
                family,
                title,
                java.sql.Date.valueOf("2026-11-10"),
                java.sql.Date.valueOf("2026-11-10"),
                series,
                originalDate == null ? null : java.sql.Date.valueOf(originalDate));
    }

    private void participant(UUID family, long appointment, long member) {
        jdbc.update(
                "insert into appointment_participants(appointment_id,member_id,account_id) values (?,?,?)",
                appointment,
                member,
                family);
    }

    private Jwt token(UUID id, String email) {
        return new Jwt(
                "test-token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256", "kid", "test"),
                Map.of(
                        "sub",
                        id.toString(),
                        "email",
                        email,
                        "iss",
                        "homebuddy-auth",
                        "aud",
                        List.of("calendarbuddy")));
    }
}
