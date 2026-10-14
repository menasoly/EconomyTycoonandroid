# Module: visualization

- Module id: `visualization`
- Source directory: `app/src/main/java/app/many/economytycoon/visualization/`
- Responsibility: Map, network and factory renderers from snapshots; viewport, spatial index, level of detail.
- Public API: `MapRenderer`, `FactoryRenderer`, `ViewportState`, `SpatialIndex`
- Dependencies: session (authoritative: ARCHITECTURE_MANIFEST.yaml)
- Owned entities: none
- Commands handled: none
- Events produced: none
- Events consumed: read models
- Tick phase(s): none
- Tests required: Spatial index correctness; render budget benchmarks
- Estimated complexity: L
- Implementation priority: 6
- Parallel development suitability: High

## Rules

- Only the public API above may be used by other modules; everything else is `internal`.
- Changes to the public API require a change request (AI_AGENT_INSTRUCTIONS.md).
- Entities are referenced from other modules by id only.
- See COMMAND_CATALOG.md and EVENT_CATALOG.md for command and event contracts.
