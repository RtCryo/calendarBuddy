package com.calendarbuddy.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** HTTP-level tests of series scopes, authentication, account isolation, and rollback. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AppointmentApiTest.TokenConfig.class)
@Testcontainers
class AppointmentApiTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    /**
     * Accepts tokens of the form {@code uuid} and rejects everything else, so no JWKS is needed.
     */
    @TestConfiguration
    static class TokenConfig {
        @Bean
        @Primary
        JwtDecoder testDecoder() {
            return token -> {
                try {
                    UUID id = UUID.fromString(token);
                    return new Jwt(
                            token,
                            Instant.now(),
                            Instant.now().plusSeconds(3600),
                            Map.of("alg", "RS256"),
                            Map.of(
                                    "sub",
                                    id.toString(),
                                    "email",
                                    id + "@example.com",
                                    "iss",
                                    "homebuddy-auth",
                                    "aud",
                                    List.of("calendarbuddy")));
                } catch (IllegalArgumentException invalid) {
                    throw new BadJwtException("invalid token");
                }
            };
        }
    }

    @Value("${local.server.port}")
    int port;

    @Autowired JdbcTemplate jdbc;
    private final HttpClient http = HttpClient.newHttpClient();

    private record Reply(int status, String body) {
        long number(String field) {
            Matcher match = Pattern.compile("\"" + field + "\":(\\d+)").matcher(body);
            assertThat(match.find()).as(field + " in " + body).isTrue();
            return Long.parseLong(match.group(1));
        }

        int count(String text) {
            return body.split(Pattern.quote(text), -1).length - 1;
        }
    }

    private Reply call(String method, String path, UUID account, String json) throws Exception {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Content-Type", "application/json")
                        .method(
                                method,
                                json == null
                                        ? HttpRequest.BodyPublishers.noBody()
                                        : HttpRequest.BodyPublishers.ofString(json));
        if (account != null) request.header("Authorization", "Bearer " + account);
        HttpResponse<String> response =
                http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return new Reply(response.statusCode(), response.body());
    }

    private long member(UUID account, String name) throws Exception {
        return call(
                        "POST",
                        "/api/family-members",
                        account,
                        "{\"name\":\"" + name + "\",\"color\":\"coral\"}")
                .number("id");
    }

    private static String body(
            String title, int hour, long member, String recurrence, Long version) {
        return body("2026-11-03", title, hour, member, recurrence, version);
    }

    private static String body(
            String day, String title, int hour, long member, String recurrence, Long version) {
        String start = "%sT%02d:00:00+01:00".formatted(day, hour);
        String end = "%sT%02d:00:00+01:00".formatted(day, hour + 1);
        return "{\"title\":\"%s\",\"allDay\":false,\"startAt\":\"%s\",\"endAt\":\"%s\",\"participantIds\":[%d],\"recurrence\":%s,\"version\":%s}"
                .formatted(title, start, end, member, recurrence, version);
    }

    private static final String WEEKLY =
            "{\"frequency\":\"WEEKLY\",\"interval\":1,\"weekdays\":[\"TUESDAY\"],\"until\":null,\"count\":4}";
    private static final String RANGE = "/api/appointments?from=2026-11-01&to=2026-12-01";

    private long series(UUID account, long member) throws Exception {
        Reply created =
                call(
                        "POST",
                        "/api/appointments",
                        account,
                        body("Swimming", 16, member, WEEKLY, 0L));
        assertThat(created.status()).isEqualTo(201);
        return created.number("appointmentId");
    }

    private long count(String table, long seriesId) {
        return jdbc.queryForObject(
                "select count(*) from " + table + " where series_id=?", Long.class, seriesId);
    }

    @Test
    void rejectsMissingAndInvalidTokens() throws Exception {
        assertThat(call("GET", RANGE, null, null).status()).isEqualTo(401);
        assertThat(call("GET", RANGE, UUID.randomUUID(), null).status()).isEqualTo(200);
        HttpResponse<String> invalid =
                http.send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + RANGE))
                                .header("Authorization", "Bearer not-a-token")
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertThat(invalid.statusCode()).isEqualTo(401);
    }

    @Test
    void reportsTheAccountTimeZone() throws Exception {
        UUID account = UUID.randomUUID();
        assertThat(call("GET", "/api/account", null, null).status()).isEqualTo(401);
        Reply reply = call("GET", "/api/account", account, null);
        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.body())
                .contains("\"timeZone\":\"Europe/Berlin\"")
                .contains(account + "@example.com");
    }

    @Test
    void thisOccurrenceEditAndDeleteUseExceptionRowsAndExdates() throws Exception {
        UUID account = UUID.randomUUID();
        long ben = member(account, "Ben");
        long id = series(account, ben);
        assertThat(call("GET", RANGE, account, null).count("\"title\":\"Swimming\"")).isEqualTo(4);

        Reply missingScope =
                call(
                        "PUT",
                        "/api/appointments/" + id + "?occurrenceDate=2026-11-10",
                        account,
                        body("2026-11-10", "Swimming", 17, ben, "null", 0L));
        assertThat(missingScope.status()).isEqualTo(400);

        Reply moved =
                call(
                        "PUT",
                        "/api/appointments/" + id + "?scope=this&occurrenceDate=2026-11-10",
                        account,
                        body("2026-11-10", "Swimming", 17, ben, "null", 0L));
        assertThat(moved.status()).isEqualTo(200);
        assertThat(count("appointments", id)).isEqualTo(1);
        Reply listed = call("GET", RANGE, account, null);
        assertThat(listed.count("\"title\":\"Swimming\"")).isEqualTo(4);
        assertThat(listed.body())
                .contains("2026-11-10T16:00:00Z")
                .doesNotContain("2026-11-10T15:00:00Z");

        long seriesVersion =
                call("GET", "/api/appointments/" + id, account, null).number("version");
        Reply skipped =
                call(
                        "DELETE",
                        "/api/appointments/"
                                + id
                                + "?scope=this&occurrenceDate=2026-11-17&version="
                                + seriesVersion,
                        account,
                        null);
        assertThat(skipped.status()).isEqualTo(204);
        assertThat(count("appointment_exdates", id)).isEqualTo(1);
        assertThat(call("GET", RANGE, account, null).count("\"title\":\"Swimming\"")).isEqualTo(3);

        Reply stale =
                call(
                        "DELETE",
                        "/api/appointments/"
                                + id
                                + "?scope=this&occurrenceDate=2026-11-24&version="
                                + (seriesVersion - 1),
                        account,
                        null);
        assertThat(stale.status()).isEqualTo(409);
        assertThat(count("appointment_exdates", id)).isEqualTo(1);
    }

    @Test
    void allOccurrencesKeepExceptionsForNonScheduleChangesAndResetThemForScheduleChanges()
            throws Exception {
        UUID account = UUID.randomUUID();
        long ben = member(account, "Ben");
        long id = series(account, ben);
        call(
                "PUT",
                "/api/appointments/" + id + "?scope=this&occurrenceDate=2026-11-10",
                account,
                body("2026-11-10", "Swimming", 17, ben, "null", 0L));
        long version = call("GET", "/api/appointments/" + id, account, null).number("version");
        call(
                "DELETE",
                "/api/appointments/"
                        + id
                        + "?scope=this&occurrenceDate=2026-11-17&version="
                        + version,
                account,
                null);
        version = call("GET", "/api/appointments/" + id, account, null).number("version");

        Reply renamed =
                call(
                        "PUT",
                        "/api/appointments/" + id + "?scope=all",
                        account,
                        body("Swim training", 16, ben, WEEKLY, version));
        assertThat(renamed.status()).isEqualTo(200);
        assertThat(count("appointments", id)).isEqualTo(1);
        assertThat(count("appointment_exdates", id)).isEqualTo(1);
        Reply listed = call("GET", RANGE, account, null);
        assertThat(listed.count("\"title\":\"Swim training\"")).isEqualTo(2);
        assertThat(listed.count("\"title\":\"Swimming\"")).isEqualTo(1);

        version = call("GET", "/api/appointments/" + id, account, null).number("version");
        Reply rescheduled =
                call(
                        "PUT",
                        "/api/appointments/" + id + "?scope=all",
                        account,
                        body("Swim training", 15, ben, WEEKLY, version));
        assertThat(rescheduled.status()).isEqualTo(200);
        assertThat(count("appointments", id)).isZero();
        assertThat(count("appointment_exdates", id)).isZero();
        assertThat(call("GET", RANGE, account, null).count("\"title\":\"Swim training\""))
                .isEqualTo(4);

        version = call("GET", "/api/appointments/" + id, account, null).number("version");
        call(
                "PUT",
                "/api/appointments/" + id + "?scope=this&occurrenceDate=2026-11-10",
                account,
                body("2026-11-10", "One off", 9, ben, "null", version));
        version = call("GET", "/api/appointments/" + id, account, null).number("version");
        assertThat(
                        call(
                                        "DELETE",
                                        "/api/appointments/" + id + "?scope=all&version=" + version,
                                        account,
                                        null)
                                .status())
                .isEqualTo(204);
        assertThat(count("appointments", id)).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from appointments where id=?", Long.class, id))
                .isZero();
        assertThat(call("GET", RANGE, account, null).body()).isEqualTo("[]");
    }

    @Test
    void seriesOfAnotherAccountIsNotFound() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID intruder = UUID.randomUUID();
        long ben = member(owner, "Ben");
        long id = series(owner, ben);
        long own = member(intruder, "Eve");
        assertThat(call("GET", "/api/appointments/" + id, intruder, null).status()).isEqualTo(404);
        assertThat(
                        call(
                                        "PUT",
                                        "/api/appointments/" + id + "?scope=all",
                                        intruder,
                                        body("Hijack", 9, own, WEEKLY, 0L))
                                .status())
                .isEqualTo(404);
        assertThat(
                        call(
                                        "DELETE",
                                        "/api/appointments/" + id + "?scope=all&version=0",
                                        intruder,
                                        null)
                                .status())
                .isEqualTo(404);
        assertThat(call("GET", "/api/appointments/" + id, owner, null).status()).isEqualTo(200);
    }

    @Test
    void failedScheduleChangeRollsBackTheWholeEdit() throws Exception {
        UUID account = UUID.randomUUID();
        long ben = member(account, "Ben");
        long id = series(account, ben);
        call(
                "PUT",
                "/api/appointments/" + id + "?scope=this&occurrenceDate=2026-11-10",
                account,
                body("2026-11-10", "Swimming", 17, ben, "null", 0L));
        long version = call("GET", "/api/appointments/" + id, account, null).number("version");
        jdbc.execute(
                "create function reject_exception_delete() returns trigger language plpgsql as $$"
                        + " begin if old.series_id is not null then raise exception 'blocked'; end if; return old; end $$");
        jdbc.execute(
                "create trigger reject_exception_delete before delete on appointments"
                        + " for each row execute function reject_exception_delete()");
        try {
            Reply failed =
                    call(
                            "PUT",
                            "/api/appointments/" + id + "?scope=all",
                            account,
                            body("Renamed", 15, ben, WEEKLY, version));
            assertThat(failed.status()).isGreaterThanOrEqualTo(500);
        } finally {
            jdbc.execute("drop trigger reject_exception_delete on appointments");
            jdbc.execute("drop function reject_exception_delete()");
        }
        assertThat(
                        jdbc.queryForObject(
                                "select title from appointments where id=?", String.class, id))
                .isEqualTo("Swimming");
        assertThat(
                        jdbc.queryForObject(
                                "select version from appointments where id=?", Long.class, id))
                .isEqualTo(version);
        assertThat(
                        jdbc.queryForObject(
                                "select extract(hour from start_at at time zone 'Europe/Berlin') from appointments where id=?",
                                Integer.class,
                                id))
                .isEqualTo(16);
        assertThat(count("appointments", id)).isEqualTo(1);
    }
}
