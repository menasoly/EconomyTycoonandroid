# Module: session

- Module id: `session`
- Source directory: `app/src/main/java/app/many/economytycoon/session/`
- Responsibility: Application services: game session lifecycle, command submission, read models, new game and scenario flow, autosave policy.
- Public API: `GameSession`, `ReadModels` (StateFlow), `NewGameService`
- Dependencies: simulation, persistence, infra (authoritative: ARCHITECTURE_MANIFEST.yaml)
- Owned entities: none (session state only)
- Commands handled: forwards commands
- Events produced: SessionStateChanged
- Events consumed: TickCompleted, SaveCompleted
- Tick phase(s): publish
- Tests required: Session lifecycle, read model throttling, rejection mapping
- Estimated complexity: M
- Implementation priority: 5
- Parallel development suitability: Medium

## Rules

- Only the public API above may be used by other modules; everything else is `internal`.
- Changes to the public API require a change request (AI_AGENT_INSTRUCTIONS.md).
- Entities are referenced from other modules by id only.
- See COMMAND_CATALOG.md and EVENT_CATALOG.md for command and event contracts.
