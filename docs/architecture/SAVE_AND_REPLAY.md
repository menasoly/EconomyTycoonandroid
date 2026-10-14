# Save and Replay

## Saves

- Multiple slots; atomic writes (write to temp, fsync, rename).
- Autosave on a timer, on app stop and on major events; rolling backups of the last K autosaves.
- Corruption recovery: verify checksum on load; fall back to the previous snapshot plus journal replay; report to the user if unrecoverable.

## Checkpoints and journal

A snapshot every N ticks plus a command journal in between. Loading = latest valid snapshot + journal replay to the target tick.

## Replay

ReplayEngine starts from a snapshot, feeds the journal in order, and compares state hashes at every checkpoint interval. A mismatch reports the first divergent tick and partition. Replay uses the same code path as live simulation, with no wall-clock or unseeded inputs.

## Versioning

Save format version + content version + engine rules version. If the engine rules version changes behavior, saves record it and replays run under the matching rules or are marked non-replayable across versions. Migrations are pure functions chained version to version and tested with stored fixtures from each released version.

## Cloud backup

Optional. Offline-first: the local save is canonical. Conflict resolution by tick and device lineage; when saves diverge, the user chooses; no silent overwrites.

## State hash

Hash of the canonical snapshot bytes per partition plus an overall hash, used by replay, cross-device parity tests and (later) server verification.
