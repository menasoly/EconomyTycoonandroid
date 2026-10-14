# Coding Standards

## General

- Kotlin official style; static analysis and formatting enforced in CI.
- Simulation code: no `android.*`, no wall-clock, no unseeded randomness, no hash-order iteration, no floating point in state transitions.
- Public API of a module lives in `api/`; everything else is `internal`.
- Immutable data classes for snapshots and commands; mutation only inside the owning engine.
- No `!!`, no swallowed exceptions; typed results for expected failures.

## Naming

Types: `CompanyId`, `ProductionLine`, `SettlementIntent`. Commands: verb + noun (`PlaceOrder`). Events: past tense (`OrderFilled`). Tests mirror the source path and end with `Test`.

## Tests

Every behavior change comes with tests. Invariants asserted in tests after each mutation helper. No test depends on wall-clock or order of other tests.

## Documentation

KDoc on public API with units and invariants. Architecture changes require an ADR.

## Commits

`<type>(<scope>): <description>`. Small, coherent, buildable. No attribution trailers.
