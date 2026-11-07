package com.calendarbuddy.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
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
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Metrics endpoint, business counters, and bounded labels. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.defaults.metrics.export.enabled=true",
        "management.prometheus.metrics.export.enabled=true"})
@Import(CalendarMetricsTest.TokenConfig.class)
@Testcontainers
class CalendarMetricsTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    /** Accepts tokens of the form {@code uuid} and rejects everything else, so no JWKS is needed. */
    @TestConfiguration
    static class TokenConfig {
        @Bean @Primary
        JwtDecoder testDecoder() {
            return token -> {
                try {
                    UUID id = UUID.fromString(token);
                    return new Jwt(token, Instant.now(), Instant.now().plusSeconds(3600), Map.of("alg", "RS256"),
                            Map.of("sub", id.toString(), "email", id + "@example.com", "iss", "homebuddy-auth",
                                    "aud", List.of("calendarbuddy")));
                } catch (IllegalArgumentException invalid) {
                    throw new BadJwtException("invalid token");
                }
            };
        }
    }

    @Value("${local.server.port}") int port;
    @Autowired MeterRegistry registry;
    private final HttpClient http = HttpClient.newHttpClient();

    private record Reply(int status, String body) {
        long number(String field) {
            Matcher match = Pattern.compile("\"" + field + "\":(\\d+)").matcher(body);
            assertThat(match.find()).as(field + " in " + body).isTrue();
            return Long.parseLong(match.group(1));
        }
    }

    private Reply call(String method, String path, UUID account, String json) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json));
        if (account != null) request.header("Authorization", "Bearer " + account);
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return new Reply(response.statusCode(), response.body());
    }

    private double counter(String name, String operation, String outcome) {
        var counter = registry.find(name).tag("operation", operation).tag("outcome", outcome).counter();
        return counter == null ? 0 : counter.count();
    }

    private static String appointment(long member, Long version, String recurrence) {
        return "{\"title\":\"Swimming\",\"allDay\":false,\"startAt\":\"2026-11-03T16:00:00+01:00\","
                + "\"endAt\":\"2026-11-03T17:00:00+01:00\",\"participantIds\":[%d],\"recurrence\":%s,\"version\":%s}"
                .formatted(member, recurrence, version);
    }

    private static final String WEEKLY =
            "{\"frequency\":\"WEEKLY\",\"interval\":1,\"weekdays\":[\"TUESDAY\"],\"until\":null,\"count\":4}";

    @Test
    void operationsAreCountedByOperationAndOutcome() throws Exception {
        UUID account = UUID.randomUUID();
        String memberOk = "appointment.operations";
        double created = counter("calendarbuddy.family.member.operations", "create", "success");
        double duplicate = counter("calendarbuddy.family.member.operations", "create", "conflict");
        double apptCreated = counter("calendarbuddy." + memberOk, "create", "success");
        double apptConflict = counter("calendarbuddy." + memberOk, "update", "conflict");
        double apptDeleted = counter("calendarbuddy." + memberOk, "delete", "success");

        String member = "{\"name\":\"Mia\",\"color\":\"coral\"}";
        long memberId = call("POST", "/api/family-members", account, member).number("id");
        assertThat(call("POST", "/api/family-members", account, member).status()).isEqualTo(409);
        Reply made = call("POST", "/api/appointments", account, appointment(memberId, 0L, "null"));
        assertThat(made.status()).isEqualTo(201);
        long id = made.number("appointmentId");
        assertThat(call("PUT", "/api/appointments/" + id, account, appointment(memberId, 99L, "null")).status())
                .isEqualTo(409);
        long version = made.number("version");
        assertThat(call("DELETE", "/api/appointments/" + id + "?version=" + version, account, null).status())
                .isEqualTo(204);

        assertThat(counter("calendarbuddy.family.member.operations", "create", "success")).isEqualTo(created + 1);
        assertThat(counter("calendarbuddy.family.member.operations", "create", "conflict")).isEqualTo(duplicate + 1);
        assertThat(counter("calendarbuddy." + memberOk, "create", "success")).isEqualTo(apptCreated + 1);
        assertThat(counter("calendarbuddy." + memberOk, "update", "conflict")).isEqualTo(apptConflict + 1);
        assertThat(counter("calendarbuddy." + memberOk, "delete", "success")).isEqualTo(apptDeleted + 1);
    }

    @Test
    void recurrenceExpansionIsTimedPerRange() throws Exception {
        UUID account = UUID.randomUUID();
        long memberId = call("POST", "/api/family-members", account, "{\"name\":\"Leo\",\"color\":\"teal\"}")
                .number("id");
        assertThat(call("POST", "/api/appointments", account, appointment(memberId, 0L, WEEKLY)).status())
                .isEqualTo(201);
        var timer = registry.find("calendarbuddy.recurrence.expansion").timer();
        long before = timer == null ? 0 : timer.count();

        assertThat(call("GET", "/api/appointments?from=2026-11-01&to=2026-12-01", account, null).status())
                .isEqualTo(200);

        assertThat(registry.get("calendarbuddy.recurrence.expansion").timer().count()).isEqualTo(before + 1);
    }

    @Test
    void totalsAreGaugesAndLabelsStayBounded() throws Exception {
        assertThat(registry.get("calendarbuddy.appointments").gauge().value()).isGreaterThanOrEqualTo(0);
        assertThat(registry.get("calendarbuddy.family.members").gauge().value()).isGreaterThanOrEqualTo(0);
        for (Meter meter : registry.getMeters()) {
            if (meter.getId().getName().startsWith("calendarbuddy.")) {
                assertThat(meter.getId().getTags()).allSatisfy(
                        tag -> assertThat(tag.getKey()).isIn("operation", "outcome"));
            }
        }
    }

    @Test
    void prometheusEndpointIsPublicAndOtherActuatorPathsRequireAToken() throws Exception {
        UUID account = UUID.randomUUID();
        call("POST", "/api/family-members", account, "{\"name\":\"Zoe\",\"color\":\"rose\"}");

        Reply scrape = call("GET", "/actuator/prometheus", null, null);

        assertThat(scrape.status()).isEqualTo(200);
        assertThat(scrape.body()).contains("calendarbuddy_family_member_operations_total", "http_server_requests",
                "jvm_memory_used_bytes");
        assertThat(scrape.body()).doesNotContain(account.toString()).doesNotContain("Zoe");
        assertThat(call("GET", "/actuator/env", null, null).status()).isEqualTo(401);
        assertThat(call("GET", "/actuator/metrics", null, null).status()).isEqualTo(401);
    }
}
