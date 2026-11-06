# Proposal

## Why

The root capability `homebuddy-deployment` says a product API SHALL NOT be reachable from another stack, and the root change [`isolate-product-apis`](../../../../../openspec/changes/archive/2026-11-06-isolate-product-apis/proposal.md) closes the gap. `calendar-api` still joins the shared `homebuddy` network only to fetch authBuddy's JWKS, so every container of every stack can call it directly.

## What Changes

- **BREAKING (deployment)**: `calendar-api` leaves `homebuddy` and joins the external, internal-only network `calendarbuddy-identity` instead, where authBuddy is reachable as `auth`. `AUTH_JWKS_URI` and all other configuration stay unchanged.
- The UI keeps its `homebuddy` membership and reaches `calendar-api` over the stack's default network.
- The stack no longer starts until `calendarbuddy-identity` exists. README and architecture documentation describe `docker network create --internal calendarbuddy-identity`.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `compose-deployment`: the requirement "calendarBuddy reaches the identity service over the homeBuddy network" separates the UI's `homebuddy` membership from the API's identity network.

## Non-goals

- Changing token validation or the JWKS URI.
- Isolating the UI on `homebuddy`.

## Impact

- `docker-compose.yml`, `README.md`, `docs/ARCHITECTURE.md`, and the `compose-deployment` spec.
- Rollout: apply only after authBuddy's [`join-identity-networks`](../../../../../authBuddy/openspec/changes/archive/2026-11-06-join-identity-networks/proposal.md) is deployed; otherwise every request returns 401.
