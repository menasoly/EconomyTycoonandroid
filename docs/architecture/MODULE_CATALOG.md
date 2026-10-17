# Module Catalog (High-Level Domain Boundaries)

Modules are folders inside the app source tree, not Gradle modules or separate packages published as libraries. Boundaries are enforced by dependency rules and architecture tests.

| Id | Domain | Responsibility | May depend on |
|---|---|---|---|
| core | Core primitives | Ids, fixed-point money and quantity, time, result types, deterministic random | none |
| content | Content definitions | Industry, good, recipe and facility definitions plus validation | core |
| world | World and geography | Regions, cities, population, resources, infrastructure | core, content |
| companies | Corporate | Companies, ownership, structure, objectives | core, content, finance |
| finance | Financial engine | Ledger, accounts, loans, settlement, statements | core |
| markets | Market engine | Orders, clearing, price formation, demand coupling | core, content, world, finance |
| production | Manufacturing | Facilities, lines, recipes, capacity, quality | core, content, workforce |
| logistics | Supply chain | Shipments, routes, warehouses, trade | core, world, production, markets |
| workforce | Employment | Employee aggregates, hiring, wages, morale | core, finance |
| macro | Macroeconomy | Indicators, policy, central bank | core, markets, finance, world |
| competitors | AI | Agent profiles, observation, planning, command output | core, domain read interfaces |
| simulation | Orchestration | Clock, scheduler, coordinator, events, snapshots | all domain modules |
| session | Application services | Game session, use cases, read models | simulation, persistence |
| persistence | Data | Room entities, snapshots, journal, migrations | core, domain types |
| ui | Presentation | Screens, navigation, design system | session |
| visualization | Rendering | Map and factory views from snapshots | session |
| infra | Infrastructure | Logging, diagnostics, dispatchers | none |

## Rules

- A module exposes a small public API; everything else is internal.
- Cross-domain interaction goes through commands, events and read interfaces.
- No cycles. Cyclic needs are resolved by moving shared types down into core or an interface into the lower module.
- Each module owns its entities; others reference them by id only.
- A new module requires an ADR.

Later volumes add per-module detail: public API, owned entities, events produced and consumed, tests, complexity and priority.
| blueprint | Facility blueprints | Capture facility layouts, share them as text codes, plan and apply them as ordinary commands | core, content, world, workforce, production, companies, facilityprofit, simulation, session, persistence |
| marketing | Marketing | Campaigns, channels, awareness and loyalty stocks, demand lift, optimiser | core, content, finance, markets, world, advisor |
