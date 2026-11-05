# Proposal

## Why

homeBuddy currently offers only finanzBuddy for household finances. Families also need one shared place to plan who has which appointment and when. calendarBuddy adds a private family calendar as a second homeBuddy service: the family signs in with its homeBuddy account, maintains its members, records one-off and repeating appointments for one or more members, and sees either the whole family's schedule or one person's schedule at a glance.

## What Changes

- Create `calendarBuddy` as its own Git repository next to `finanzBuddy` with a backend (`calendar-api`), web UI (`ui`), PostgreSQL schema, Docker Compose deployment, CI, and documentation, reusing the finanzBuddy stack and conventions.
- Sign in with the shared **homeBuddy account** issued by the new `authBuddy` identity service: the same email and password work in finanzBuddy and calendarBuddy. `calendar-api` validates authBuddy tokens and keeps only a local account projection.
- Treat one account as one family: every session of that account, on any device, can view, add, edit, and delete all members and all appointments. No per-member logins or permissions.
- Let the family manage member profiles (name and a color from a fixed accessible palette): list, add, rename/recolor, and remove with an explicit confirmation of affected appointments.
- Let the family create, view, edit, and delete appointments (title, timed or all-day range, optional location and notes) assigned to one or more members.
- Support simple repeating appointments: daily, weekly (on chosen weekdays), monthly, or yearly with an interval and an optional end (date or number of occurrences); edit or delete a single occurrence or the whole series.
- Provide a calendar with month and week views plus an agenda list, navigation by period and "Today", and a filter between the whole family and a single member.
- Reuse the finanzBuddy design-system anatomy with a distinct indigo palette and calendarBuddy brand so users recognize a different service.

## Capabilities

### New Capabilities

- `identity-integration`: Accepting homeBuddy account tokens in `calendar-api` and provisioning the local account projection.
- `user-auth-ui`: Sign-in and registration screens backed by authBuddy, session storage, protected routes, and sign-out.
- `family-members`: Account-scoped member profiles, shared family access, member colors, and removal semantics.
- `appointments`: Account-scoped appointment storage, validation, participants, and range queries.
- `appointment-recurrence`: Repeat rules, occurrence expansion, and single-occurrence versus series changes.
- `calendar-view-ui`: Month/week/agenda presentation, period navigation, family/member filter, and appointment create/edit/delete flows including repeats.
- `workspace-shell-ui`: calendarBuddy workspace shell, navigation, and default destination.
- `ui-design-system`: calendarBuddy palette and calendar patterns on the shared finanzBuddy anatomy.
- `compose-deployment`: Local and production Docker Compose topology for calendarBuddy on the shared homeBuddy network.

### Modified Capabilities

- None in this repository.

## Dependencies

- Coordinated by the root umbrella change [`establish-homebuddy-platform`](../../../../../openspec/changes/archive/2026-11-05-establish-homebuddy-platform/proposal.md) (rollout step 3). calendarBuddy conforms to the root capabilities `homebuddy-identity-contract`, `homebuddy-deployment`, and `homebuddy-ui-family`; its own specs describe how.
- `authBuddy` change [`add-identity-service`](../../../../../authBuddy/openspec/changes/archive/2026-11-04-add-identity-service/proposal.md) must be deployed first: it provides registration, sign-in, and the JWKS used to verify tokens.
- finanzBuddy change [`adopt-homebuddy-identity`](../../../../../finanzBuddy/openspec/changes/archive/2026-11-05-adopt-homebuddy-identity/proposal.md) moves finanzBuddy to the same accounts; calendarBuddy does not depend on it technically, but the shared account promise holds only once both are live.

## Non-goals

- Single sign-on session sharing between the two UIs (the same credentials are entered once per service and browser).
- Per-member logins, invitations, roles, or "created by" attribution.
- Complex recurrence: "this and following" edits, nth-weekday-of-month rules ("second Tuesday"), arbitrary RRULE input, or multiple rules per series.
- Reminders/notifications, iCal/CalDAV import or export, and external calendar sync.
- Kafka, object storage, or Grafana dashboards; calendarBuddy only exposes Prometheus-ready actuator metrics.

## Impact

- New repository `calendarBuddy/`: `calendar-api/` (Spring Boot 4.1, Java 25, Liquibase), `ui/` (React 19, TypeScript 7, Vite), compose files, `.env.example`, `docs/`, `AGENTS.md`, CI workflows.
- New PostgreSQL database `calendarbuddy` owned solely by `calendar-api`; no data exchange with finanzBuddy.
- New HTTP API under `/api/family-members` and `/api/appointments`; the UI's `/api/auth/*` calls are proxied to authBuddy.
- Runtime dependency on authBuddy's JWKS endpoint over the shared `homebuddy` Docker network; new Traefik host route for the UI.
