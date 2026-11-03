# Spec Delta

## Purpose

Defines how calendarBuddy runs locally and in production, independently of and alongside the other homeBuddy services.

## ADDED Requirements

### Requirement: calendarBuddy runs as its own Compose stack
The repository SHALL provide a Docker Compose stack with PostgreSQL, `calendar-api`, and `ui`, configured from an `.env` file documented by `.env.example`. Services SHALL have health checks, start in dependency order, and use `calendarbuddy-` container names, a dedicated database, and dedicated volumes. Secrets SHALL NOT be committed.

#### Scenario: Start locally
- **WHEN** a developer runs the authBuddy stack, copies `.env.example` to `.env`, and runs `docker compose up`
- **THEN** PostgreSQL, `calendar-api`, and `ui` become healthy and the calendar is reachable in the browser

#### Scenario: Run beside finanzBuddy
- **WHEN** finanzBuddy's local stack is already running on the same machine
- **THEN** calendarBuddy starts without port, container-name, or volume conflicts

### Requirement: calendarBuddy reaches the identity service over the homeBuddy network
`calendar-api` and `ui` SHALL join the external `homebuddy` Docker network. The UI SHALL proxy `/api/auth/` to authBuddy and all other `/api/` requests to `calendar-api` so the browser uses one origin. `calendar-api` SHALL be configured with authBuddy's JWKS URI, issuer, and the `calendarbuddy` audience through environment variables.

#### Scenario: Sign in through the UI proxy
- **WHEN** the browser posts credentials to `/api/auth/login` on the calendarBuddy origin
- **THEN** the request reaches authBuddy and the issued token is accepted by `calendar-api`

#### Scenario: Missing network
- **WHEN** the `homebuddy` network does not exist
- **THEN** startup fails with a message naming the network, as documented in the README

### Requirement: Production exposes the UI through Traefik
The `production` profile SHALL route a configurable `CALENDAR_DOMAIN` to the UI over HTTPS with automatic certificates, and SHALL support attaching to an existing homeBuddy Traefik instance instead of starting a second one on the same host. `calendar-api` and PostgreSQL SHALL NOT be published to the host.

#### Scenario: Production routing
- **WHEN** the stack runs with the `production` profile and `CALENDAR_DOMAIN` set
- **THEN** HTTPS requests to that host reach the UI, `/api/auth/` reaches authBuddy, and other `/api/` requests reach `calendar-api`

#### Scenario: Shared Traefik host
- **WHEN** another homeBuddy Traefik already owns ports 80 and 443
- **THEN** calendarBuddy is routed through it via the `homebuddy` network without starting its own Traefik
