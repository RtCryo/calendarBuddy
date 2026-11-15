# calendarBuddy

calendarBuddy is the family calendar service in the [homeBuddy workspace](../README.md). It uses the shared homeBuddy account from authBuddy. One family account can manage up to 20 member profiles, one-off and recurring appointments, and month, week, and agenda views.

See the [documentation map](docs/README.md), [architecture](docs/ARCHITECTURE.md), and [UI design system](ui/DESIGN_SYSTEM.md).

## Local development

Start Docker, create the shared `homebuddy` network and the internal `calendarbuddy-identity` and `calendarbuddy-metrics` networks (`docker network create --internal calendarbuddy-identity` and `docker network create --internal calendarbuddy-metrics`), then start authBuddy. The central Prometheus of the root stack scrapes `calendar-api` over `calendarbuddy-metrics`; see Metrics in `docs/ARCHITECTURE.md`. Copy `.env.example` to `.env`, set a local PostgreSQL password and domain, and run `docker compose up -d --build`. Open `http://localhost:3010`. The API health endpoint is at `http://localhost:8081/actuator/health`.

Run API tests in `calendar-api/` with `./mvnw verify` and format the sources with `./mvnw spotless:apply` (Spotless with google-java-format, AOSP style). Run UI checks in `ui/` with `npm ci`, `npm run test`, `npm run lint`, and `npm run build`. API integration tests require Docker for PostgreSQL Testcontainers.
