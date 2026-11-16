package com.calendarbuddy.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Tracing of requests and of the JWKS fetch (calendar-tracing, root homebuddy-tracing). */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // Export immediately so the test does not wait for the batch delay.
        properties = "management.opentelemetry.tracing.export.schedule-delay=10ms")
@Import(CalendarTracingTest.ExporterConfig.class)
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CalendarTracingTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String PARENT_ID = "00f067aa0ba902b7";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    static final RSAKey KEY = generateKey();
    static final List<String> JWKS_TRACEPARENTS = new CopyOnWriteArrayList<>();
    static final HttpServer JWKS = startJwksServer();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add(
                "app.auth.jwks-uri",
                () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks");
    }

    @AfterAll
    static void stopJwks() {
        JWKS.stop(0);
    }

    @Value("${local.server.port}")
    int port;

    @Autowired ApplicationContext context;
    @Autowired Tracer tracer;
    @Autowired CollectingSpanExporter exported;

    @BeforeEach
    void clear() {
        exported.spans.clear();
    }

    @Test
    void nothingIsExportedOverOtlpWithoutAnEndpoint() {
        assertThat(context.getBeansOfType(OtlpHttpSpanExporter.class)).isEmpty();
        assertThat(context.getBeansOfType(OtlpMeterRegistry.class)).isEmpty();
    }

    // First: the decoder caches the key set, so only the first validation fetches it.
    @Test
    @Order(1)
    void authenticatedRequestContinuesTheTraceAndIncludesTheJwksFetch() throws Exception {
        UUID account = UUID.randomUUID();
        String token = token(account);

        HttpResponse<String> response = get("/api/account", token, true);

        assertThat(response.statusCode()).isEqualTo(200);
        // The first validation fetches the key set inside the request's trace.
        assertThat(JWKS_TRACEPARENTS).anyMatch(header -> header.contains(TRACE_ID));
        SpanData server = awaitSpan(TRACE_ID, SpanKind.SERVER);
        SpanData jwks = awaitSpan(TRACE_ID, SpanKind.CLIENT);
        assertThat(server.getParentSpanId()).isEqualTo(PARENT_ID);
        assertThat(server.getName()).contains("/api/account");
        assertThat(server.getResource().getAttribute(AttributeKey.stringKey("service.name")))
                .isEqualTo("calendarbuddy-calendar-api");
        assertThat(server.getResource().getAttribute(AttributeKey.stringKey("service.namespace")))
                .isEqualTo("calendarbuddy");
        assertThat(jwks.getAttributes().asMap().values())
                .anySatisfy(value -> assertThat(String.valueOf(value)).contains("/jwks"));
        // No span of the trace holds the token or the account's e-mail address.
        assertThat(spansOf(TRACE_ID))
                .allSatisfy(
                        span ->
                                assertThat(span.getAttributes().asMap().values())
                                        .allSatisfy(
                                                value ->
                                                        assertThat(String.valueOf(value))
                                                                .doesNotContain(token)
                                                                .doesNotContain("eyJ")
                                                                .doesNotContain(
                                                                        account + "@example.com")));
    }

    @Test
    void spanNamesUseTheRouteTemplate() throws Exception {
        String token = token(UUID.randomUUID());

        get("/api/family-members/" + UUID.randomUUID(), token, false);
        get("/api/family-members/" + UUID.randomUUID(), token, false);

        await().atMost(Duration.ofSeconds(10))
                .until(
                        () ->
                                exported.spans.stream()
                                                .filter(s -> s.getKind() == SpanKind.SERVER)
                                                .count()
                                        >= 2);
        assertThat(
                        exported.spans.stream()
                                .filter(s -> s.getKind() == SpanKind.SERVER)
                                .map(SpanData::getName)
                                .distinct())
                .singleElement()
                .asString()
                .doesNotMatch(".*[0-9a-f]{8}-[0-9a-f]{4}-.*");
    }

    @Test
    void actuatorRequestsAreNotExported() throws Exception {
        assertThat(get("/actuator/health", null, false).statusCode()).isEqualTo(200);
        get("/api/account", token(UUID.randomUUID()), false);

        // The later API request is exported, so the health check would have been exported by now.
        await().atMost(Duration.ofSeconds(10))
                .until(
                        () ->
                                exported.spans.stream()
                                        .anyMatch(s -> s.getName().contains("/api/account")));
        assertThat(exported.spans).noneMatch(s -> s.getName().contains("/actuator"));
        assertThat(
                        exported.spans.stream()
                                .collect(Collectors.groupingBy(SpanData::getTraceId))
                                .values())
                .allSatisfy(
                        trace ->
                                assertThat(trace)
                                        .anyMatch(span -> span.getKind() == SpanKind.SERVER));
    }

    @Test
    void memberChangesLogTheirOutcomeWithTheTraceIdAndNoContent(CapturedOutput output)
            throws Exception {
        String token = token(UUID.randomUUID());
        String name = "Grandma Trace " + UUID.randomUUID();

        HttpResponse<String> created =
                HttpClient.newHttpClient()
                        .send(
                                HttpRequest.newBuilder(
                                                URI.create(
                                                        "http://localhost:"
                                                                + port
                                                                + "/api/family-members"))
                                        .header("Authorization", "Bearer " + token)
                                        .header("Content-Type", "application/json")
                                        .header(
                                                "traceparent",
                                                "00-" + TRACE_ID + "-" + PARENT_ID + "-01")
                                        .POST(
                                                HttpRequest.BodyPublishers.ofString(
                                                        "{\"name\":\""
                                                                + name
                                                                + "\",\"color\":\"coral\"}"))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());

        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(output.getOut())
                .containsPattern(
                        Pattern.quote("[" + TRACE_ID + "-")
                                + "[0-9a-f]{16}\\].*Family member create succeeded")
                .doesNotContain(name);
    }

    @Test
    void logLinesInsideASpanCarryTheTraceAndSpanId(CapturedOutput output) {
        Span span = tracer.nextSpan().name("log-correlation").start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            LoggerFactory.getLogger(CalendarTracingTest.class).info("inside a traced request");
        } finally {
            span.end();
        }

        String correlation = "[" + span.context().traceId() + "-" + span.context().spanId() + "]";
        assertThat(output.getOut())
                .containsPattern(Pattern.quote(correlation) + ".*inside a traced request");
    }

    private HttpResponse<String> get(String path, String token, boolean withParent)
            throws Exception {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (withParent) {
            request.header("traceparent", "00-" + TRACE_ID + "-" + PARENT_ID + "-01");
        }
        return HttpClient.newHttpClient()
                .send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private List<SpanData> spansOf(String traceId) {
        return exported.spans.stream().filter(s -> s.getTraceId().equals(traceId)).toList();
    }

    private SpanData awaitSpan(String traceId, SpanKind kind) {
        await().atMost(Duration.ofSeconds(10))
                .until(() -> spansOf(traceId).stream().anyMatch(s -> s.getKind() == kind));
        return spansOf(traceId).stream().filter(s -> s.getKind() == kind).findFirst().orElseThrow();
    }

    private static String token(UUID account) throws Exception {
        JWTClaimsSet claims =
                new JWTClaimsSet.Builder()
                        .subject(account.toString())
                        .issuer("homebuddy-auth")
                        .audience("calendarbuddy")
                        .claim("email", account + "@example.com")
                        .issueTime(new Date())
                        .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                        .build();
        SignedJWT jwt =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(),
                        claims);
        jwt.sign(new RSASSASigner(KEY));
        return jwt.serialize();
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("tracing").generate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static HttpServer startJwksServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(
                    "/jwks",
                    exchange -> {
                        String traceparent = exchange.getRequestHeaders().getFirst("traceparent");
                        if (traceparent != null) {
                            JWKS_TRACEPARENTS.add(traceparent);
                        }
                        byte[] body =
                                new JWKSet(KEY.toPublicJWK())
                                        .toString()
                                        .getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, body.length);
                        try (var output = exchange.getResponseBody()) {
                            output.write(body);
                        }
                    });
            server.start();
            return server;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ExporterConfig {

        @Bean
        CollectingSpanExporter collectingSpanExporter() {
            return new CollectingSpanExporter();
        }
    }

    /** Keeps exported spans in memory instead of sending them anywhere. */
    static class CollectingSpanExporter implements SpanExporter {

        final List<SpanData> spans = new CopyOnWriteArrayList<>();

        @Override
        public CompletableResultCode export(Collection<SpanData> batch) {
            spans.addAll(batch);
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }
    }
}
