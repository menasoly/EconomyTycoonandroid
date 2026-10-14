# EconomyTycoon Architecture

Master index. Each document has one purpose and references the others instead of restating their definitions. `ARCHITECTURE_MANIFEST.yaml` is authoritative for module ids and dependencies.

## Foundation

| Document | Purpose |
|---|---|
| [PRODUCT_VISION.md](PRODUCT_VISION.md) | Vision, pillars, MVP versus long-term scope |
| [TECHNICAL_REQUIREMENTS.md](TECHNICAL_REQUIREMENTS.md) | Constraints and quality attributes |
| [TECHNOLOGY_DECISIONS.md](TECHNOLOGY_DECISIONS.md) | Engine and rendering evaluation, recommended stack |
| [ARCHITECTURE_OVERVIEW.md](ARCHITECTURE_OVERVIEW.md) | Context, layers, principles |
| [REPOSITORY_STRATEGY.md](REPOSITORY_STRATEGY.md) | Source layout, branching |
| [MODULE_CATALOG.md](MODULE_CATALOG.md) | Domain boundaries |
| [DEPENDENCY_GRAPH.md](DEPENDENCY_GRAPH.md) | Allowed dependencies diagram |
| [ARCHITECTURE_MANIFEST.yaml](ARCHITECTURE_MANIFEST.yaml) | Machine-readable modules, dependencies, tick phases |
| [adr/](adr/) | Architecture decision records |
| [VOLUME_1_CLOSEOUT.md](VOLUME_1_CLOSEOUT.md) | Decisions and open questions after Volume 1 |

## Simulation and economy

| Document | Purpose |
|---|---|
| [SIMULATION_ENGINE.md](SIMULATION_ENGINE.md) | Tick lifecycle, determinism, snapshots |
| [ECONOMIC_MODEL.md](ECONOMIC_MODEL.md) | Numeric model, invariants, damping |
| [DOMAIN_MODEL.md](DOMAIN_MODEL.md) | Entities and ownership |
| [MARKET_ENGINE.md](MARKET_ENGINE.md) | Orders, clearing, demand |
| [FINANCIAL_ENGINE.md](FINANCIAL_ENGINE.md) | Ledger, settlement, debt, equity |
| [MANUFACTURING_ENGINE.md](MANUFACTURING_ENGINE.md) | Recipes and production |
| [SUPPLY_CHAIN_ENGINE.md](SUPPLY_CHAIN_ENGINE.md) | Procurement, shipments, trade |
| [WORKFORCE_ENGINE.md](WORKFORCE_ENGINE.md) | Cohort workforce model |
| [WORLD_SIMULATION.md](WORLD_SIMULATION.md) | Geography, population, macro, government |
| [AI_COMPETITOR_ENGINE.md](AI_COMPETITOR_ENGINE.md) | Agent architecture |

## Application, data and platform

| Document | Purpose |
|---|---|
| [ANDROID_ARCHITECTURE.md](ANDROID_ARCHITECTURE.md) | App structure, threading, navigation |
| [UI_UX_SPECIFICATION.md](UI_UX_SPECIFICATION.md) | Screen catalog and UX rules |
| [RENDERING_ARCHITECTURE.md](RENDERING_ARCHITECTURE.md) | Maps and factory visualization |
| [DATA_ARCHITECTURE.md](DATA_ARCHITECTURE.md) | Stores, schemas, content compatibility |
| [SAVE_AND_REPLAY.md](SAVE_AND_REPLAY.md) | Saves, journal, replay |
| [MULTIPLAYER_ARCHITECTURE.md](MULTIPLAYER_ARCHITECTURE.md) | Optional server-authoritative backend |
| [SECURITY_ARCHITECTURE.md](SECURITY_ARCHITECTURE.md) | Security, anti-cheat, entitlements, monetization |

## Quality and delivery

| Document | Purpose |
|---|---|
| [TEST_STRATEGY.md](TEST_STRATEGY.md) | Test layers and golden scenarios |
| [PERFORMANCE_BUDGETS.md](PERFORMANCE_BUDGETS.md) | Budgets and benchmark plan |
| [IMPLEMENTATION_ROADMAP.md](IMPLEMENTATION_ROADMAP.md) | Phases, gates, vertical slice |
| [FIRST_100_TASKS.md](FIRST_100_TASKS.md) | Initial task breakdown |
| [RISK_REGISTER.md](RISK_REGISTER.md) | Risks and mitigations |
| [AI_AGENT_INSTRUCTIONS.md](AI_AGENT_INSTRUCTIONS.md) | Rules for implementation agents |
| [CODING_STANDARDS.md](CODING_STANDARDS.md) | Code and commit standards |
| [MODULE_TEMPLATE.md](MODULE_TEMPLATE.md) | Per-module spec template |
| [TASK_TEMPLATE.md](TASK_TEMPLATE.md) | Task spec template |
| [INTEGRATION_GUIDE.md](INTEGRATION_GUIDE.md) | Branches, PRs, gates |
