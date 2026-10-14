# Module: production

- Module id: `production`
- Source directory: `app/src/main/java/app/many/economytycoon/production/`
- Responsibility: Facilities, production lines, machines, recipes execution, inventory, quality, maintenance, construction projects.
- Public API: `ProductionEngine`, `InventoryService.reserve/consume/add`, `Facility`, `ProductionLine`, `ConstructionProject`
- Dependencies: core, content, workforce (authoritative: ARCHITECTURE_MANIFEST.yaml)
- Owned entities: Facility, ProductionLine, Machine, Inventory, Warehouse stock
- Commands handled: BuildFacility, ConfigureLine, SetPriority, StartMaintenance, UpgradeMachine
- Events produced: FacilityCompleted, BatchProduced, LineBlocked, MachineBroken
- Events consumed: ShipmentDelivered, WorkersHired
- Tick phase(s): 6, construction in 3
- Tests required: INV-03/04; yield curves; bottleneck cases; circular dependency rejection
- Estimated complexity: XL
- Implementation priority: 3
- Parallel development suitability: Medium

## Rules

- Only the public API above may be used by other modules; everything else is `internal`.
- Changes to the public API require a change request (AI_AGENT_INSTRUCTIONS.md).
- Entities are referenced from other modules by id only.
- See COMMAND_CATALOG.md and EVENT_CATALOG.md for command and event contracts.
