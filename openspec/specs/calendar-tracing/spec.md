# calendar-tracing Specification

## Purpose
Defines how `calendar-api` takes part in homeBuddy tracing. The trace pipeline, naming and propagation convention, retention, and privacy baseline are the root capability `homebuddy-tracing`; this capability adds only calendarBuddy specifics.

## Requirements

### Requirement: calendar-api exports traces of its requests
`calendar-api` SHALL record a server span for every incoming HTTP request other than `/actuator/**` and SHALL export it to the homeBuddy telemetry collector as defined by the root capability `homebuddy-tracing`, with the resource attributes `service.name = calendarbuddy-calendar-api` and `service.namespace = calendarbuddy`. Server span names SHALL use the route template. The export endpoint SHALL be configured by the deployment only, so that `calendar-api` exports nothing when it is not set.

#### Scenario: Create an appointment
- **WHEN** a signed-in user creates an appointment
- **THEN** a trace with a `POST` server span of `calendarbuddy-calendar-api` named after the route template is stored in the central trace store

#### Scenario: Many appointments read by id
- **WHEN** many different appointments are read by their identifier
- **THEN** their server spans share one name built from the route template

#### Scenario: Health checks and scrapes
- **WHEN** the health check or the central Prometheus calls `/actuator/**`
- **THEN** no span of that request is exported, including spans of filters that run inside it

#### Scenario: Collector unavailable
- **WHEN** the collector cannot be reached
- **THEN** calendar requests succeed with unchanged latency, and `calendar-api` only logs that spans could not be exported

### Requirement: The JWKS fetch is part of the request trace
`calendar-api` SHALL fetch authBuddy's JWKS through an observed HTTP client, so that the fetch is a client span in the trace of the request that triggered it and carries W3C trace context to authBuddy.

#### Scenario: First authenticated request after a restart
- **WHEN** `calendar-api` restarts and then handles its first authenticated request
- **THEN** that request's trace contains a client span for `http://auth:8080/.well-known/jwks.json` and authBuddy's server span for it

### Requirement: calendar-api samples every request by default
`calendar-api` SHALL sample every trace it starts by default, SHALL let operators lower the probability with `CALENDAR_API_TRACING_SAMPLING_PROBABILITY` without rebuilding, and SHALL follow the sampling decision of an incoming trace context.

#### Scenario: Turn sampling off
- **WHEN** an operator sets `CALENDAR_API_TRACING_SAMPLING_PROBABILITY=0` and recreates `calendar-api`
- **THEN** its requests produce no traces

### Requirement: calendar-api spans hold no credentials or personal data
Spans of `calendar-api` SHALL NOT contain tokens, `Authorization` or cookie headers, request or response bodies, e-mail addresses, or appointment and member content such as titles, notes, or names.

#### Scenario: Search traces for credentials
- **WHEN** an operator calls the API with a known token and searches the stored spans for the token, an `eyJ` prefix, and the account's e-mail address
- **THEN** none is found

### Requirement: calendar-api log lines carry the trace id
Every log line `calendar-api` writes while handling a traced request SHALL contain `[<traceId>-<spanId>]` with the 32- and 16-character lowercase hexadecimal ids of the current span. Every create, update, and delete of an appointment or family member SHALL write one log line with the operation and its outcome (`succeeded`, or `rejected` with `conflict` or `invalid`) and no title, note, name, date, or identifier.

#### Scenario: Log line inside a request
- **WHEN** `calendar-api` logs while handling a traced request
- **THEN** the log line contains the trace id of that request's trace

#### Scenario: From a span to its log lines
- **WHEN** a user adds a family member and an operator opens the logs link of that request's span
- **THEN** the log line `Family member create succeeded` of that request is shown, and it does not contain the member's name
