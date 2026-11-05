# calendarBuddy contributor instructions

This repository is an independent homeBuddy service. Write code, comments, documentation, specifications, and commit messages in English.

Read `docs/ARCHITECTURE.md`, the relevant OpenSpec capability, and the root homeBuddy contract before changing authentication, service boundaries, routing, ports, or shared UI foundations. Cross-service contracts and umbrella rollout plans belong in the root `openspec/`; calendarBuddy behavior belongs here.

Keep `docs/ARCHITECTURE.md` aligned with code and Compose configuration. Keep `docs/README.md` linked to current capabilities, implementation entry points, and archived changes. Validate changes with `openspec validate <name> --strict`.

The UI follows `ui/DESIGN_SYSTEM.md` and `ui/AGENTS.md`. Use the same token names and component anatomy as finanzBuddy, with the calendarBuddy palette and calendar patterns documented there.
