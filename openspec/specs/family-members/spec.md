# family-members Specification

## Purpose
Lets an account maintain the profiles of its family members so appointments can be assigned to and filtered by each person.

## Requirements

### Requirement: The whole family shares one account with full access
One homeBuddy account SHALL represent one family. Every authenticated session of that account, on any number of devices at the same time, SHALL be able to view, add, edit, and delete all family members and all appointments of the account. The system SHALL NOT restrict access per family member.

#### Scenario: Two family members on different devices
- **WHEN** two family members are signed in with the family account on a phone and a laptop and one adds an appointment for the other
- **THEN** both see the appointment after their calendar refreshes and either can edit or delete it

#### Scenario: Edit another member's appointment
- **WHEN** a session edits an appointment whose participants do not include the person using the device
- **THEN** the edit is allowed

### Requirement: Users manage private family member profiles
The system SHALL let an authenticated user list, add, edit, and remove only their own family members through `/api/family-members`. A member SHALL have a name, trimmed and 1 to 100 characters, unique within the account ignoring case, and a color chosen from the fixed palette `indigo`, `teal`, `coral`, `rose`, `amber`, `sky`, `violet`, `slate`. An account SHALL have at most 20 members. Members SHALL NOT have credentials. Lists SHALL be returned in the user's order of creation.

#### Scenario: Add a member
- **WHEN** a user adds a member named `Anna` with color `coral`
- **THEN** the member is saved for that account and appears in the member list and calendar filter

#### Scenario: Reject a duplicate name
- **WHEN** a user adds ` anna ` while `Anna` already exists in their family
- **THEN** the system returns 409 and no member is created

#### Scenario: Reject an unknown color or exceeded limit
- **WHEN** a user submits a color outside the palette or adds a 21st member
- **THEN** the system returns 400 and no member is created

#### Scenario: Access another account's member
- **WHEN** a user reads, edits, or removes a member id that belongs to another account
- **THEN** the system returns 404 and changes nothing

#### Scenario: Edit a member
- **WHEN** a user renames a member or changes their color
- **THEN** existing appointments of that member show the new name and color

### Requirement: Removing a member is explicit and atomic
Before removal the system SHALL report how many appointments would be deleted because the member is their only participant and how many shared appointments would only lose this participant, counting a repeating series and each detached occurrence once. Removal SHALL apply both effects, including deleting the exceptions of deleted series, and delete the member in one transaction.

#### Scenario: Preview removal impact
- **WHEN** a user requests the removal impact of a member with 3 own and 2 shared appointments
- **THEN** the system reports 3 appointments to delete and 2 to keep without that member

#### Scenario: Remove a member
- **WHEN** a user confirms removal of that member
- **THEN** the 3 own appointments are deleted, the 2 shared appointments keep their other participants, and the member no longer exists

#### Scenario: Removal fails
- **WHEN** any part of the removal fails
- **THEN** the member and all appointments remain unchanged

### Requirement: The family page manages members
The UI SHALL provide a `/family` page listing members with color swatch, name, and upcoming appointment count, an Add member action, an edit dialog with name field and an accessible color choice showing each color's name, and a remove action that shows the impact before confirmation. Empty, loading, and error states SHALL follow the design system.

#### Scenario: Empty family
- **WHEN** a user without members opens `/family`
- **THEN** the page explains that members are needed to plan appointments and offers Add member

#### Scenario: Choose a color by keyboard
- **WHEN** a user selects a member color using only the keyboard
- **THEN** each color is reachable, announced by its name, and the selected color is indicated by more than color alone
