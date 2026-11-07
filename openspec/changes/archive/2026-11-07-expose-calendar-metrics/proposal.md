# Proposal

## Why

The root change [`add-homebuddy-monitoring`](https://github.com/RtCryo/homeBuddy/blob/main/openspec/changes/add-homebuddy-monitoring/proposal.md) adds one central Prometheus and Grafana for every homeBuddy service. `calendar-api` lists `prometheus` in its actuator exposure but has no Prometheus registry, its security rules permit only the health endpoints, and it records no business metrics, so calendarBuddy is invisible in monitoring.

## What Changes

- `calendar-api` adds the Micrometer Prometheus registry and serves `/actuator/prometheus` with basic service metrics (availability, uptime, HTTP rate, latency and errors, JVM) and these business metrics with bounded labels:
  - `calendarbuddy_appointment_operations_total{operation,outcome}` with `operation` in `create`, `update`, `delete` and `outcome` in `success`, `conflict`, `invalid`;
  - `calendarbuddy_family_member_operations_total{operation,outcome}` with the same sets;
  - `calendarbuddy_appointments` and `calendarbuddy_family_members`: gauges of the stored totals across all accounts;
  - `calendarbuddy_recurrence_expansion_seconds`: a timer for the duration of expanding recurring appointments for a requested range.
- The security rules permit `GET /actuator/prometheus` without authentication. It stays unreachable through the calendarBuddy UI proxy, which forwards only `/api/auth/` and `/api/` paths.
- `calendar-api` joins the external, internal-only network `calendarbuddy-metrics`, so the central Prometheus can scrape `calendarbuddy-calendar-api:8080` while the API stays unreachable from other stacks.
- Documentation lists the `docker network create --internal calendarbuddy-metrics` command.

The monitoring contract (networks, scrape and label conventions, Grafana portal) is the root capability `homebuddy-monitoring`; this change does not restate it.

## Capabilities

### New Capabilities

- `calendar-metrics`: The metrics `calendar-api` exposes and how they stay bounded and private.

### Modified Capabilities

- `compose-deployment`: `calendar-api` also joins `calendarbuddy-metrics`.

## Non-goals

- Dashboards, scrape configuration, and Grafana, which live in the root `monitoring/` folder.
- Per-account, per-member, or per-appointment metrics; appointment titles or times in labels.
- Alerting.

## Impact

- `calendar-api/pom.xml` (Micrometer Prometheus registry), `application.yml`, `SecurityConfig`, `AppointmentService`, `FamilyMemberService`, new metrics code, tests.
- `docker-compose.yml`, `README.md`, `docs/ARCHITECTURE.md`.
- Hosts must create `calendarbuddy-metrics` before starting calendarBuddy.
