# Spec Delta

## Purpose

Defines how calendarBuddy instantiates the root capability `homebuddy-delivery`: its deployable components, its images, and its checks.

## ADDED Requirements

### Requirement: calendarBuddy follows the homeBuddy delivery contract
calendarBuddy SHALL satisfy the root capability `homebuddy-delivery` (CI baseline, `ci` gate, Conventional Commits, release-please releases, image publication, production on released images) and SHALL NOT restate it. Its deployable components are `calendar-api` and `ui`; their images are `ghcr.io/rtcryo/calendarbuddy-calendar-api` and `ghcr.io/rtcryo/calendarbuddy-ui`. Its CI SHALL, for `calendar-api`, check formatting with Spotless and run `./mvnw verify`, and for `ui`, run `npm run lint`, `npm test`, and `npm run build`. It SHALL build the image of every deployable component without pushing it. Its workflows SHALL trigger only on pull requests to `main` and pushes to `main`.

#### Scenario: Formatting violation in the API
- **WHEN** a pull request changes a Java file in `calendar-api` so that `spotless:check` fails
- **THEN** `ci` fails

#### Scenario: A feature branch is pushed
- **WHEN** a contributor pushes to a branch other than `main` without opening a pull request
- **THEN** no workflow runs

#### Scenario: A UI test fails
- **WHEN** a pull request makes a Vitest test in `ui` fail
- **THEN** `ci` fails

### Requirement: calendar-api is formatted with Spotless
`calendar-api` SHALL configure Spotless with google-java-format in AOSP style, as the other homeBuddy Java services do, and its sources SHALL satisfy `./mvnw spotless:check`. Contributor instructions SHALL name `./mvnw spotless:apply` to format.

#### Scenario: Format locally
- **WHEN** a developer runs `./mvnw spotless:apply` in `calendar-api`
- **THEN** the sources are rewritten to the configured style and `./mvnw spotless:check` passes

### Requirement: A release publishes the calendarBuddy images
Each calendarBuddy release SHALL publish the two images named above with the tags `X.Y.Z` and `X.Y` as specified by the root capability `homebuddy-delivery`, each built from its component's `Dockerfile` with the build arguments `VERSION` and `REVISION`.

#### Scenario: calendarBuddy releases v1.0.0
- **WHEN** the release pull request for `v1.0.0` is merged
- **THEN** `calendarbuddy-calendar-api:1.0.0` and `calendarbuddy-ui:1.0.0` exist under `ghcr.io/rtcryo/` with attestations, and the GitHub Release lists both images with their digests
