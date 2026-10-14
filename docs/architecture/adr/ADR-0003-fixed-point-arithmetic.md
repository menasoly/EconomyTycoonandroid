# ADR-0003: Fixed-point integer arithmetic for money and quantities

Status: Accepted

## Context

Floating point can differ across devices and operation orders, which breaks determinism and invites rounding drift in the ledger.

## Decision

Represent money and quantities as scaled 64-bit integers with explicit rounding rules. Floating point is allowed only in presentation and non-persisted analytics.

## Consequences

Exact conservation checks and reproducible hashes. Requires careful scale choices and overflow guards, defined in the economic model volume.
