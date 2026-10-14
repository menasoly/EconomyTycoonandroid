# Data Architecture

Module: `persistence`, `content`.

## Stores

| Store | Technology | Contents |
|---|---|---|
| Save slot metadata | Room | Slot id, name, tick, version, timestamps, thumbnail |
| Snapshots | Compressed binary blobs in files, indexed in Room | Full authoritative state at checkpoints |
| Command journal | Room table or append-only file | Commands per tick since last snapshot |
| Historical statistics | Room (time-series tables) | Downsampled company and market series for analytics |
| Content | Bundled versioned files, validated at load | Industries, goods, recipes, parameters |
| Settings | DataStore | User preferences |

## Authoritative versus derived

Authoritative: entity state, RNG state, ledger, event queue. Derived: statements, rankings, charts, caches. Derived data is never loaded as truth; it is rebuilt or validated against authoritative data.

## Room schema principles

- Normalized tables for save metadata and history; snapshots stored as blobs to avoid per-entity row costs.
- Indexes: (slot, tick) for history and journal; (company, series, tick) for statistics.
- Schema migrations use Room migrations with tests per version step.

## Snapshot format

Canonical binary encoding with a header: format version, content version, tick, partition table, checksum. Canonical order and no padding bytes so the state hash is stable across devices.

## Content compatibility

Content packs have a version and a stable id for each definition. Saves record the content versions used. When new industries are added, existing saves continue to run; new definitions are available only when a migration adds them to the world explicitly. Removed definitions are kept as deprecated aliases.

## Throughput targets

Snapshot write in the background within the budget defined in PERFORMANCE_BUDGETS.md; statistics writes batched per N ticks in one transaction.
