# calendar-view-ui Specification

## Purpose
Presents the family's appointments as a calendar for the whole family or one member and lets the user create, inspect, edit, and delete appointments.

## Requirements

### Requirement: The calendar offers month, week, and agenda views
The `/calendar` page SHALL provide month, week, and agenda views with weeks starting on Monday. The month view SHALL be the default on wide and tablet viewports and the agenda view the default on narrow viewports. Today SHALL be indicated by text or accessible name as well as visual emphasis. The month view SHALL show up to three appointment chips per day and a "+N more" control that opens that day's appointments. The week view SHALL show an all-day row and an hourly grid that scrolls within its own region and initially shows 07:00; overlapping timed appointments SHALL be laid out side by side. The agenda view SHALL list the visible period grouped by day.

#### Scenario: Open the calendar
- **WHEN** a signed-in user opens `/calendar` on a wide viewport
- **THEN** the current month is shown with today marked and the family's appointments in their days

#### Scenario: Busy day in month view
- **WHEN** a day has five appointments in month view
- **THEN** three chips and a "+2 more" control are shown, and activating it reveals all five

#### Scenario: Overlapping appointments in week view
- **WHEN** two timed appointments overlap on one day in week view
- **THEN** both are fully readable side by side within that day's column

#### Scenario: Narrow viewport
- **WHEN** a user opens the calendar at 680 px or less
- **THEN** the agenda view is shown without page-level horizontal overflow

### Requirement: Users navigate periods and the state is shareable
The page SHALL provide previous, next, and Today actions and a view switcher. The view, reference date, and member filter SHALL be kept in the URL so that reloads, browser history, and copied links restore the same calendar. Loading, empty, and error states SHALL follow the design system and an empty period SHALL NOT look like a loading failure.

#### Scenario: Navigate and reload
- **WHEN** a user moves to the next month, selects Anna, and reloads the page
- **THEN** the same month, view, and member filter are shown

#### Scenario: Empty period
- **WHEN** the visible period has no appointments
- **THEN** the grid is shown with a notice that there are no appointments and an action to add one

### Requirement: Users can view the whole family or one member
The page SHALL provide a labeled filter with "Whole family" and each family member shown with color swatch and name. With a member selected, only that member's appointments SHALL be shown and the page heading SHALL name the member. Every appointment chip SHALL show its time or "All day", its title, and its participants' color markers with names or initials available as text or accessible name, so membership never depends on color alone. Without any family members, the calendar SHALL direct the user to add the first member.

#### Scenario: Filter by member
- **WHEN** a user selects Anna in the filter
- **THEN** the calendar shows only appointments Anna participates in and the heading reads that it is Anna's calendar

#### Scenario: Shared appointment in family view
- **WHEN** an appointment has Anna and Ben as participants in the whole-family view
- **THEN** it is shown once with both members' markers and names

#### Scenario: No members yet
- **WHEN** a user without family members opens the calendar
- **THEN** the page explains that a member is needed and links to adding one on the family page

### Requirement: Users create, inspect, edit, and delete appointments in a dialog
Selecting an appointment SHALL open a details dialog using the shared detail-panel pattern with title, time, participants, location, and notes, and Edit and Delete actions. An Add appointment action and activating an empty day or time slot SHALL open the editor with the start prefilled; with a member filter active, that member SHALL be preselected. The editor SHALL provide title, all-day toggle, date and time fields, participant checkboxes, location, and notes, validate required fields and date order inline, and disable submission while saving. Delete SHALL require confirmation. After success the calendar SHALL refresh; after a 409 conflict the dialog SHALL explain that the appointment changed and offer to reload it. Closing the dialog SHALL return focus to its opener.

#### Scenario: Create from an empty day
- **WHEN** a user activates an empty 2026-11-12 cell while Ben is selected in the filter
- **THEN** the editor opens with 2026-11-12 as date and Ben as participant

#### Scenario: Validation in the editor
- **WHEN** a user saves without a title or with an end before the start
- **THEN** the editor shows the errors next to the fields and sends no request

#### Scenario: Delete an appointment
- **WHEN** a user deletes an appointment and confirms
- **THEN** the appointment disappears from the calendar and focus returns to a sensible place in the calendar

#### Scenario: Two devices edit the same appointment
- **WHEN** an appointment was changed on another device after the dialog was opened and the user saves
- **THEN** the dialog explains that the appointment changed and offers to reload it without losing the calendar view

#### Scenario: Keyboard use
- **WHEN** a user navigates days and appointments with the keyboard
- **THEN** every day, appointment, and dialog control is reachable with visible focus and an accessible name including date and title

### Requirement: Users create and change repeating appointments in the editor
The editor SHALL offer a Repeat selector with Does not repeat, Daily, Weekly, Monthly, and Yearly, an interval field, weekday toggles for weekly repeats preselecting the start's weekday, and an end choice of Never, On date, or After a number of occurrences, with inline validation. Occurrences of a series SHALL show a repeat indicator with accessible text in chips, agenda rows, and details. Saving or deleting an occurrence of a series SHALL first ask whether to apply the change to this occurrence or all occurrences, defaulting to this occurrence. Before saving a schedule change to all occurrences, the UI SHALL warn that individually changed or deleted occurrences will be reset.

#### Scenario: Create a weekly repeat
- **WHEN** a user chooses Weekly, selects Tuesday and Thursday, and ends on a date
- **THEN** the saved series appears on those weekdays in month, week, and agenda views with a repeat indicator

#### Scenario: Choose the scope of a change
- **WHEN** a user saves a change to an occurrence of a series
- **THEN** the UI asks for this occurrence or all occurrences before sending the request, and canceling the choice sends nothing

#### Scenario: Warn before resetting exceptions
- **WHEN** a user changes the start time of all occurrences of a series that has a moved occurrence
- **THEN** the UI warns that individual changes will be reset and saves only after confirmation
