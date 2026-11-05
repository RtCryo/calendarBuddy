# Spec Delta

## Purpose

Lets calendarBuddy accept the shared homeBuddy account issued by authBuddy, so a family uses the same credentials as in finanzBuddy.

## ADDED Requirements

### Requirement: calendar-api accepts only valid homeBuddy account tokens
`calendar-api` SHALL accept a bearer token on every `/api` endpoint except health only when it is an RS256 JWT signed by a key published in authBuddy's JWKS, with issuer `homebuddy-auth`, an audience containing `calendarbuddy`, an unexpired expiry, and an account UUID subject. `calendar-api` SHALL NOT store passwords or issue tokens. Signing keys SHALL be cached and refreshed when a token names an unknown key id.

#### Scenario: Valid homeBuddy token
- **WHEN** a request carries a token issued by authBuddy for a registered account
- **THEN** the request is processed as that account

#### Scenario: Missing or expired token
- **WHEN** a request reaches a protected endpoint without a token or with an expired one
- **THEN** the system returns 401 Unauthorized

#### Scenario: Token with wrong issuer, audience, or signature
- **WHEN** a request carries a token signed with an unknown key, a shared HMAC secret, another issuer, or an audience without `calendarbuddy`
- **THEN** the system returns 401 Unauthorized

#### Scenario: Signing key rotation
- **WHEN** authBuddy starts signing with a new key that is published in its JWKS
- **THEN** `calendar-api` accepts tokens signed with the new key without a restart

### Requirement: Accounts are provisioned on first use
On the first accepted request of an account, `calendar-api` SHALL create exactly one local account record keyed by the token subject, with the token email and the default time zone `Europe/Berlin`, and SHALL update the stored email when the token's email changes. All calendar data SHALL belong to the local account record, and each request SHALL act only on its own account's data.

#### Scenario: First visit of a new account
- **WHEN** an account registered in finanzBuddy opens calendarBuddy for the first time
- **THEN** a local account with no members and no appointments is created

#### Scenario: Concurrent first requests
- **WHEN** two requests of a new account arrive at the same time
- **THEN** exactly one local account record exists afterwards and both requests succeed

#### Scenario: Data isolation
- **WHEN** two different accounts use calendarBuddy
- **THEN** neither can read or change the other's members or appointments
