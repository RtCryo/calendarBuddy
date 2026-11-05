# appointments Specification

## Purpose
Stores the family's appointments, their participants, and timing so the calendar can show them for the whole family or one member.

## Requirements

### Requirement: Users manage private appointments
The system SHALL let an authenticated user create, read, update, and delete only their own appointments through `/api/appointments`. An appointment SHALL have a title trimmed to 1 to 200 characters, optional location up to 200 characters, optional notes up to 2000 characters, and one or more participants that are members of the same account. Invalid input SHALL be rejected with 400 and field errors; ids of another account SHALL be answered with 404. No rejected request SHALL change data.

#### Scenario: Create an appointment for one member
- **WHEN** a user creates `Dentist` for member Anna on 2026-11-10 from 09:00 to 09:30
- **THEN** the appointment is saved with Anna as its only participant

#### Scenario: Create an appointment for several members
- **WHEN** a user creates `Parents' evening` for two members
- **THEN** the appointment is saved once and belongs to both members

#### Scenario: Reject an appointment without participants
- **WHEN** a user submits an appointment with no participants
- **THEN** the system returns 400 and saves nothing

#### Scenario: Reject a foreign participant
- **WHEN** a user submits a participant id belonging to another account
- **THEN** the system rejects the request without revealing that member

### Requirement: Appointments are timed or all-day
A timed appointment SHALL have a start and end instant with the end not before the start and a duration of at most 31 days. An all-day appointment SHALL have a start date and an inclusive end date not before the start date spanning at most 31 days, and SHALL NOT be shifted by time-zone or daylight-saving changes. Timed values SHALL be exchanged as ISO-8601 instants with offset and presented in the account time zone.

#### Scenario: Multi-day all-day appointment
- **WHEN** a user creates an all-day `Holiday` from 2026-12-21 to 2026-12-23
- **THEN** it is shown on exactly those three dates

#### Scenario: End before start
- **WHEN** a user submits an end before the start
- **THEN** the system returns 400 with an error on the end field

#### Scenario: Daylight-saving transition
- **WHEN** a timed appointment is saved for 2026-10-25 at 10:00 in `Europe/Berlin`
- **THEN** it is returned and shown at 10:00 local time on that date

### Requirement: Appointments can be queried by period and member
`GET /api/appointments` SHALL accept a date range `from` (inclusive) and `to` (exclusive) in the account time zone of at most 100 days and an optional `memberId`. It SHALL return one item per own one-off appointment and per repeating occurrence that overlaps the range, with participants, ordered by start and then title. Each item SHALL identify its appointment, its occurrence date, and whether it belongs to a series. With `memberId`, it SHALL return only items that member participates in. A larger or inverted range SHALL be rejected with 400.

#### Scenario: Query the whole family for a month view
- **WHEN** the UI requests the range covering the visible weeks of November 2026 without `memberId`
- **THEN** all family appointments overlapping those weeks are returned, including ones that started earlier

#### Scenario: Query one member
- **WHEN** the UI requests the same range with Anna's `memberId`
- **THEN** only appointments with Anna as a participant are returned, including shared ones

### Requirement: Concurrent edits do not overwrite each other silently
Each appointment SHALL carry a version. An update or delete with a stale version SHALL be rejected with 409 and leave the stored appointment unchanged.

#### Scenario: Stale update
- **WHEN** two browser tabs edit the same appointment and the second saves after the first
- **THEN** the second save receives 409 and the first save's values remain
