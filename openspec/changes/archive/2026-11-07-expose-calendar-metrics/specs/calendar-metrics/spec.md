# Calendar Metrics Specification

## Purpose

Defines the Prometheus metrics `calendar-api` exposes to the central homeBuddy monitoring stack.

## ADDED Requirements

### Requirement: calendar-api exposes Prometheus metrics
`calendar-api` SHALL serve Prometheus metrics at `GET /actuator/prometheus` on port 8080 without a user token, including availability, uptime, HTTP request rate, latency, and errors, and JVM memory. The endpoint SHALL NOT be reachable through the calendarBuddy UI proxy or any Traefik route, as required by the root capability `homebuddy-monitoring`.

#### Scenario: Prometheus scrapes calendar-api
- **WHEN** the central Prometheus requests `http://calendarbuddy-calendar-api:8080/actuator/prometheus`
- **THEN** it receives metrics in Prometheus format, including HTTP request and JVM metrics

#### Scenario: Metrics through the UI
- **WHEN** a browser requests `/api/../actuator/prometheus` or any `/api/` variant on the calendarBuddy origin
- **THEN** the response is not the metrics output

### Requirement: Business metrics are bounded and anonymous
`calendar-api` SHALL expose `calendarbuddy_appointment_operations_total` and `calendarbuddy_family_member_operations_total`, each with an `operation` label limited to `create`, `update`, and `delete` and an `outcome` label limited to `success`, `conflict`, and `invalid`; the gauges `calendarbuddy_appointments` and `calendarbuddy_family_members` with the stored totals across all accounts; and the timer `calendarbuddy_recurrence_expansion_seconds` for expanding recurring appointments in a requested range. No metric SHALL carry an account identifier, family member, appointment title, date, or other unbounded label value.

#### Scenario: Create and delete an appointment
- **WHEN** a user creates and then deletes an appointment
- **THEN** `calendarbuddy_appointment_operations_total{operation="create",outcome="success"}` and `{operation="delete",outcome="success"}` each increase by one, and `calendarbuddy_appointments` goes up and then back down

#### Scenario: Version conflict
- **WHEN** an update is rejected because its version is stale
- **THEN** `calendarbuddy_appointment_operations_total{operation="update",outcome="conflict"}` increases by one

#### Scenario: Recurring range request
- **WHEN** a client loads a calendar range that contains recurring appointments
- **THEN** `calendarbuddy_recurrence_expansion_seconds_count` increases

#### Scenario: Many accounts
- **WHEN** many accounts create appointments and family members
- **THEN** the number of `calendarbuddy_*` series does not grow and no series carries personal data
