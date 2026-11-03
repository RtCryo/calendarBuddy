# Spec Delta

## Purpose

Defines the calendarBuddy sign-in, registration, session, and sign-out experience using the shared homeBuddy account.

## ADDED Requirements

### Requirement: Users register and sign in with the homeBuddy account
The UI SHALL provide `/register` and `/login` pages using the shared authentication layout with calendarBuddy branding, calling authBuddy through the UI's `/api/auth` proxy. The pages SHALL state that the account is the family's homeBuddy account shared with finanzBuddy. Fields SHALL have persistent labels and native type/required validation; submission SHALL be disabled while a request is pending. API errors SHALL be shown as error notices without clearing the email field.

#### Scenario: Sign in with an existing finanzBuddy account
- **WHEN** a user enters the email and password they use for finanzBuddy
- **THEN** the UI stores the issued token and opens the calendar

#### Scenario: Register successfully
- **WHEN** a visitor submits valid registration data
- **THEN** the UI shows a success notice and offers to continue to sign-in with the email prefilled

#### Scenario: Register with an existing email
- **WHEN** the identity service responds 409 to a registration
- **THEN** the UI shows that a homeBuddy account with this email already exists and links to sign-in

#### Scenario: Sign in with wrong credentials
- **WHEN** the identity service responds 401 to a sign-in
- **THEN** the UI shows a generic invalid credentials notice and keeps the email entered

#### Scenario: Identity service unavailable
- **WHEN** the sign-in or registration request fails because the identity service cannot be reached
- **THEN** the UI shows that sign-in is temporarily unavailable and lets the user retry

### Requirement: Protected pages require a session
The UI SHALL store the access token for the session, attach it to API requests, and redirect unauthenticated visitors of protected pages to `/login`, returning them to the requested page after sign-in. Without a requested page, sign-in SHALL open the calendar. A 401 response from any API call SHALL clear the session and redirect to sign-in.

#### Scenario: Open a protected page without a session
- **WHEN** a visitor opens `/family` without a valid session
- **THEN** the UI redirects to `/login` and returns to `/family` after successful sign-in

#### Scenario: Session expires during use
- **WHEN** an API call returns 401 while the user is on the calendar
- **THEN** the session is cleared and the user is redirected to sign-in with the calendar as return path

### Requirement: Users can sign out
The workspace SHALL offer a sign-out action that clears the stored calendarBuddy session and opens `/login`.

#### Scenario: Sign out
- **WHEN** a signed-in user activates sign-out
- **THEN** the session is removed and protected pages are no longer reachable without signing in again
