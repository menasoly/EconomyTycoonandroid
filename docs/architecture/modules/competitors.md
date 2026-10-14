# Module: competitors

- Module id: `competitors`
- Source directory: `app/src/main/java/app/many/economytycoon/competitors/`
- Responsibility: AI agents using the player's command interface: observation, utility scoring, planning, memory, personalities.
- Public API: `Agent`, `Observation`, `AgentProfile`, `AgentScheduler`, `CommandSink`
- Dependencies: core, companies, markets, production, logistics (authoritative: ARCHITECTURE_MANIFEST.yaml)
- Owned entities: AgentState (memory, profile)
- Commands handled: emits any player command
- Events produced: AgentDecisionLogged
- Events consumed: OrderFilled, CompanyBankrupt, PriceChanged
- Tick phase(s): 2
- Tests required: Constraint fuzzing; fixed-observation decision fixtures; long-run survival
- Estimated complexity: XL
- Implementation priority: 5
- Parallel development suitability: High once interfaces are fixed

## Rules

- Only the public API above may be used by other modules; everything else is `internal`.
- Changes to the public API require a change request (AI_AGENT_INSTRUCTIONS.md).
- Entities are referenced from other modules by id only.
- See COMMAND_CATALOG.md and EVENT_CATALOG.md for command and event contracts.
