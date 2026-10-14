# EconomyTycoon Architecture

Master index for the architecture documentation. Each volume is self-contained and references the others rather than restating their definitions.

## Volume 1 — Architecture Foundation

| Document | Purpose |
|---|---|
| [PRODUCT_VISION.md](PRODUCT_VISION.md) | Product vision, pillars, MVP versus long-term scope |
| [TECHNICAL_REQUIREMENTS.md](TECHNICAL_REQUIREMENTS.md) | Functional constraints and quality attributes with measurable targets |
| [TECHNOLOGY_DECISIONS.md](TECHNOLOGY_DECISIONS.md) | Evaluation of engine and rendering approaches, recommended stack |
| [ARCHITECTURE_OVERVIEW.md](ARCHITECTURE_OVERVIEW.md) | System context, layers, dependency rules, principles |
| [SIMULATION_ENGINE.md](SIMULATION_ENGINE.md) | Simulation execution model and tick lifecycle |
| [MODULE_CATALOG.md](MODULE_CATALOG.md) | High-level domain boundaries and ownership |
| [REPOSITORY_STRATEGY.md](REPOSITORY_STRATEGY.md) | Source layout inside the app, naming, branching |
| [VOLUME_1_CLOSEOUT.md](VOLUME_1_CLOSEOUT.md) | Completed decisions, open questions, consistency checks |
| [adr/](adr/) | Architecture decision records |

## Planned volumes

2. Economic model and invariants
3. Domain entities and data schemas
4. Market, production, supply chain and workforce engines
5. Finance, banking and capital markets
6. AI competitor architecture
7. Android application, UI and rendering
8. Persistence, save and replay
9. Backend, multiplayer, security
10. Testing, performance, roadmap and task breakdown

## Status vocabulary

- **Accepted** — binding for implementation.
- **Proposed** — recommended, requires a prototype or review before it binds.
- **Open** — undecided; listed in the closeout document.
