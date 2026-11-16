# Proposal

## Why

The root change [`add-homebuddy-tracing`](https://github.com/RtCryo/homeBuddy/blob/main/openspec/changes/add-homebuddy-tracing/proposal.md) adds a central Tempo trace store behind the homeBuddy telemetry collector. `calendar-api` records no traces today: a slow calendar view or a failed token validation (including the JWKS fetch from authBuddy) cannot be followed as one request, and its log lines cannot be matched to a request.

## What Changes

- `calendar-api` records a trace for every incoming HTTP request and exports it with OTLP over HTTP to the collector, following the root capability `homebuddy-tracing`. It does not restate that contract.
- Resource attributes: `service.name = calendarbuddy-calendar-api`, `service.namespace = calendarbuddy`.
- Traced outgoing call: the JWKS fetch from authBuddy. The `JwtDecoder` is built with a `RestTemplate` that uses the application's observation registry, so the fetch is a client span inside the request trace and sends `traceparent` to authBuddy.
- Every log line written inside a trace keeps Spring Boot's default console pattern and carries `[<traceId>-<spanId>]`; calendarBuddy sets no own `logging.pattern.console`.
- Sampling probability 1.0 by default; operators lower it with `CALENDAR_API_TRACING_SAMPLING_PROBABILITY` in `.env`.
- Health-check and scrape requests to `/actuator/**` are not exported as traces, including Spring Security's spans inside them.
- Spans carry no tokens, e-mail addresses, appointment titles, notes, or request bodies; span names use route templates. OTLP metrics and log export stay off.
- The export endpoint is set only in `docker-compose.yml`; an unreachable collector never affects the API.

## Capabilities

### New Capabilities

- `calendar-tracing`: Which requests and calls `calendar-api` traces, its resource attributes, sampling, privacy of its spans, and the trace id in its logs.

### Modified Capabilities

- None. `calendar-api` already joins `calendarbuddy-metrics`, which is also the path to the collector, and token validation rules in `identity-integration` do not change.

## Non-goals

- Browser tracing in the UI and tracing in the UI's nginx proxy.
- JDBC spans.

## Impact

- `calendar-api/pom.xml` (`spring-boot-starter-opentelemetry`), `application.yml`, `SecurityConfig` (JWKS client), filters that keep `/actuator/**` out of the traces, tests.
- `docker-compose.yml` (OTLP endpoint and sampling environment), `.env.example`, `docs/ARCHITECTURE.md`, `docs/README.md`.
- No new network, port, or secret.
