# Tasks

## 1. Project scaffolding

- [x] 1.1 Initialize `calendarBuddy/` as its own Git repository with the layout from the design (`calendar-api/`, `ui/`, `docs/`, `AGENTS.md`, `.gitignore`, `.env.example`); verify no secrets are tracked.
- [x] 1.2 Scaffold `calendar-api` with Spring Boot 4.1, Java 25, web, security, OAuth2 resource server, validation, JPA, Liquibase, PostgreSQL, actuator, and Maven wrapper; verify `./mvnw verify` passes with a context-load test against Testcontainers PostgreSQL.
- [x] 1.3 Scaffold `ui` with React 19, TypeScript 7, Vite 8, Tailwind 4, React Router 7, axios, Vitest, Testing Library, and oxlint; verify `npm run build`, `npm run test`, and `npm run lint` pass.
- [x] 1.4 Write `docs/ARCHITECTURE.md` (including the authBuddy dependency) and `docs/README.md` (documentation map); verify links resolve.

## 2. homeBuddy identity integration

- [x] 2.1 Add the `accounts` changeset (UUID id, email, time zone) with rollback; verify migration test.
- [x] 2.2 Configure JWT resource-server validation against the authBuddy JWKS with issuer and audience checks, and just-in-time account provisioning and email refresh; verify tests for valid, expired, wrong-issuer, wrong-audience, unknown-key, and HMAC/finanzBuddy-legacy tokens, and concurrent first requests creating one account.
- [x] 2.3 Build login/register pages on `AuthLayout` calling the proxied authBuddy endpoints, token storage, axios interceptor, `ProtectedRoute` with return path, and sign-out; verify UI tests for validation, API errors, unavailable identity service, redirects, and 401 handling.

## 3. Family members and shared access

- [x] 3.1 Add the `family_members` changeset with account FK, case-insensitive unique name per account, and palette check constraint; verify migration test.
- [x] 3.2 Implement list/create/update/delete and removal-impact endpoints with the 20-member cap and atomic unlink/delete of appointments, series, and exceptions; verify tests for validation, duplicates, account isolation, two sessions of one account, and removal outcomes.
- [x] 3.3 Build the `/family` page (member table, add/edit dialog with color choice, removal confirmation with impact); verify UI tests for empty/loading/error, keyboard color selection, and removal flow.

## 4. Appointments

- [x] 4.1 Add `appointments` and `appointment_participants` changesets with account-consistent FKs, timed/all-day check constraints, version column, and range indexes; verify migration test.
- [x] 4.2 Implement CRUD and the occurrence range query with member filter, overlap semantics, 100-day limit, optimistic locking, and validation; verify tests for timed/all-day, multi-day overlap, DST dates, foreign members, stale versions, and account isolation.

## 5. Recurrence

- [x] 5.1 Add recurrence columns, `series_id`/`original_date`, and `appointment_exdates` with constraints (single end, weekday set for weekly, unique exception per series and date); verify migration test.
- [x] 5.2 Implement the expansion engine for daily/weekly/monthly/yearly with interval, until, count, and no end; verify table-driven tests for DST transitions, day 29–31 skipping, 29 February, weekly multi-day sets, count/until boundaries, and range overlap of long-running series.
- [x] 5.3 Implement this-occurrence edit/delete (exception row, exdate) and all-occurrences edit/delete (keep exceptions for non-schedule changes, reset them on schedule changes, cascade on delete); verify service and API tests for each path, member filtering of exceptions, and rollback on failure.

## 6. Calendar UI

- [x] 6.1 Implement URL-backed calendar state, period navigation, Today, view switcher, and member filter; verify UI tests for restore on reload/back and filter changes.
- [x] 6.2 Build the month grid with day cells, chips, "+N more", and empty-day creation; verify keyboard navigation and accessible names.
- [x] 6.3 Build the week view with all-day row and contained hourly grid with overlap layout; verify overlapping layout and slot creation.
- [x] 6.4 Build the agenda list as narrow default and accessible fallback; verify narrow layout and day grouping.
- [x] 6.5 Build the appointment details/editor dialog with participants, all-day toggle, validation, delete confirmation, and 409 handling; verify create/edit/delete, validation, reload prompt, and focus return.
- [x] 6.6 Add the Repeat controls, repeat indicator, this/all occurrences choice, and exceptions-reset warning; verify UI tests for each frequency, end choices, single-occurrence and series edits/deletes, and keyboard operation.

## 7. Shell and design system

- [x] 7.1 Port the finanzBuddy token layer and shared component CSS with the calendarBuddy indigo palette, brand mark, and member palette tokens; verify every new text/control pair against WCAG 2.2 AA.
- [x] 7.2 Implement `AppShell` with Calendar and Family navigation, account email, sign-out, and footer; verify UI tests for navigation and default destination.
- [x] 7.3 Write `ui/DESIGN_SYSTEM.md` and `ui/AGENTS.md` documenting the shared anatomy, palette divergence, and calendar patterns; verify wide/tablet/narrow layouts, focus, contrast, and reduced motion.

## 8. Deployment and verification

- [x] 8.1 Add Dockerfiles, nginx proxies for `/api/auth/` (authBuddy) and `/api/` (calendar-api), `docker-compose.yml` on the external `homebuddy` network, local override ports, and the production Traefik profile; verify `docker compose up` yields healthy services alongside running authBuddy and finanzBuddy stacks, and that one account signs in to both UIs.
- [x] 8.2 Add GitHub workflows for `calendar-api`, `ui`, and compose validation; verify they run green.
- [x] 8.3 Run backend tests, UI tests/build/lint, and `openspec validate add-calendar-buddy --strict`; record any failure and fix it before completion.
