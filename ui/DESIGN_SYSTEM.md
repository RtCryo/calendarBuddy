# calendarBuddy UI design system

calendarBuddy adopts the [finanzBuddy UI design system](../../finanzBuddy/ui/DESIGN_SYSTEM.md) as its anatomy reference and the root [`homebuddy-ui-family`](../../openspec/specs/homebuddy-ui-family/spec.md) contract. Typography, spacing, radii, elevation, focus outline, motion, breakpoints, shell, buttons, fields, tables, notices, dialogs, and empty/loading/error states are the same as in finanzBuddy, with identical CSS token names in `src/index.css`. This file records only what differs and the patterns that exist only in a calendar.

## Divergence from finanzBuddy

| Token | finanzBuddy | calendarBuddy | Verified pair |
|---|---|---|---|
| `--color-primary`, `--color-focus` | `#167D68` | `#4F46B8` indigo | 7.24:1 on white, 6.72:1 on page |
| `--color-primary-dark` | `#116653` | `#3D3594` | 9.83:1 on white, 8.49:1 on the soft surface |
| `--color-text-strong`, `--color-ink` | `#172B4D` | `#1E2A4A` | 14.14:1 on white |
| `--color-page` | `#F6F8FB` | `#F6F6FC` | muted text 5.35:1 |
| `--color-primary-soft-surface` | `#F7FBF9` | `#EEEDFA` | primary 6.25:1, muted 4.97:1 |
| `--color-primary-soft-border` | `#AFCFC4` | `#C9C6EC` | decorative only |
| Brand mark | invoice glyph | calendar glyph | white on primary 7.24:1 |

The semantic success, warning, danger, info, and neutral tokens, `--color-text`, `--color-text-muted`, `--color-control-border` (4.02:1), `--color-border`, and every spacing, size, radius, shadow, and motion token are unchanged. Finance-only tokens (amounts, metrics, drop zone, document-row hover) are not used by calendarBuddy and are omitted. Shadow tokens use the calendarBuddy navy `30 42 74`. A shared `@homebuddy/ui` package remains a follow-up once both UIs stabilize.

## Member colors

Members choose one of eight fixed keys. Each is a CSS custom property `--member-color` set by a `member-<key>` class and is used as the background of a marker that always contains the member's initials, so identity never depends on color. White initials on the marker and the marker color on white both meet 4.5:1.

| Key | Value | On white | On soft surface |
|---|---|---|---|
| `indigo` | `#4F46B8` | 7.24:1 | 6.25:1 |
| `teal` | `#116653` | 6.89:1 | 5.95:1 |
| `coral` | `#AD3D2D` | 6.03:1 | 5.21:1 |
| `rose` | `#A53059` | 6.63:1 | 5.72:1 |
| `amber` | `#895500` | 6.24:1 | 5.39:1 |
| `sky` | `#216493` | 6.35:1 | 5.48:1 |
| `violet` | `#70449A` | 7.04:1 | 6.08:1 |
| `slate` | `#46556C` | 7.56:1 | 6.53:1 |

A new color must reach 4.5:1 against white and stay distinguishable from the existing eight by more than hue alone (initials remain the identifier).

## Calendar patterns

All grids expose roles and labels, scroll inside their own region, and never cause page-level horizontal overflow at 680 px or less. The calendar time zone is the account zone (`Europe/Berlin` by default) in every view and in the editor. Dates in labels use English long form ("Tuesday, 3 November 2026") regardless of the browser locale.

| Pattern | Anatomy | States and behavior |
|---|---|---|
| Month grid | `role="grid"` with a weekday header row (Monday first, full names for assistive technology) and one row per week; a day cell holds a day-number button, up to three chips, and "+N more" | today (`aria-current="date"`, filled day number, "(today)" in the button name), outside month (tinted), focus (shared outline), hover (soft surface). Arrow keys move between day buttons and change the period at the edge; Enter creates an appointment on that day. "+N more" opens a dialog listing all of the day's appointments |
| Week time grid | Day header buttons, an all-day row, and a `region` named "Hourly schedule" with 24 hour rows (62 px) scrolling vertically and initially positioned at 07:00 | Overlapping timed chips share the column width side by side; slot buttons create an appointment at that hour; today's header uses the soft surface and "(today)" text. At narrow widths the whole grid scrolls inside its wrapper (minimum 740 px) |
| Agenda list | A named list of day groups, each a surface with an `h2` date heading and chips; only days with appointments appear | Default below 680 px and the accessible fallback of the grids; today's group is marked "(today)" |
| Appointment chip | Button with time or "All day", title, a repeat indicator (`↻`, decorative) and member markers (initials) | Accessible name: date, time, title, participants ("with Anna and Ben"), and "repeats" for series occurrences. Hover outlines in primary; focus uses the shared outline |
| Member swatch and badge | Colored circle (`member-dot`) or initials badge (`member-badge`) | Decorative swatches are `aria-hidden`; badges carry the name as visible or screen-reader text |
| Member filter | Labeled select "Show" with "Whole family" and each member name | The page heading names the selected member ("Anna's calendar") |
| Color picker | Radio group "Color", one radio per palette key with swatch and visible name | Arrow keys move the selection; the selected option shows a check mark in addition to the checked radio |
| Appointment dialog | Shared dialog: details (title, time, repeat note, participants, location, notes) then editor; scope choice, reset warning, delete confirmation, and conflict steps replace the body in place | Focus enters the dialog, Tab is trapped, Escape closes, and focus returns to the opener (or the page heading if it disappeared). Field errors are inline text tied to the field with `aria-describedby`; submission is disabled while saving |
| Empty and error states | Empty period: grid stays visible with a notice card and "Add appointment". Error: alert notice with Retry. Loading: status text | An empty period is never styled as a failure |

## Responsive rules

| Width | Behavior |
|---|---|
| Wide (above 900 px) | Fixed 248 px sidebar; month view default |
| Tablet (681–900 px) | 210 px sidebar (`--sidebar-width-compact`); month view default |
| Narrow (680 px or less) | Sidebar becomes a horizontal navigation strip with sign-out kept; agenda default when the URL has no view; month cells shrink to 100 px; week grid scrolls within its wrapper; two-column form rows become one column |

## Accessibility and motion

- Focus is a 3 px indigo outline with a 2 px offset on every interactive element.
- Every control has a visible label or accessible name; icon-only controls (previous/next period) are named.
- Text pairs meet 4.5:1 and essential control boundaries 3:1; the pairs above are the verified set, and chip text uses `--color-primary-dark` on the soft surface (8.49:1).
- Under `prefers-reduced-motion: reduce` all animations and transitions are shortened to 0.01 ms; opening a dialog or changing the period still changes content immediately.

## Exceptions and change checklist

Divergence from the finanzBuddy anatomy must be recorded in the first table with a rationale. When adding a screen: reuse the shared classes and tokens, check text and control contrast, label every control, handle loading, empty, and error states, test the keyboard path, and check wide, tablet, and narrow layouts.
