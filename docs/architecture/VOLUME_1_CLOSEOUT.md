# Volume 1 Closeout

## Completed decisions

- Native Compose UI with a separate visualization layer (ADR-0002).
- Fixed-point arithmetic and seeded named random streams (ADR-0003, ADR-0004).
- Single-writer simulation with immutable snapshots (ADR-0005).
- Domain folders inside the app, no separate packages (ADR-0006).
- Tick lifecycle and its ordering rationale (SIMULATION_ENGINE.md).

## Proposed, needs validation

- Native Kotlin engine (ADR-0001): prototype the tick loop at target scale on a low-end device, measure tick time and GC.
- Room plus compressed snapshots and a command journal for persistence.
- Manual dependency composition versus a DI framework.

## Open questions

1. Tick granularity: one tick equals one in-game day, or finer?
2. Behavior after the app was in the background: pause, bounded catch-up, or none?
3. Scale of money and quantity fixed-point units and overflow policy.
4. Snapshot strategy: structural sharing or partitioned copy.
5. Minimum supported Android version and device tiers.
6. Whether the iOS version shares golden test vectors with this repository.

## Dependencies on later volumes

- Economic model and invariants: fixed-point scales, ledger rules.
- Domain entities: ids, versions, relationships.
- Persistence and replay: canonical serialization for state hashes.
- Testing and performance: architecture tests, benchmark acceptance criteria.

## Consistency checks

- Layer table, module catalog and repository layout use the same domain names.
- No domain depends upward; core has no dependencies.
- Every ADR referenced in other documents exists.
- Tick phases write only to the state their domain owns.

## Documents to keep in the repository

All files under `docs/architecture/` in this volume.
