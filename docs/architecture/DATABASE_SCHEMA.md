# Database Schema (Room)

Authoritative simulation state is stored as snapshot blobs; Room holds metadata, journal and history. Types: INTEGER for ids/ticks/amounts, TEXT, BLOB.

## save_slot

| Column | Type | Notes |
|---|---|---|
| slot_id | INTEGER PK | |
| name | TEXT | |
| created_at, updated_at | INTEGER | wall-clock epoch ms, metadata only |
| world_seed | INTEGER | |
| current_tick | INTEGER | |
| format_version | INTEGER | |
| content_version | TEXT | |
| rules_version | INTEGER | engine rules version |
| thumbnail | BLOB NULL | |

## snapshot

| Column | Type | Notes |
|---|---|---|
| snapshot_id | INTEGER PK | |
| slot_id | INTEGER FK | |
| tick | INTEGER | |
| file_path | TEXT | compressed blob on disk |
| size_bytes | INTEGER | |
| state_hash | BLOB | canonical hash |
| is_autosave | INTEGER | |

Index: (slot_id, tick DESC).

## journal_command

| Column | Type | Notes |
|---|---|---|
| slot_id | INTEGER FK | |
| tick | INTEGER | |
| sequence | INTEGER | total order within tick |
| issuer | INTEGER | |
| payload | BLOB | versioned encoding |

PK (slot_id, tick, sequence). Pruned after the next verified snapshot.

## history_series

| Column | Type | Notes |
|---|---|---|
| slot_id | INTEGER | |
| series_key | TEXT | e.g. company:42:cash, market:grain:price |
| tick | INTEGER | |
| value | INTEGER | fixed-point |

PK (slot_id, series_key, tick). Downsampled by tier (full recent, coarser older).

## news_event

| Column | Type | Notes |
|---|---|---|
| slot_id, tick, sequence | INTEGER | PK |
| kind | TEXT | |
| payload | BLOB | |

## Migrations

Each schema version has an exported schema file and a migration test that opens a stored database of the previous version, migrates and validates. Snapshot format migrations are separate pure functions (SAVE_AND_REPLAY.md).

## Wall-clock note

Timestamps appear only in metadata; simulation logic never reads them.
