# Spec Delta

## ADDED Requirements

### Requirement: Production runs the released calendarBuddy images
The production Compose file SHALL run `calendar-api` and `ui` from `ghcr.io/rtcryo/calendarbuddy-<component>:${CALENDARBUDDY_VERSION}` without `build:` sections, and SHALL fail with a message naming `CALENDARBUDDY_VERSION` when the variable is unset, as the root capability `homebuddy-delivery` requires. `docker-compose.override.yml` SHALL build both components from their directories and tag them `calendarbuddy-<component>:dev`. `.env.example` SHALL define `CALENDARBUDDY_VERSION` with the latest released version. PostgreSQL and the service-local Traefik keep their pinned upstream images; networks, routing, ports, and aliases are unchanged.

#### Scenario: Deploy a release
- **WHEN** an operator sets `CALENDARBUDDY_VERSION=1.0.0` and runs `docker compose -f docker-compose.yml pull` and `up -d`
- **THEN** the calendarBuddy application containers run the `1.0.0` images and keep their networks and routing

#### Scenario: Version variable missing
- **WHEN** `docker compose -f docker-compose.yml up -d` runs without `CALENDARBUDDY_VERSION`
- **THEN** Compose fails with a message naming `CALENDARBUDDY_VERSION`

#### Scenario: Local development
- **WHEN** a developer runs `docker compose up --build`
- **THEN** both components are built from the working tree and tagged `calendarbuddy-<component>:dev`
