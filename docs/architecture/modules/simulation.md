# Module: simulation

- Module id: `simulation`
- Source directory: `app/src/main/java/app/many/economytycoon/simulation/`
- Responsibility: Clock, scheduler, tick pipeline, event queue, coordinator, snapshots, replay, invariant phase.
- Public API: `SimulationCoordinator.submit(cmd)`, `advance(ticks)`, `snapshot()`, `ReplayEngine`, `EventQueue`
- Dependencies: core, finance, world, workforce, production, markets, logistics, companies, macro, competitors (authoritative: ARCHITECTURE_MANIFEST.yaml)
- Owned entities: SimulationTick, SimulationSnapshot, EconomicEvent
- Commands handled: processes all commands
- Events produced: TickCompleted, EconomicEventFired
- Events consumed: all domain events (routes to subscribers)
- Tick phase(s): all
- Tests required: Determinism, replay, golden scenarios, invariant phase
- Estimated complexity: XL
- Implementation priority: 4
- Parallel development suitability: Low: critical path

## Rules

- Only the public API above may be used by other modules; everything else is `internal`.
- Changes to the public API require a change request (AI_AGENT_INSTRUCTIONS.md).
- Entities are referenced from other modules by id only.
- See COMMAND_CATALOG.md and EVENT_CATALOG.md for command and event contracts.
