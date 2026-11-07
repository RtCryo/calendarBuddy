## MODIFIED Requirements

### Requirement: calendarBuddy reaches the identity service over the homeBuddy network
The `ui` SHALL join the external `homebuddy` Docker network. `calendar-api` SHALL NOT join `homebuddy`; it SHALL join the external, internal-only network `calendarbuddy-identity` defined by the root capability `homebuddy-deployment`, on which authBuddy is reachable as `auth`, and the external, internal-only network `calendarbuddy-metrics` defined by the root capability `homebuddy-monitoring`, on which only the central Prometheus can reach it. The UI SHALL proxy `/api/auth/` to authBuddy and all other `/api/` requests to `calendar-api` over the stack's default network so the browser uses one origin. `calendar-api` SHALL be configured with authBuddy's JWKS URI, issuer, and the `calendarbuddy` audience through environment variables. The README SHALL list the `docker network create --internal` command for `calendarbuddy-identity` and `calendarbuddy-metrics`.

#### Scenario: Sign in through the UI proxy
- **WHEN** the browser posts credentials to `/api/auth/login` on the calendarBuddy origin
- **THEN** the request reaches authBuddy and the issued token is accepted by `calendar-api`

#### Scenario: API fetches the signing keys
- **WHEN** `calendar-api` validates a token with an unknown key id
- **THEN** it fetches `http://auth:8080/.well-known/jwks.json` over `calendarbuddy-identity`

#### Scenario: API is not reachable from another stack
- **WHEN** a container of another stack resolves `calendar-api` or `calendarbuddy-calendar-api`
- **THEN** the name does not resolve

#### Scenario: Prometheus scrapes the API
- **WHEN** the central Prometheus connects to `calendarbuddy-calendar-api:8080`
- **THEN** the connection succeeds over `calendarbuddy-metrics`

#### Scenario: Missing network
- **WHEN** the `homebuddy`, `calendarbuddy-identity`, or `calendarbuddy-metrics` network does not exist
- **THEN** startup fails with a message naming the network, as documented in the README
