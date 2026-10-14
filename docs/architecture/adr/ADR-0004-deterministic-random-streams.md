# ADR-0004: Seeded, named random streams

Status: Accepted

## Context

A single shared random generator makes results depend on call order, so unrelated changes shift outcomes.

## Decision

Provide a deterministic random provider with an independent stream per subsystem and partition, derived from the world seed. No other source of randomness is allowed in simulation code.

## Consequences

Replay and cross-device parity become testable. The generator algorithm and constants are fixed in the specification.
