# Android Architecture

Package root: `app.many.economytycoon`.

## Layers in the app

- `ui/`: Compose screens, navigation graph, design system, state holders (ViewModels).
- `session/`: application services; `GameSession` owns the simulation, exposes `StateFlow` read models and accepts commands.
- `simulation/` and domain folders: pure Kotlin, no Android imports.
- `persistence/`: Room database, snapshot and journal stores.
- `infra/`: dispatchers, logging, diagnostics.

## Threading

| Work | Dispatcher |
|---|---|
| Simulation tick loop | Single dedicated thread (single-threaded dispatcher) |
| Snapshot encoding, saving | IO dispatcher |
| AI read-only planning (if parallelized) | Default dispatcher over snapshots |
| UI | Main |

The UI never touches mutable simulation state; it collects read-model flows derived from published snapshots, throttled to the UI frame rate.

## Lifecycle

The simulation runs in a session scope tied to the app process, paused when the app is backgrounded (policy per open question 2). Autosave at configurable intervals and on stop. A foreground service is not used for the simulation; offline progress is handled by an explicit catch-up policy.

## State management

Unidirectional data flow: UI event -> ViewModel -> command to GameSession -> accepted or rejected result -> new snapshot -> read model -> UI state. ViewModels hold only screen state (filters, selections).

## Navigation

Single-activity, Compose Navigation with typed routes per top-level destination: Dashboard, Companies, World, Markets, Operations, Finance, Research, Competitors, Analytics, Profile. Adaptive scaffolds: bottom bar on phones, navigation rail on tablets and foldables.

## Dependency composition

A manual composition root creates the session, stores and dispatchers. A DI framework can be adopted later by ADR.

## Platform integration

Google Play Games (sign-in, leaderboards, cloud save later), Play Billing for entitlements, WorkManager only for non-simulation maintenance (backup, content updates).

## Build

Gradle Kotlin DSL, one `app` module (ADR-0006), static analysis and architecture tests in CI.
