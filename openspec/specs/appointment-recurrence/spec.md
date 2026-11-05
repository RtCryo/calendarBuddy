# appointment-recurrence Specification

## Purpose
Lets the family create simple repeating appointments and change a single occurrence or the whole series.

## Requirements

### Requirement: Appointments can repeat by a simple rule
An appointment SHALL optionally repeat daily, weekly, monthly, or yearly every 1 to 99 periods. A weekly repeat SHALL occur on a non-empty set of weekdays, defaulting to the weekday of the start. A repeat SHALL end never, on an inclusive end date not before the start, or after 1 to 730 occurrences; at most one end SHALL be given. The appointment's own start SHALL be the first occurrence. Every occurrence SHALL keep the series' local start time, duration or all-day span, title, location, notes, and participants unless changed individually. Invalid rules SHALL be rejected with 400 and change nothing.

#### Scenario: Weekly lessons on two weekdays
- **WHEN** a user creates `Swimming` for Ben on Tuesday 2026-11-03 at 16:00, repeating weekly on Tuesday and Thursday until 2026-12-17
- **THEN** occurrences appear on every Tuesday and Thursday from 2026-11-03 through 2026-12-17 at 16:00

#### Scenario: Repeat a fixed number of times
- **WHEN** a user creates a daily repeat ending after 5 occurrences
- **THEN** exactly five consecutive daily occurrences exist

#### Scenario: Open-ended yearly birthday
- **WHEN** a user creates an all-day `Anna's birthday` on 2026-03-14 repeating yearly without an end
- **THEN** it appears on 14 March of every following year shown in the calendar

#### Scenario: Invalid rule
- **WHEN** a user submits an interval of 0, a weekly repeat without weekdays, or both an end date and a count
- **THEN** the system returns 400 and saves nothing

### Requirement: Occurrences follow predictable calendar semantics
Occurrences SHALL be computed in the account time zone and keep their local wall-clock time across daylight-saving changes. A monthly repeat SHALL occur on the start's day of the month and skip months that do not have that day. A yearly repeat starting on 29 February SHALL occur only in leap years. Range queries SHALL return every occurrence overlapping the requested range, including occurrences that started before it.

#### Scenario: Daylight-saving change
- **WHEN** a weekly 10:00 appointment in `Europe/Berlin` has occurrences before and after 2026-10-25
- **THEN** every occurrence is shown at 10:00 local time

#### Scenario: Monthly on the 31st
- **WHEN** a monthly repeat starts on 2026-01-31
- **THEN** occurrences exist in January, March, May, July, August, October, and December and not in months with fewer days

### Requirement: Users change one occurrence or the whole series
When editing or deleting an occurrence of a series, the user SHALL choose between this occurrence and all occurrences. Editing this occurrence SHALL replace only that occurrence with its own values, including its own participants; deleting this occurrence SHALL remove only that date. Editing all occurrences SHALL apply to the series; when only title, location, notes, or participants change, individually changed or deleted occurrences SHALL be kept; when the start, end, all-day flag, or repeat rule changes, individual changes and deletions SHALL be discarded. Deleting all occurrences SHALL remove the series with all individual changes. Each operation SHALL be atomic and protected by the series version.

#### Scenario: Move one occurrence
- **WHEN** a user moves the 2026-11-19 `Swimming` occurrence to 17:00 and chooses this occurrence
- **THEN** only that date shows 17:00 and all other occurrences stay at 16:00

#### Scenario: Skip one occurrence
- **WHEN** a user deletes the 2026-11-24 occurrence and chooses this occurrence
- **THEN** that date shows no `Swimming` and the rest of the series remains

#### Scenario: Rename the series
- **WHEN** a user renames the series to `Swim training` and chooses all occurrences
- **THEN** every occurrence shows the new title, and the moved 2026-11-19 occurrence keeps its 17:00 time

#### Scenario: Change the schedule of the series
- **WHEN** a user changes the series time to 15:00 and chooses all occurrences
- **THEN** every occurrence, including previously moved or deleted ones, follows the new rule at 15:00

#### Scenario: Filter an individually changed occurrence
- **WHEN** a single occurrence's participants are changed from Ben to Anna and the calendar is filtered to Ben
- **THEN** that occurrence is not shown in Ben's calendar but appears in Anna's
