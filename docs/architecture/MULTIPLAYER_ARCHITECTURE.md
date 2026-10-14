# Multiplayer and Backend Architecture

Optional and separately deployable. Single-player has no dependency on it.

## Model

Server-authoritative. Clients send commands; the server validates and applies them in its simulation and returns authoritative results. Clients render and may predict locally, but never settle finances.

## Recommended stack (Proposed)

A JVM backend (Kotlin) so the same pure-Kotlin engine code runs on the server, with PostgreSQL for durable state, a message queue for command ingestion, and object storage for snapshots. Reason: engine reuse removes a third implementation. Decision recorded by ADR when Phase 8 begins.

## World ownership

Each multiplayer world has one authoritative simulation shard. Commands carry (player, world, expected tick, idempotency key). The shard orders commands; concurrency is single-writer per world.

## Transactions

Player-to-player trades are executed as ledger transactions inside the server simulation: reserve, match, settle atomically. The client never sends computed balances.

## API contracts (outline)

| API | Purpose | Auth |
|---|---|---|
| POST /worlds/{id}/commands | Submit a command | Player token, world membership |
| GET /worlds/{id}/state?since= | Delta state for a player | Player token |
| GET /worlds/{id}/events | Event stream | Player token |
| POST /saves | Cloud backup upload | Player token |
| GET /leaderboards | Rankings | Public or token |
| POST /entitlements/verify | Validate purchase | Player token |

## Migration path from offline

1. Keep the command-and-snapshot interface identical online and offline.
2. Introduce accounts and cloud backup.
3. Add asynchronous multiplayer worlds with long ticks.
4. Add real-time shared worlds if economics of hosting permit.

## Cost control

Tick rates and world sizes are capped; idle worlds hibernate; snapshots are compressed and tiered.
