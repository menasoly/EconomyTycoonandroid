# Module: infra

- Module id: `infra`
- Source directory: `app/src/main/java/app/many/economytycoon/infra/`
- Responsibility: Logging, diagnostics, dispatchers, build-time flags, time abstraction for non-simulation code.
- Public API: `AppDispatchers`, `Logger`, `Diagnostics.report(...)`, `BuildFlags`
- Dependencies: none (authoritative: ARCHITECTURE_MANIFEST.yaml)
- Owned entities: none
- Commands handled: none
- Events produced: none
- Events consumed: none
- Tick phase(s): none
- Tests required: Unit tests for dispatcher wiring and diagnostics buffer
- Estimated complexity: S
- Implementation priority: 1
- Parallel development suitability: High

## Rules

- Only the public API above may be used by other modules; everything else is `internal`.
- Changes to the public API require a change request (AI_AGENT_INSTRUCTIONS.md).
- Entities are referenced from other modules by id only.
- See COMMAND_CATALOG.md and EVENT_CATALOG.md for command and event contracts.
