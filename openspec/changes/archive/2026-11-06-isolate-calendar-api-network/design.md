# Design

## Context

See proposal.md for motivation. Docker registers a container's service name and container name on every network it joins, so only network membership can hide `calendar-api`. The API reads `AUTH_JWKS_URI` (default `http://auth:8080/.well-known/jwks.json`). The root [design](../../../../../openspec/changes/archive/2026-11-06-isolate-product-apis/design.md) records the rationale for per-API identity networks.

## Decisions

- **Replace `homebuddy` with `calendarbuddy-identity` for `calendar-api` only.** It keeps its default network for PostgreSQL. The UI is unchanged.
- **Declare `calendarbuddy-identity` as `external: true`** so Compose fails fast with the network name.
- **No application change.** The alias `auth` is the same on the identity network.

## Risks / Trade-offs

- Deploying before authBuddy joins `calendarbuddy-identity` makes the JWKS fetch fail and every request 401. Mitigation: the rollout order and a reachability check before the change.

## Migration Plan

1. Confirm authBuddy is deployed on `calendarbuddy-identity`.
2. Create the network if missing, apply the change, and run `docker compose up -d`.

Rollback: put `calendar-api` back on `homebuddy` and recreate it.
