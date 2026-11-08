# Design

## Context

calendarBuddy has two deployable components: `calendar-api` (Spring Boot, Java 25, Maven wrapper, Liquibase) and `ui` (React, `vitest`, `oxlint`, Vite). Its workflows today are `calendar-api.yml` (`./mvnw verify`), `ui.yml` (test, lint, build), and `docker-compose.yml` (development `config` only), all on every branch push. `calendar-api/pom.xml` has no Spotless plugin, unlike authBuddy and finanzBuddy. The shared decisions are made in the root change `add-homebuddy-ci-and-releases` and its capability `homebuddy-delivery`; this design records only what is specific to calendarBuddy.

## Decisions

### 1. Jobs

`ci.yml` has these jobs: `changes` (filters `calendar-api/**`, `ui/**`, `compose`, `openspec`); `calendar-api` (`./mvnw -B spotless:check`, then `./mvnw -B verify`); `ui` (`npm ci`, `npm run lint`, `npm test`, `npm run build`, where `build` runs `tsc -b`); `image` (matrix `calendar-api`, `ui`; build without push); `compose` (both configurations with CI-only values for every required variable and `CALENDARBUDDY_VERSION=0.0.0-ci`); `openspec`; and `ci`.

### 2. Spotless for calendar-api

The Spotless plugin configuration is copied from `authBuddy/auth-service/pom.xml` (google-java-format, AOSP style) so all Java services format alike. Applying it once reformats existing sources; the reformatting is a separate commit in the same pull request so it is easy to skip when reading the diff. `./mvnw verify` keeps working because `spotless:check` is bound as in the other services.

### 3. Images and Dockerfiles

Two images named `ghcr.io/rtcryo/calendarbuddy-<component>`. Each Dockerfile gains `ARG VERSION=dev` and `ARG REVISION=unknown` in its final stage with the `org.opencontainers.image.version` and `org.opencontainers.image.revision` labels.

### 4. Compose

`calendar-api` and `ui` get `image: ghcr.io/rtcryo/calendarbuddy-<component>:${CALENDARBUDDY_VERSION:?Set CALENDARBUDDY_VERSION to a released version (see .env.example)}` and lose `build:` (currently `build: ./calendar-api` and `build: ./ui`). `docker-compose.override.yml` adds `build:` with the same contexts and `image: calendarbuddy-<component>:dev`, next to the host ports it already publishes. PostgreSQL and the service-local Traefik keep their pinned upstream images.

### 5. Release bootstrap

`bootstrap-sha` is the `main` commit that merges this change; `release-as: 1.0.0` yields `v1.0.0` and is removed afterwards in a `chore` pull request. `.env.example` carries `CALENDARBUDDY_VERSION=1.0.0 # x-release-please-version` and is listed under `extra-files`.

## Risks / Trade-offs

- [Reformatting touches many Java files and can hide a real change in review] → Do it in its own commit and keep the rest of the pull request free of Java changes.
- [The monitoring change edits the same Compose files] → Whichever change lands second rebases; the `compose` job then adds the monitoring variables.
- [The first pull from GHCR fails while packages are private] → Make both packages public and linked right after the first release, before switching production.
