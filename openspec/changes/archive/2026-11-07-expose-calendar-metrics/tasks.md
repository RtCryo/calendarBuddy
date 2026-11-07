# Tasks

## 1. Metrics

- [x] 1.1 Add `micrometer-registry-prometheus` and permit `GET /actuator/prometheus` in `SecurityConfig`. Verify with a test that the endpoint returns Prometheus text without a token and that other actuator paths still require authentication.
- [x] 1.2 Add the metrics component and record `calendarbuddy_appointment_operations_total` and `calendarbuddy_family_member_operations_total` for create, update, and delete with outcomes `success`, `conflict`, and `invalid`. Verify with tests for every operation and outcome.
- [x] 1.3 Add the gauges `calendarbuddy_appointments` and `calendarbuddy_family_members` with cached cross-account counts, and the `calendarbuddy_recurrence_expansion_seconds` timer around range expansion. Verify with tests, and that no label holds an account id, member, title, or date.

## 2. Compose and documentation

- [x] 2.1 Join `calendar-api` to the external `calendarbuddy-metrics` network in `docker-compose.yml`. Verify with `docker compose config` and that startup without the network fails naming it.
- [x] 2.2 Update `README.md` and `docs/ARCHITECTURE.md` with the metrics endpoint, metric names, and the `docker network create --internal calendarbuddy-metrics` command.

## 3. Verification

- [x] 3.1 With the root stack running, verify that `calendarbuddy-calendar-api` is up in the central Prometheus, that creating and deleting an appointment changes the operation counters and totals, and that the calendarBuddy dashboard shows them.
- [x] 3.2 Verify that `/actuator/prometheus` is not served through `localhost:3010`, including `/api/../actuator/prometheus`, and that no metric label holds personal data after several accounts used the calendar.
