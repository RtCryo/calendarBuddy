## 1. Code style and workflows

- [x] 1.1 Add the Spotless plugin (google-java-format, AOSP) to `calendar-api/pom.xml` as in `authBuddy/auth-service/pom.xml`, apply it with `./mvnw spotless:apply` in its own commit, and name it in `AGENTS.md`. Verify that `./mvnw spotless:check` and `./mvnw verify` pass.
- [ ] 1.2 Replace `calendar-api.yml`, `ui.yml`, and `docker-compose.yml` under `.github/workflows/` with `ci.yml` (jobs `changes`, `calendar-api`, `ui`, `image`, `compose`, `openspec`, `ci`), and add `pr-title.yml`. Verify that the previous every-branch workflows are gone, that a Spotless violation in `calendar-api` fails `ci`, and that a non-conventional title fails the title check.
- [ ] 1.3 Add `release.yml`, `release-please-config.json` (`bootstrap-sha`, `release-as: 1.0.0`, `extra-files` for `.env.example`), `.release-please-manifest.json`, and `CHANGELOG.md`; add `":semanticCommits"` to `renovate.json`. Verify with the first release pull request that it proposes `v1.0.0`.

## 2. Images and Compose

- [x] 2.1 Add the `VERSION` and `REVISION` build arguments and OCI labels to the Dockerfiles of `calendar-api` and `ui`. Verify with `docker build --build-arg VERSION=0.0.0` and `docker inspect` for each.
- [x] 2.2 Switch `calendar-api` and `ui` in `docker-compose.yml` to the `calendarbuddy-<component>` images pinned by `CALENDARBUDDY_VERSION`, move `build:` to `docker-compose.override.yml`, and add the variable to `.env.example`. Verify that `docker compose -f docker-compose.yml config` has no `build:` section, fails without the variable naming it, and that `docker compose up --build` tags `calendarbuddy-<component>:dev` for both.

## 3. Documentation and specifications

- [x] 3.1 Update `README.md`, `docs/README.md`, `docs/ARCHITECTURE.md`, and the deployment notes (delivery flow, `CALENDARBUDDY_VERSION`, rollback across migrations); link the new `delivery` capability from `docs/README.md`. Verify against the root `docs/ARCHITECTURE.md` registry.
- [ ] 3.2 Run `openspec validate adopt-homebuddy-delivery --strict` and archive the change after the root umbrella tasks for this repository are done.
