# calendarBuddy architecture

calendarBuddy is an independent homeBuddy service with a Spring Boot `calendar-api`, a React UI, and its own PostgreSQL database. The [documentation map](README.md) links the service entry points. The root [homeBuddy architecture](../../docs/ARCHITECTURE.md) owns cross-service contracts.

authBuddy issues homeBuddy account tokens. The UI proxies `/api/auth/` to authBuddy over the external `homebuddy` network; other `/api/` requests go to `calendar-api`. The API validates RS256 tokens against authBuddy's JWKS and stores only an account projection keyed by the token subject. It does not store passwords or issue tokens.

One account represents one family. Its members and appointments are private to that account, and every session of the account may edit them. PostgreSQL owns account projections, members, appointments, participants, recurrence rules, and exceptions. No calendar data is shared with finanzBuddy.

In local development, the UI uses port 3010, the API uses 8081, and PostgreSQL uses 5433. The production UI joins the external `homebuddy` network as `calendarbuddy-ui` and carries a `CALENDAR_DOMAIN` Traefik route. The root Traefik owns ports 80 and 443 on a shared host; the service-local Traefik profile is for standalone deployment.

## Data and API

Liquibase changesets create `accounts`, `family_members`, `appointments`, `appointment_participants`, and `appointment_exdates`. The API validates and applies all writes in transactions. Appointment participants use account-consistent foreign keys. A detached occurrence is an appointment row with `series_id` and `original_date`; a skipped occurrence is an exdate. The parent series version guards both kinds of change.

`/api/family-members` supports list, create, update, removal impact, and removal. `/api/appointments` supports CRUD and a range query with a 100-day cap and an optional member filter. Timed values are instants; all-day values are inclusive local dates. The recurrence engine expands daily, weekly, monthly, and yearly rules in the account time zone. It skips missing month days and preserves wall-clock time across daylight-saving transitions.

The React UI stores the JWT in session storage, adds it to protected requests, and routes unauthorized users to sign-in with a return path. `/calendar` has month, week, and agenda modes with URL-backed date, view, and member state. `/family` manages member profiles. Both pages use the shared design anatomy documented in [the UI design system](../ui/DESIGN_SYSTEM.md).

## Local run

Start Rancher Desktop or another Docker engine. Create the external network with `docker network create homebuddy`, then start authBuddy. Set `POSTGRES_PASSWORD` and `CALENDAR_DOMAIN` in a local `.env` copied from `.env.example`, then run `docker compose up -d --build`. The UI is at `http://localhost:3010`; the API health endpoint is at `http://localhost:8081/actuator/health`.
