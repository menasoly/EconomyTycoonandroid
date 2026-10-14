# Implementation Roadmap

Complexity: S/M/L/XL, relative sizing only.

| Phase | Deliverables | Depends on | Acceptance | Main risks | Complexity |
|---|---|---|---|---|---|
| 0 Architecture foundation | Volumes 1-10, manifest | none | Documents reviewed, open questions resolved or scheduled | Over-design | M |
| 1 App foundation | Composition root, navigation, design system, persistence skeleton, architecture tests | 0 | App runs, architecture tests green | Boundary drift | L |
| 2 Simulation foundation | Core primitives, clock, scheduler, RNG, ledger, minimal markets | 1 | Determinism and ledger property tests green | Numeric bugs | L |
| 3 Business operations | Facilities, production, inventory, workforce, procurement, logistics | 2 | Playable farm-to-shop chain | Bottleneck instability | XL |
| 4 Market intelligence | Consumer demand, dynamic pricing, AI competitors, events | 3 | AI companies survive and compete in golden scenarios | AI instability | XL |
| 5 Advanced economy | Banking, capital markets, government, trade, macro | 4 | Recession scenario behaves plausibly | Feedback instability | XL |
| 6 World expansion | Geography, infrastructure, more industries, research | 5 | New industry added by data only | Content volume | L |
| 7 Advanced gameplay | Campaigns, challenges, achievements, scenarios, advanced visualization | 6 | Scenario pack playable | Scope creep | L |
| 8 Cloud and multiplayer | Accounts, cloud saves, server shard, trading | 7 | Server-authoritative trade validated | Cost, cheating | XL |
| 9 Hardening | Profiling, optimization, accessibility, security, release | all | Budgets met on target devices | Late performance issues | L |

## Early vertical slice (end of Phase 3)

One region, farm to food processing to retail, the player plus a few AI rivals, ledger-backed finances, save and load, deterministic replay. This slice is the integration gate for the platform before breadth is added.

## MVP versus expansion

MVP: Phases 1-4 limited to a few industries. Expansion: Phases 5-9 plus content growth.

## Codebase scale (relative)

Largest areas by expected complexity: domain engines (markets, production, finance), AI, UI screens, persistence and migration, rendering. Opportunities for sharing: pure-Kotlin engine reusable on a server; golden vectors shared with iOS. Duplication risks: separate AI and player command paths, per-industry code, per-screen formatting helpers. Size is not a goal; breadth is justified only by gameplay systems listed above.
