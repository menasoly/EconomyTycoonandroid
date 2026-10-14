# Module: persistence

- Module id: `persistence`
- Source directory: `app/src/main/java/app/many/economytycoon/persistence/`
- Responsibility: Room database, snapshot encoding, command journal, statistics history, migrations, autosave.
- Public API: `SaveRepository`, `SnapshotCodec`, `JournalStore`, `HistoryStore`, `Migrator`
- Dependencies: core, simulation, infra (authoritative: ARCHITECTURE_MANIFEST.yaml)
- Owned entities: Save slots, snapshot blobs, journal rows, history series
- Commands handled: none
- Events produced: SaveCompleted, SaveFailed
- Events consumed: TickCompleted
- Tick phase(s): 14 (checkpoint scheduling)
- Tests required: Round trip, corruption recovery, per-version migration fixtures
- Estimated complexity: L
- Implementation priority: 4
- Parallel development suitability: High after snapshot contract

## Rules

- Only the public API above may be used by other modules; everything else is `internal`.
- Changes to the public API require a change request (AI_AGENT_INSTRUCTIONS.md).
- Entities are referenced from other modules by id only.
- See COMMAND_CATALOG.md and EVENT_CATALOG.md for command and event contracts.
