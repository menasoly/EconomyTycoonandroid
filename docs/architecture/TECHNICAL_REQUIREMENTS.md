# Technical Requirements

Targets are design goals to be validated by benchmarks, not measured results.

## Platform

- Android, Kotlin, Jetpack Compose, Coroutines and Flow.
- Phones and tablets; adaptive layouts.
- Room (SQLite) for persistence.
- Minimum SDK and device tiers are defined in the performance budgets volume.

## Functional constraints

- The simulation is deterministic: identical seed, content and command log produce identical state.
- The simulation has no dependency on Android, Compose or navigation.
- All value movements (money, goods) are conserved or explicitly created or destroyed by named rules.
- AI companies act through the same command interface as the player.
- Content (industries, recipes, goods) is data, loaded and validated at startup.

## Quality attributes

| Attribute | Requirement | Validation |
|---|---|---|
| Determinism | Bit-identical state hash across runs and devices | Replay and golden-hash tests |
| Correctness | Ledger always balances; inventory never negative | Invariant checks every tick in debug, sampled in release |
| Performance | Tick duration within the per-tier budget; UI at 60 fps for dashboards | Benchmark suite |
| Responsiveness | Simulation never blocks the main thread | Strict mode and trace checks |
| Battery and thermal | Bounded background work; adaptive simulation speed | Device profiling |
| Testability | Every engine module runs in plain JVM tests | CI |
| Extensibility | New industry added by data without engine code changes | Content-only pull request test |
| Evolvability | Saves migrate across versions | Migration tests |
| Security | Client is never trusted in multiplayer | Server-authoritative design (later volume) |
| Accessibility | TalkBack, scalable text, color-independent encoding | Accessibility audit |

## Numerical requirements

- Money and quantities use fixed-point integer arithmetic (see ADR-0003).
- No floating point in state transitions that affect persisted or replayed state.
- Randomness only through the seeded deterministic provider (see ADR-0004).
