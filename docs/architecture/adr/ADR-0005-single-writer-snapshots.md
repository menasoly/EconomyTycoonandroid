# ADR-0005: Single-writer simulation with immutable snapshots

Status: Accepted

## Context

UI, AI planning and persistence need to read state while the simulation advances.

## Decision

One simulation thread owns mutable state. Other consumers read immutable snapshots published at tick end. Results flow back as commands at tick boundaries.

## Consequences

No locks on hot paths and consistent reads. Snapshot cost must be bounded using structural sharing or partitioned copy, to be benchmarked.
