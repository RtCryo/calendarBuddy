## MODIFIED Requirements

### Requirement: calendarBuddy reaches the identity service over the homeBuddy network
The `ui` SHALL join the external `homebuddy` Docker network. `calendar-api` SHALL NOT join `homebuddy`; it SHALL join the external, internal-only network `calendarbuddy-identity` defined by the root capability `homebuddy-deployment`, on which authBuddy is reachable as `auth`. The UI SHALL proxy `/api/auth/` to authBuddy and all other `/api/` requests to `calendar-api` over the stack's default network so the browser uses one origin. `calendar-api` SHALL be configured with authBuddy's JWKS URI, issuer, and the `calendarbuddy` audience through environment variables.

#### Scenario: Sign in through the UI proxy
- **WHEN** the browser posts credentials to `/api/auth/login` on the calendarBuddy origin
- **THEN** the request reaches authBuddy and the issued token is accepted by `calendar-api`

#### Scenario: API fetches the signing keys
- **WHEN** `calendar-api` validates a token with an unknown key id
- **THEN** it fetches `http://auth:8080/.well-known/jwks.json` over `calendarbuddy-identity`

#### Scenario: API is not reachable from another stack
- **WHEN** a container of another stack resolves `calendar-api` or `calendarbuddy-calendar-api`
- **THEN** the name does not resolve

#### Scenario: Missing network
- **WHEN** the `homebuddy` or `calendarbuddy-identity` network does not exist
- **THEN** startup fails with a message naming the network, as documented in the README
