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

`SecurityConfig.jwtDecoder` takes Spring Boot's auto-configured `RestTemplateBuilder` and passes `builder.build()` to `NimbusJwtDecoder.withJwkSetUri(...).restOperations(...)`. The builder carries the observation registry, so the JWKS fetch is a client span (`GET`, `http://auth:8080/.well-known/jwks.json`) that propagates `traceparent`. The decoder caches the key set, so the span appears only on the first validation after start and after a key rotation. Issuer, audience, algorithm, and clock-skew rules are unchanged.

### Actuator requests are not exported

A `SpanExportingPredicate` bean drops server spans whose `uri` tag starts with `/actuator`, so health checks and scrapes create no traces while the HTTP metrics stay unchanged.

### Logs and privacy

Spring Boot's default console pattern includes `[<traceId>-<spanId>]` with tracing on the classpath; calendarBuddy keeps it. Server span names are `<METHOD> <route template>` (for example `GET /api/appointments/{id}`); Spring records no headers, query parameters, or bodies, and calendarBuddy adds no custom span attributes.

## Risks / Trade-offs

- [`RestTemplateBuilder` lives in Boot 4's REST client module] → Add `spring-boot-starter-restclient` if `spring-boot-starter-web` does not bring it; covered by a test that the decoder's client is observed.
- [A JWKS outage during a request now shows as a failed client span] → Intended: it makes the failure visible in the request trace.
