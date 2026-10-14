# Test Strategy

## Layers

| Type | Scope | Tooling |
|---|---|---|
| Unit | Pure functions and engines | JUnit on JVM |
| Invariant | Domain rules INV-01..INV-10 | Checked after every mutation in tests |
| Property-based | Ledger, order book, rounding, allocation | Property-testing library |
| Determinism | Same seed gives same hash across runs | JVM tests, repeated runs, parallel and serial |
| Replay | Journal replay reproduces hashes | ReplayEngine tests |
| Persistence and migration | Round trip, each version step | Room tests with fixtures |
| AI decisions | Fixed observations, constraint fuzzing | JVM |
| Integration | Phase pipeline end-to-end | JVM |
| UI | Compose tests, screenshot tests | Compose test APIs |
| Performance | Tick and render benchmarks | Macrobenchmark and JVM benchmarks |
| Long-running | Years of simulated time | Scheduled CI |
| Cross-platform parity | Shared golden hashes with iOS | Fixture files |
| Multiplayer | Concurrency and idempotency | Backend test suite |

## Golden scenarios

Small scripted worlds with known seeds and expected outcomes (state hashes plus key metrics): single farm to market; two competing retailers; supply chain with a shortage; bank run; mass bankruptcy; recession. Golden files change only through reviewed pull requests.

## Required adversarial tests

Negative inventory, duplicate settlement, infinite money loops, deadlocked supply chains, circular recipes (content validation), unbounded inflation, insolvent companies, zero-demand markets, extreme shortages, mass bankruptcies, large recessions.

## Gates

A pull request needs: unit and invariant tests for changed behavior, architecture tests green, determinism test green, no golden change without explanation.
