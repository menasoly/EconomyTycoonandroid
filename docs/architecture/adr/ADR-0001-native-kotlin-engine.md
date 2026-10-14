# ADR-0001: Native Kotlin simulation engine with extractable structure

Status: Proposed

## Context

The repository is Android only. A shared C++, Rust or KMP core adds toolchain cost before any playable slice exists.

## Decision

Implement the engine in Kotlin inside the app as pure-Kotlin domain folders with no Android imports. Treat behavior as a specification backed by golden state-hash vectors so another implementation or a KMP extraction can match it.

## Consequences

Fast start and plain JVM tests. Requires a prototype to confirm tick budgets and GC behavior on low-end devices. Extraction to KMP stays cheap if the dependency rules hold.
