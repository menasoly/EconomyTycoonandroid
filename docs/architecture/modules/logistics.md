# Module: logistics

- Module id: `logistics`
- Source directory: `app/src/main/java/app/many/economytycoon/logistics/`
- Responsibility: Procurement contracts, warehouses, routes, shipments, trade barriers, disruptions.
- Public API: `LogisticsEngine`, `RouteGraph`, `Shipment`, `ContractService`, `TradePolicyView`
- Dependencies: core, world, production, markets (authoritative: ARCHITECTURE_MANIFEST.yaml)
- Owned entities: Supplier, Shipment, Vehicle, Contract
- Commands handled: ProposeContract, BuyVehicle, AssignRoute, ReorderPolicy
- Events produced: ShipmentDelivered, ShipmentLost, ContractBreached
- Events consumed: TradeMatched, InfrastructureChanged, EconomicEventFired
- Tick phase(s): 5, 9
- Tests required: Routing determinism; reservation at origin; disruption; deadlock detection
- Estimated complexity: L
- Implementation priority: 4
- Parallel development suitability: Medium

## Rules

- Only the public API above may be used by other modules; everything else is `internal`.
- Changes to the public API require a change request (AI_AGENT_INSTRUCTIONS.md).
- Entities are referenced from other modules by id only.
- See COMMAND_CATALOG.md and EVENT_CATALOG.md for command and event contracts.
