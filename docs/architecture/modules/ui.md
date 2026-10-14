# Module: ui

- Module id: `ui`
- Source directory: `app/src/main/java/app/many/economytycoon/ui/`
- Responsibility: Compose screens, navigation, design system, view models.
- Public API: Screens and `*ViewModel` per SCREEN_SPECS.md
- Dependencies: session, infra (authoritative: ARCHITECTURE_MANIFEST.yaml)
- Owned entities: none
- Commands handled: issues commands through session
- Events produced: none
- Events consumed: read models
- Tick phase(s): none
- Tests required: Compose tests, screenshot tests, accessibility checks
- Estimated complexity: XL
- Implementation priority: 5
- Parallel development suitability: High per screen

## Rules

- Only the public API above may be used by other modules; everything else is `internal`.
- Changes to the public API require a change request (AI_AGENT_INSTRUCTIONS.md).
- Entities are referenced from other modules by id only.
- See COMMAND_CATALOG.md and EVENT_CATALOG.md for command and event contracts.
