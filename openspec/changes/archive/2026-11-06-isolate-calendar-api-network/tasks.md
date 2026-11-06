# Tasks

## 1. Compose

- [x] 1.1 In `docker-compose.yml`, replace `homebuddy` with the external `calendarbuddy-identity` network for `calendar-api`, declare the network, and keep the UI on `homebuddy`. Verify with `docker compose config` that `calendar-api` lists `default` and `calendarbuddy-identity` only, and `ui` still lists `homebuddy`.

## 2. Documentation

- [x] 2.1 Document `docker network create --internal calendarbuddy-identity` in `README.md` and `docs/ARCHITECTURE.md`, and update network mentions there.

## 3. Verification

- [x] 3.1 After authBuddy is deployed on `calendarbuddy-identity`, verify that `calendar-api` accepts a fresh homeBuddy token through the calendarBuddy UI.
- [x] 3.2 Verify that `calendar-api` and `calendarbuddy-calendar-api` do not resolve from a throwaway container on `homebuddy`.
- [x] 3.3 Verify that starting the stack without `calendarbuddy-identity` fails with a message naming it.
