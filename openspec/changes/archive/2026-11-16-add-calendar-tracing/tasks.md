# Tasks

## 1. Tracing in calendar-api

- [x] 1.1 Add `spring-boot-starter-opentelemetry` and the `application.yml` settings from design.md (sampling 1.0, resource attributes, OTLP metrics and log export off, no export endpoint). Verify with a test that no OTLP meter registry and no span exporter is active without the endpoint property, and that `./mvnw verify` passes.
- [x] 1.2 Build the `JwtDecoder` with a `RestTemplate` that uses the application's `ObservationRegistry`. Verify with a test that the JWKS request carries a `traceparent` header when made inside an observation, and that the existing token validation tests pass.
- [x] 1.3 Add the `SpanExportingPredicate` that drops server spans of `/actuator/**` and the `ObservationPredicate` that skips Spring Security observations inside them. Verify with a test that a health check exports no span and that every exported trace has its server span.
- [x] 1.4 Verify with a test that a log line written inside a traced request contains `[<traceId>-<spanId>]`.
- [x] 1.5 Log each appointment and family member change with its operation and outcome only. Verify with a test that adding a member in a traced request logs `Family member create succeeded` with the caller's trace id and without the member's name.

## 2. Compose and documentation

- [x] 2.1 Set `MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT` and `MANAGEMENT_TRACING_SAMPLING_PROBABILITY=${CALENDAR_API_TRACING_SAMPLING_PROBABILITY:-1.0}` on `calendar-api` in `docker-compose.yml`, document the variable in `.env.example`. Verify with `docker compose config`.
- [x] 2.2 Update `docs/ARCHITECTURE.md` (tracing settings, JWKS client span, link to root `homebuddy-tracing`) and add tracing to `docs/README.md`.

## 3. Verification

- [x] 3.1 With the root stack running, create an appointment and verify the trace in Tempo (`service.name = "calendarbuddy-calendar-api"`, span name from the route template), and after a restart of `calendar-api` a JWKS client span to authBuddy in the first authenticated request's trace.
- [x] 3.2 Set `CALENDAR_API_TRACING_SAMPLING_PROBABILITY=0`, recreate `calendar-api`, and verify that its requests produce no traces; restore the default.
