# Module: world

- Module id: `world`
- Source directory: `app/src/main/java/app/many/economytycoon/world/`
- Responsibility: Geography, population, resources, climate, infrastructure graphs, real estate.
- Public API: `WorldGenerator`, `WorldQuery`, `RegionId`, `CityId`, `PopulationModel`, `InfrastructureGraph`
- Dependencies: core, content (authoritative: ARCHITECTURE_MANIFEST.yaml)
- Owned entities: World, Region, Country, City, PopulationSegment, Parcel
- Commands handled: BuyParcel, LeaseParcel
- Events produced: PopulationChanged, ResourceDepleted, InfrastructureChanged
- Events consumed: EconomicEventFired
- Tick phase(s): 1, 4
- Tests required: Seeded generation golden hashes; migration balance; depletion
- Estimated complexity: L
- Implementation priority: 3
- Parallel development suitability: High after content

## Rules

- Only the public API above may be used by other modules; everything else is `internal`.
- Changes to the public API require a change request (AI_AGENT_INSTRUCTIONS.md).
- Entities are referenced from other modules by id only.
- See COMMAND_CATALOG.md and EVENT_CATALOG.md for command and event contracts.
