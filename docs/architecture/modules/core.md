# Module: core

- Module id: `core`
- Source directory: `app/src/main/java/app/many/economytycoon/core/`
- Responsibility: Shared primitives used by every domain: typed ids, fixed-point money and quantity, ticks, result types, deterministic random streams, canonical hashing.
- Public API: `Money`, `Quantity`, `Rate`, `mulDiv`, `RoundingMode`, `splitLargestRemainder`, typed `*Id`, `Tick`, `Result<T, Rejection>`, `RandomProvider.stream(name, partition)`, `CanonicalHasher`
- Dependencies: none (authoritative: ARCHITECTURE_MANIFEST.yaml)
- Owned entities: none (value types only)
- Commands handled: none
- Events produced: none
- Events consumed: none
- Tick phase(s): all (library)
- Tests required: Property tests for arithmetic and rounding; stream independence; hash stability fixtures
- Estimated complexity: M
- Implementation priority: 1
- Parallel development suitability: High: no dependencies

## Rules

- Only the public API above may be used by other modules; everything else is `internal`.
- Changes to the public API require a change request (AI_AGENT_INSTRUCTIONS.md).
- Entities are referenced from other modules by id only.
- See COMMAND_CATALOG.md and EVENT_CATALOG.md for command and event contracts.
