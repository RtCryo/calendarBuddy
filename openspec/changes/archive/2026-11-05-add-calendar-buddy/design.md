# Design

## Context

See [proposal](proposal.md) for motivation and the capability deltas under [`specs/`](specs/) for normative behavior. `homeBuddy/` is a root workspace whose [`openspec/`](../../../../openspec/) holds the cross-service contracts this design follows (`homebuddy-identity-contract`, `homebuddy-deployment`, `homebuddy-ui-family`); service repositories such as `finanzBuddy/` are listed in its `repos.txt`. It establishes the reference stack: Spring Boot 4.1.2 on Java 25 with Spring Security, JWT via jjwt, JPA, Liquibase and PostgreSQL 18; a React 19 / TypeScript 7 / Vite 8 / Tailwind 4 UI tested with Vitest and linted with oxlint, served by nginx; Docker Compose with a `production` Traefik profile. Its UI contract lives in `finanzBuddy/ui/DESIGN_SYSTEM.md` and the `ui-design-system` capability.

The family uses one homeBuddy account for both services. Accounts are issued by the new `authBuddy` service ([`add-identity-service`](../../../../../authBuddy/openspec/changes/archive/2026-11-04-add-identity-service/design.md)): RS256-signed JWTs with `iss=homebuddy-auth`, `sub` = account UUID, an `email` claim, `aud` containing `calendarbuddy` and `finanzbuddy`, and a JWKS at `/.well-known/jwks.json`.

## Goals / Non-Goals

**Goals:** Ship a self-contained calendarBuddy that a family can use end to end (sign in with the homeBuddy account, add members, plan one-off and repeating appointments, view by family or member). Keep the codebase recognizably the same shape as finanzBuddy. Keep the visual language shared while making the service identity obvious.

**Non-Goals:** Cross-UI single sign-on sessions, member logins, complex recurrence rules, reminders, external calendar sync, event streaming, object storage.

## Decisions

### Separate repository that mirrors finanzBuddy

```
calendarBuddy/            (own Git repository)
  calendar-api/           Spring Boot service (package com.calendarbuddy.api)
  ui/                     React app (DESIGN_SYSTEM.md, AGENTS.md, nginx.conf, Dockerfile)
  docs/                   ARCHITECTURE.md, README.md (documentation map)
  openspec/               config, specs, changes
  docker-compose.yml, docker-compose.override.yml, .env.example, AGENTS.md
  .github/workflows/      calendar-api.yml, ui.yml, docker-compose.yml
```

A single backend is enough: there is no asynchronous processing, so no Kafka, parser, or object storage. Alternative: add calendar endpoints to finanzBuddy's `api-gateway`. Rejected because it couples deployments and data ownership of two products.

### calendar-api is a resource server for homeBuddy accounts

`calendar-api` does not store passwords or issue tokens. It uses Spring Security's OAuth2 resource-server JWT support configured with authBuddy's JWKS URI (cached, refreshed on unknown `kid`), and requires `iss=homebuddy-auth`, `aud` containing `calendarbuddy`, and an unexpired `exp`. Health is public; everything else under `/api` requires a valid token.

On the first authenticated request of an account, `calendar-api` inserts `accounts(id uuid = sub, email, time_zone default 'Europe/Berlin', created_at)` and updates `email` when the claim changes. All calendar tables reference `accounts.id`. The UI's `/api/auth/register` and `/api/auth/login` are proxied by its nginx to authBuddy, so the browser keeps a single origin and the auth contract (`{ token, email }`) matches finanzBuddy's.

Alternatives: validating a shared HMAC secret (every service could mint tokens for every other one) or calling authBuddy per request (adds latency and a hard runtime dependency). JWKS-based verification needs authBuddy only for key refresh.

### One account is one family with shared full access

There is no member-level identity. Any valid token for the account may read and change every member and appointment of that account, and several devices may be signed in concurrently. Concurrent edits are protected by optimistic versions rather than locks. Because the account is shared with finanzBuddy, everyone who knows the family credentials can also open the family's finanzBuddy data; the auth screens state that the account is the shared homeBuddy account.

### Family members are profiles

`family_members(id, account_id, name, color, created_at)` holds profiles without credentials, listed in creation order. Names are trimmed, 1–100 characters, unique per account ignoring case. `color` is a key from a fixed palette (`indigo`, `teal`, `coral`, `rose`, `amber`, `sky`, `violet`, `slate`), each verified at ≥4.5:1 against white. The family is capped at 20 members.

Removing a member is one atomic request: the member is unlinked from shared appointments and series, and appointments or series where the member was the only participant are deleted with their exceptions. `GET /api/family-members/{id}/removal-impact` returns both counts so the UI can confirm precisely. Alternative: archive members; deferred until history is needed.

### Appointments with explicit timed and all-day semantics

`appointments(id, account_id, title, location, notes, all_day, start_at, end_at, start_date, end_date, recurrence columns, series_id, original_date, version, created_at, updated_at)` plus `appointment_participants(appointment_id, member_id)` with owner-consistent foreign keys.

- Timed appointments store `start_at`/`end_at` as `timestamptz`; the API exchanges ISO-8601 instants with offset, and the UI renders in the account time zone.
- All-day appointments store `start_date`/`end_date` as dates; the end date is inclusive in API and UI.
- Validation: title trimmed 1–200 characters, location ≤200, notes ≤2000, end not before start, duration at most 31 days, at least one participant, every participant in the same account. Violations return 400 with field errors; foreign ids return 404.
- `version` enables optimistic locking; a stale update returns 409 and the UI offers to reload.

`GET /api/appointments?from=&to=&memberId=` takes a half-open date range in the account time zone of at most 100 days and returns **occurrences**: one item per one-off appointment and per expanded series occurrence overlapping the range, ordered by start then title. Each item carries `appointmentId`, `occurrenceDate`, `seriesId` (for series occurrences and detached exceptions), and `recurring`. CRUD lives under `/api/appointments` and `/api/appointments/{id}`.

### Simple recurrence stored as a structured rule

A series is an appointment row with `recurrence_freq` (`DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY`), `recurrence_interval` (1–99), `recurrence_weekdays` (non-empty set for weekly, defaulting to the start's weekday), and at most one end: `recurrence_until` (inclusive date) or `recurrence_count` (1–730). No end means it repeats indefinitely. The first occurrence is the appointment's own start.

- Occurrences keep the series' local wall-clock time and duration in the account time zone, so 10:00 stays 10:00 across daylight-saving changes.
- Monthly repeats on the start's day of month and skips months without that day; yearly on 29 February occurs only in leap years (RFC 5545 semantics for these subsets).
- Expansion runs in `calendar-api` per query; the 100-day range bound keeps it finite. A series is a range-query candidate when its start is before the range end and its computed last occurrence (or open end) is not before the range start.
- **This occurrence** edits create a detached exception row (`series_id`, `original_date`) with its own fields and participants that replaces that occurrence; **this occurrence** deletes add `appointment_exdates(series_id, occurrence_date)`. Editing a detached exception again updates that row.
- **All occurrences** edits update the series row. If only title, location, notes, or participants change, exceptions are kept. If the schedule changes (start, end, all-day flag, or rule), exceptions and exdates are discarded in the same transaction; the UI warns before saving. **All occurrences** delete removes the series, its exceptions, and exdates.
- The member filter matches a series by the series participants and a detached exception by its own participants.

Alternatives: store an RFC 5545 RRULE string (flexible but invites unsupported rules and needs parsing on every path) or materialize every occurrence row (simple reads but unbounded rows for open-ended series and costly series edits). Structured columns validate easily and can later be mapped to RRULE for iCal export.

### Calendar UI: month, week, agenda, and a member filter

Routes: `/login`, `/register`, `/` → `/calendar`, `/calendar`, `/family`. Calendar state lives in the URL (`/calendar?view=month&date=2026-11-03&member=12`).

- **Month view** (default on wide/tablet): Monday-first grid of the weeks covering the month, today marked, up to three chips per day and "+N more".
- **Week view**: all-day row above an hourly grid that scrolls inside its container and opens at 07:00; overlapping timed appointments share the column width.
- **Agenda list** (default on narrow, available everywhere): chronological day groups; also the accessible fallback of the grids.
- **Filter**: labeled selector with "Whole family" and each member (swatch + name); a selected member is named in the heading.
- **Chips** show member markers with names or initials, time or "All day", title, and a repeat icon with accessible text for series occurrences.
- **Details/editor dialog** reuses the right-side detail-panel pattern: title, all-day toggle, date/time fields, participant checkboxes, a Repeat selector (Does not repeat, Daily, Weekly, Monthly, Yearly) with interval, weekday toggles for weekly, and end choice (Never, On date, After N times), location, notes. For a series occurrence, Save and Delete first ask "This occurrence" or "All occurrences"; schedule changes to all occurrences show the exceptions-reset warning.
- The family page lists members with color, name, and upcoming appointment count, add/edit in a dialog, and remove with the impact confirmation.

Alternative: a third-party calendar component. It brings its own visual language, conflicting with the design-system rule against a competing language; the needed views are small enough for CSS grid.

### Same design-system anatomy, different palette

calendarBuddy copies finanzBuddy's `index.css` structure, token names, and shared component classes and documents them in its own `ui/DESIGN_SYSTEM.md`, linking to finanzBuddy's reference as the origin. Only color values and the brand change:

| Token | finanzBuddy | calendarBuddy | Contrast on white |
|---|---|---|---|
| `--color-primary` / `--color-focus` | `#167D68` teal | `#4F46B8` indigo | 7.24:1 |
| `--color-primary-strong` | `#116653` | `#3D3594` | 9.83:1 |
| `--color-text-strong` | `#172B4D` | `#1E2A4A` | 14.14:1 |
| `--color-canvas` | `#F6F8FB` | `#F6F6FC` (primary on it 6.72:1) | — |
| primary soft surface / border | teal tints | indigo tints (`#EEEDFA`, strong on it 8.49:1) | — |

Semantic success/warning/danger/info tokens stay identical. The brand mark becomes a calendar glyph with "calendarBuddy". New calendar patterns are added to the catalog with contrast and responsive results.

Alternative: extract a shared `@homebuddy/ui` package; recorded as a follow-up once both UIs stabilize.

### Deployment on the shared homeBuddy network

`docker-compose.yml` defines `postgres` (database `calendarbuddy`, own volume), `calendar-api`, `ui`, and a `production`-profile Traefik routing `Host(${CALENDAR_DOMAIN})` to the UI. `calendar-api` and `ui` join the external Docker network `homebuddy`, where authBuddy is reachable as `auth`; nginx proxies `/api/auth/` to `http://auth:8080/auth/` and `/api/` to `calendar-api`. Container names use the `calendarbuddy-` prefix. Local override ports follow the homeBuddy port registry: UI 3010, API 8081, Postgres 5433. The UI carries Traefik labels with `traefik.docker.network=homebuddy` so the shared root Traefik routes it; the stack's own Traefik is only for a standalone deployment and must not run beside the shared one.

## Risks / Trade-offs

- authBuddy unavailable -> existing tokens keep working with cached keys; sign-in and registration fail with a clear notice until it returns.
- Shared family credentials also open finanzBuddy -> stated on the auth screens; per-member access stays a documented follow-up.
- Recurrence edge cases (DST, month ends, leap years) -> structured subset with explicit semantics and table-driven expansion tests in `Europe/Berlin`.
- Discarding exceptions on schedule changes may surprise users -> explicit warning before saving.
- Hand-built grids risk accessibility gaps -> agenda list as semantic fallback, labeled grid roles, keyboard tests.
- Duplicated design-system CSS can drift from finanzBuddy -> identical token names; divergence recorded in `DESIGN_SYSTEM.md`.

## Migration Plan

1. Deploy authBuddy (`add-identity-service`) and the `homebuddy` network.
2. Scaffold and deploy `calendar-api` and `ui`; Liquibase creates accounts, members, appointments, participants, and exdates. There is no existing calendar data.
3. Rollback is removing the calendarBuddy containers; dropping its volume deletes all calendar data and requires explicit acceptance. Accounts remain in authBuddy.
