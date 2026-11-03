# Spec Delta

## Purpose

Keeps calendarBuddy visually consistent with finanzBuddy while giving it a recognizable identity, and defines the calendar-specific patterns.

## ADDED Requirements

### Requirement: calendarBuddy shares the finanzBuddy component anatomy
calendarBuddy SHALL maintain its own canonical design-system reference that adopts the finanzBuddy design-system anatomy, token names, typography, spacing, radii, elevation, focus, motion, responsive breakpoints, and accessibility rules. Buttons, fields, selectors, notices, tables, pagination, dialogs, and empty/loading/error states SHALL keep the same shape, sizing, and interaction states as in finanzBuddy. Deviations SHALL be recorded with their rationale in the calendarBuddy reference.

#### Scenario: Compare shared components
- **WHEN** a user sees a primary button, text field, table, or notice in both services
- **THEN** shape, size, typography, and interaction states are the same and only accent colors differ

#### Scenario: A contributor adds a calendarBuddy screen
- **WHEN** a new calendarBuddy screen is built
- **THEN** it uses the documented shared anatomy and tokens rather than undocumented one-off values

### Requirement: calendarBuddy has a distinct accessible palette and brand
calendarBuddy SHALL use an indigo primary palette instead of finanzBuddy's teal for primary actions, focus, active navigation, and soft accent surfaces, and a calendar brand mark with the name calendarBuddy. Semantic success, warning, danger, info, and neutral tokens SHALL remain identical to finanzBuddy. Every text pair SHALL meet 4.5:1 and every essential control or state boundary 3:1 contrast.

#### Scenario: Identify the service
- **WHEN** a user opens calendarBuddy after using finanzBuddy
- **THEN** the brand name, mark, and accent color make clear it is a different service

#### Scenario: Contrast of the primary palette
- **WHEN** primary text, focus outlines, or primary buttons are rendered on their documented backgrounds
- **THEN** they meet the documented WCAG 2.2 AA thresholds

### Requirement: Calendar patterns are documented and accessible
The reference SHALL define the month grid, week time grid, agenda list, appointment chip, member color swatch, member filter, and color picker, including their states (today, selected, focused, hovered, overflow "+N more", empty) and wide, tablet, and narrow behavior. The eight member colors SHALL each meet 4.5:1 against white and SHALL always be accompanied by the member's name or initials. Grids SHALL expose meaningful roles and labels and SHALL scroll within their own regions rather than the page.

#### Scenario: Member color without color perception
- **WHEN** a user who cannot distinguish colors views appointments of several members
- **THEN** each appointment's members are identifiable by visible or accessible text

#### Scenario: Reduced motion
- **WHEN** the user prefers reduced motion and changes period or opens an appointment
- **THEN** non-essential transitions are removed without hiding the state change
