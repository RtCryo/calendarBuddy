# Design

## Context

See proposal.md. `calendar-api` exposes `health,info,prometheus` in `application.yml` but has no registry, and its security chain permits only `/actuator/health` and `/error` without a token. The UI proxies only `/api/auth/` and `/api/`, so actuator paths are not reachable from browsers. The root design is in [`add-homebuddy-monitoring`](https://github.com/RtCryo/homeBuddy/blob/main/openspec/changes/add-homebuddy-monitoring/design.md).

## Decisions

- **Micrometer Prometheus registry on the main port.** Add `micrometer-registry-prometheus`, keep the exposure list, and permit `GET /actuator/prometheus` without a token. The metrics carry only bounded aggregates, and a separate management port would not change network reachability.
- **Count in the controllers.** `AppointmentController` and `FamilyMemberController` record one counter increment per create, update, and delete with the outcome: `success`, `conflict` (version conflict), or `invalid` (a rejected request). A small metrics component owns the counters so the label sets are fixed and created once.
- **Totals as gauges.** `calendarbuddy_appointments` and `calendarbuddy_family_members` read a cheap count over all accounts. They are cached for the scrape interval, so scraping cannot load the database. They count rows only; no account is distinguishable.
- **Recurrence timer.** Time `RecurrenceEngine` expansion per requested range in `AppointmentService.range` with a Micrometer timer, without labels.
- **No identifiers in labels.** Only `operation` and `outcome` are business labels. HTTP metrics keep Micrometer's default URI-template labels.
- **Metrics network.** `calendar-api` joins `calendarbuddy-metrics` (external, `--internal`), declared like the identity network, next to the default network and `calendarbuddy-identity`.

## Risks / Trade-offs

- The totals are cross-account counts; they say nothing about individual accounts, which is acceptable for operations.
- Renaming a metric requires updating the root dashboard in an umbrella change.

## Migration Plan

Create `calendarbuddy-metrics` on every host, then deploy. Rollback by removing the network entry; nothing else depends on it.
