# Performance Budgets

All numbers are **targets to validate**, not measurements. Device tiers are provisional (open question 5).

| Metric | Low-end | Mid-range | High-end |
|---|---|---|---|
| Simulation tick (typical load) | <= 100 ms | <= 40 ms | <= 20 ms |
| Tick worst case (large world) | <= 300 ms | <= 120 ms | <= 60 ms |
| UI frame rate | 30+ fps on heavy screens | 60 fps | 60-120 fps |
| Memory (heap, in game) | <= 350 MB | <= 600 MB | <= 1 GB |
| Snapshot save (background) | <= 2 s | <= 1 s | <= 500 ms |
| Load to playable | <= 6 s | <= 3 s | <= 2 s |
| Battery | Adaptive speed, no wakeups in background | same | same |

## Scale targets (design, not benchmarks)

Thousands of companies, tens of thousands of facilities, aggregated consumers in the millions, workforce cohorts representing hundreds of thousands of employees.

## Techniques

Primitive arrays and struct-of-arrays for hot data, incremental recomputation, sparse updates, cadence-based level of detail, bounded AI work per tick, snapshot structural sharing.

## Parallel determinism

Parallelism only over disjoint partitions, per-partition buffers, merge in partition id order, per-partition random streams. A determinism test runs serial and parallel and compares hashes.

## Benchmark plan

Benchmark worlds at small, medium and large scale. Measure p50 and p95 tick time, allocation rate, GC pauses, snapshot time, frame time. CI runs a reduced benchmark and flags regressions beyond a threshold.

## Thermal

Adaptive: reduce simulation speed and visual effects when thermal status rises.
