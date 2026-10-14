# Technology Decisions

## 1. Simulation engine approach

| Criterion | A. Native per platform + shared spec | B. Shared C++ | C. Shared Rust | D. Kotlin Multiplatform | E. Hybrid: shared core + native UI |
|---|---|---|---|---|---|
| Determinism | Medium: needs golden vectors to catch drift | High | High | High on JVM and Native with fixed-point | High |
| Performance | Good | Best | Best | Good | Depends on core language |
| Memory | Good | Best | Best | Good (JVM GC pressure on Android) | Depends |
| Development complexity | Two code bases | JNI and Swift interop plus build toolchain | FFI plus toolchain | Single language for Android; iOS interop needed | Core plus bindings |
| AI-assisted implementation | Strong: Kotlin and Swift are well covered | Weaker: memory safety risks | Medium: borrow checker friction | Strong for Kotlin | Mixed |
| Cross-platform parity | Weakest | Strong | Strong | Strong | Strong |
| Debuggability | Strong with native tools | Weaker | Medium | Strong on Android | Medium |
| Testing | Plain JVM tests | Native tests plus bindings | Native tests plus bindings | Plain JVM tests | Core tests plus binding tests |
| Long-term maintenance | Two engines to keep in sync | One engine, harder talent pool | One engine | One engine | One engine |
| Multiplayer | Server needs a third implementation | Server can reuse core | Server can reuse core | Server on JVM can reuse core | Server reuse possible |
| Battery | Good | Best | Best | Good | Depends |

### Recommendation (Proposed)

Implement the engine **natively in Kotlin inside this app**, structured as a pure-Kotlin domain tree with no Android imports, so it can be extracted to a Kotlin Multiplatform module later without redesign. Keep parity risk controlled by treating the engine behavior as a specification: fixed-point math, a deterministic random algorithm with documented constants, and golden state-hash test vectors that any other implementation must reproduce.

Rationale:

- This repository is Android only; a shared-core toolchain adds build cost before there is a playable slice.
- The JVM gives plain unit tests and strong tooling for AI-assisted work.
- Pure-Kotlin rules keep extraction to KMP (or a server module) cheap.

Validation required before this binds (see closeout): a prototype of the tick loop at target entity counts on a low-end device to confirm the JVM meets the tick budget and GC behavior is acceptable. If it fails, the fallback is moving hot loops to struct-of-arrays primitive storage first, then evaluating Rust.

## 2. Game engine versus native UI

| Concern | Native Compose UI plus custom rendering | Unity or Godot |
|---|---|---|
| Dashboards, forms, tables, charts | Excellent, accessible, platform idioms | Weak; custom UI toolkit work |
| World map and overlays | Canvas or OpenGL/Vulkan surface where needed | Strong |
| Factory animation | 2D Canvas sprites sufficient for the plan | Strong |
| Accessibility | Native TalkBack support | Limited |
| App size and startup | Smaller | Larger runtime |
| Team and AI-agent fit | One language and toolchain | Second toolchain and scripting |

### Recommendation (Accepted)

Native Compose for all management UI. Rendering of maps and factory views uses a dedicated visualization layer (Compose Canvas first; a GPU surface only if profiling demands it). No general-purpose game engine. The visualization layer reads immutable snapshots and never mutates simulation state.

## 3. Recommended stack

| Area | Choice | Status |
|---|---|---|
| Language | Kotlin | Accepted |
| UI | Jetpack Compose, Material 3, adaptive layouts | Accepted |
| Async | Coroutines and Flow | Accepted |
| Persistence | Room over SQLite; compressed snapshot blobs; event journal | Proposed |
| Dependency injection | Manual composition root initially; revisit after the first vertical slice | Proposed |
| Serialization | kotlinx.serialization with explicit schema versions | Proposed |
| Content format | Versioned data files validated at load | Proposed |
| Testing | JUnit, property-based tests, Compose UI tests, macrobenchmarks | Proposed |
| Build | Gradle Kotlin DSL | Accepted (existing) |

New third-party dependencies need an ADR if they touch the simulation or persistence.
