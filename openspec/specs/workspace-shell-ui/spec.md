# workspace-shell-ui Specification

## Purpose
Defines the authenticated calendarBuddy workspace shell and its navigation.

## Requirements

### Requirement: The workspace shell identifies calendarBuddy and its destinations
The authenticated workspace SHALL use the shared application-shell anatomy with the calendarBuddy brand mark and name, navigation links to Calendar and Family, a breadcrumb, the signed-in email, and sign-out. The workspace root SHALL open the Calendar. Unknown authenticated routes SHALL show a not-found state with a link to the Calendar.

#### Scenario: View workspace navigation
- **WHEN** an authenticated user views the workspace shell
- **THEN** it shows the calendarBuddy brand and contains Calendar and Family links with the current page marked as active

#### Scenario: Open the workspace root
- **WHEN** an authenticated user opens `/`
- **THEN** the user is taken to `/calendar`

#### Scenario: Narrow viewport
- **WHEN** the shell is viewed at 680 px or less
- **THEN** navigation becomes a horizontal strip and sign-out remains available
