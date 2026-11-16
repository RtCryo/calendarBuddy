# Design

## Context

`calendar-api` is Spring Boot 4.1.2 on Java 25 with Actuator and the Prometheus registry. It validates homeBuddy tokens with a `NimbusJwtDecoder` built in `SecurityConfig` from `NimbusJwtDecoder.withJwkSetUri(...)`, which uses its own, unobserved `RestTemplate`. It joins `calendarbuddy-identity` (JWKS) and `calendarbuddy-metrics`, on which the root collector `homebuddy-alloy` receives OTLP on port 4318 (root capability `homebuddy-tracing`). It sets no log pattern.

## Decisions

### Spring Boot's OpenTelemetry starter

Same configuration as every homeBuddy component:

- `spring-boot-starter-opentelemetry`.
- `management.tracing.sampling.probability: 1.0` (Boot's default is 0.1); the default sampler is parent-based.
- `management.opentelemetry.resource-attributes`: `service.name: calendarbuddy-calendar-api`, `service.namespace: calendarbuddy`.
- `management.otlp.metrics.export.enabled: false` and `management.logging.export.otlp.enabled: false`, because the starter also brings the OTLP meter registry.
- No export endpoint in `application.yml`; Boot creates the span exporter only when `management.opentelemetry.tracing.export.otlp.endpoint` is set. `docker-compose.yml` sets `MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT=http://homebuddy-alloy:4318/v1/traces` and `MANAGEMENT_TRACING_SAMPLING_PROBABILITY=${CALENDAR_API_TRACING_SAMPLING_PROBABILITY:-1.0}`.

The batch span processor's bounded queue and export timeout keep request threads independent of the collector.

### Observed JWKS client

`SecurityConfig.jwtDecoder` creates a `RestTemplate`, sets the application's `ObservationRegistry` on it, and passes it to `NimbusJwtDecoder.withJwkSetUri(...).restOperations(...)`. The observed client makes the JWKS fetch a client span (`GET`, `http://auth:8080/.well-known/jwks.json`) that propagates `traceparent`. The decoder caches the key set, so the span appears only on the first validation after start and after a key rotation. Issuer, audience, algorithm, and clock-skew rules are unchanged.

### Actuator requests are not exported

A `SpanExportingPredicate` bean drops server spans whose `uri` tag starts with `/actuator`, so health checks and scrapes create no traces while the HTTP metrics stay unchanged. Spring Security's observations inside such a request would otherwise be exported as orphan spans, so an `ObservationPredicate` skips `spring.security.*` observations whose parent chain leads to a server request for `/actuator/**`.

### Logs and privacy

Spring Boot's default console pattern includes `[<traceId>-<spanId>]` with tracing on the classpath; calendarBuddy keeps it. `calendar-api` wrote no log line per request, so a span's logs link would always be empty. `CalendarMetrics`, which already decides the outcome of every appointment and family member change for its counters, now also logs it: `Appointment create succeeded`, `Family member update rejected: conflict`, and so on, without any title, note, name, date, or identifier. Server span names are built from the method and route template (for example `http get /api/family-members/{id}`); Spring records no headers, query parameters, or bodies, and calendarBuddy adds no custom span attributes.

## Risks / Trade-offs

- [Boot 4's `RestTemplateBuilder` lives in a separate REST client module that the API does not use] → Set the observation registry on a plain `RestTemplate` instead of adding the module; a test checks that the JWKS request carries the request's trace context.
- [A JWKS outage during a request now shows as a failed client span] → Intended: it makes the failure visible in the request trace.
