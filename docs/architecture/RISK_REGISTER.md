# Architectural Risk Register

S = severity, P = probability (L/M/H).

| Risk | S | P | Mitigation | Detection | Fallback |
|---|---|---|---|---|---|
| Economic instability | H | H | Damping, caps, lagged demand, golden scenarios | Long-run tests, invariant monitors | Tighten caps, add stabilizers |
| Unbounded simulation complexity | H | M | Cadence-based detail, aggregation, budgets | Benchmarks per phase | Reduce scale presets |
| Mobile performance degradation | H | M | Budgets, primitive arrays, adaptive speed | Macrobenchmarks, device farm | Lower scale on low-end |
| Cross-platform divergence | M | M | Golden vectors, fixed-point, spec | Parity tests | Share core (KMP/Rust) |
| Save incompatibility | H | M | Versioned formats, migration fixtures | Migration tests | Recovery from older snapshot + journal |
| Non-deterministic AI | H | M | Seeded streams, stable ordering | Determinism tests | Disable adaptive features |
| Inconsistent financial settlement | H | L | Single posting path, idempotency keys | Ledger property tests | Quarantine and repair tool |
| Coupled industry modules | M | M | Data-driven content, architecture tests | Dependency checks | Refactor via interfaces |
| Inconsistent agent-written code | M | H | Manifest, templates, reviews | Architecture checks, PR review | Owner rewrites |
| Integration failures | M | M | Phase gates, golden scenarios | CI | Integration branches |
| Multiplayer cheating | H | M | Server authority | Anomaly detection | Reset or ban |
| Excessive backend cost | M | M | Tick caps, hibernation | Cost dashboards | Restrict world sizes |
| Poor accessibility | M | M | Accessibility rules, audits | Audits, tests | Remediation sprint |
| Feature creep | M | H | MVP scope, phase gates | Roadmap review | Defer to expansion |
| JVM GC pauses in tick loop (ADR-0001) | H | M | Primitive arrays, allocation budgets | Profiling | Move hot loops native |
