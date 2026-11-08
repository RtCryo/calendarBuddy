# Proposal

## Why

The root change [`add-homebuddy-ci-and-releases`](https://github.com/RtCryo/homeBuddy/blob/main/openspec/changes/add-homebuddy-ci-and-releases/proposal.md) defines the root capability `homebuddy-delivery`: one CI workflow with a required `ci` gate, Conventional Commits, release-please releases, and production Compose files that run released images. calendarBuddy's three workflows run on every branch push without a `main` filter, `permissions`, or `concurrency`; `calendar-api` has no code-style check at all (its `pom.xml` has no Spotless); no Docker image is built in CI; OpenSpec is never validated; and there is no release, so production builds two images from whatever source is checked out.

## What Changes

- Replace `calendar-api.yml`, `ui.yml`, and `docker-compose.yml` under `.github/workflows/` with `ci.yml` following the root capability `homebuddy-delivery`: change detection, per component the checks listed in the `delivery` spec, an image build without push for each deployable component, validation of the production and development Compose configuration, `openspec validate --all --strict` pinned to `@fission-ai/openspec@1.13.2`, and the `ci` gate. It triggers only on pull requests to `main` and pushes to `main`.
- Add Spotless with google-java-format (AOSP style, as in authBuddy and finanzBuddy) to `calendar-api/pom.xml`, apply it once to the existing sources, and document `./mvnw spotless:apply` in the service instructions.
- Add `pr-title.yml`, `release.yml`, `release-please-config.json`, `.release-please-manifest.json`, and `CHANGELOG.md`; add `":semanticCommits"` to `renovate.json`.
- Publish two images per release: `ghcr.io/rtcryo/calendarbuddy-calendar-api` and `ghcr.io/rtcryo/calendarbuddy-ui`, each tagged `X.Y.Z` and `X.Y` with OCI labels and provenance. Both Dockerfiles accept `VERSION` and `REVISION` build arguments.
- `docker-compose.yml` runs the two components from those images, pinned by `CALENDARBUDDY_VERSION`; `docker-compose.override.yml` builds them from source and tags them `calendarbuddy-<component>:dev`. `.env.example` documents `CALENDARBUDDY_VERSION`. The service-local Traefik, networks, and ports are unchanged.
- **BREAKING (deployment)**: `docker compose -f docker-compose.yml up -d` no longer builds; it pulls the version named in `.env` and fails naming `CALENDARBUDDY_VERSION` when it is missing.
- Document the delivery flow in `README.md`, `docs/README.md`, and `docs/ARCHITECTURE.md`.

## Capabilities

### New Capabilities

- `delivery`: calendarBuddy's deployable components, image names, checks per component, and release configuration, as the instance of root `homebuddy-delivery` for this repository.

### Modified Capabilities

- `compose-deployment`: production runs the released `calendar-api` and `ui` images pinned by `CALENDARBUDDY_VERSION`; local development builds from source.

## Impact

- `.github/workflows/`, `calendar-api/pom.xml` and its Java sources (formatting only), `release-please-config.json`, `.release-please-manifest.json`, `CHANGELOG.md`, `renovate.json`, `docker-compose.yml`, `docker-compose.override.yml`, `.env.example`, the Dockerfiles of `calendar-api` and `ui`, `README.md`, `docs/`, `AGENTS.md`.
- GitHub settings: squash merge only, required checks `ci` and the title check on `main`, secret `RELEASE_PLEASE_TOKEN`, public and linked GHCR packages (configured in the root umbrella change's rollout).
- Rollout order: calendarBuddy is redeployed from its `v1.0.0` release after authBuddy runs its own release in production.
