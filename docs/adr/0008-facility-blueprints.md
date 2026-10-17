# ADR-0008: Facility blueprints are saved plans made of ordinary commands

Status: accepted

## Context

A player who has tuned a facility (lines, machines, recipes, staffing, trading rules) has to repeat every one of those
choices by hand for the next one. Players also want to share set-ups, and a set-up copied from someone else may name
recipes the player has not researched or content that has since been renamed.

The obvious shortcut is a new "paste configuration" command that sets everything at once. That adds a command, a codec
entry, an executor path and a replay format change for something that is only a convenience, and it would bypass the
rules each existing command already enforces (cost, research, limits).

## Decision

A blueprint is a plain value: the facility type plus a description of its lines, machines, storage, schedule, quality,
staffing, training and buy and sell rules. It never mentions ids that belong to one game (no facility, line or deposit
ids). Applying it is planning, not executing:

- `ApplyPlanner` compares the blueprint with the target (an existing facility or a new one) and produces an
  `ApplyPlan`: ordered steps, each with the steps it depends on, an estimated cost, and a reason when it was left out.
- A step turns into an existing `Command` with `toCommand`. The package never submits it; the session does, through
  the normal command path, so every rule still applies and the replay log needs no new entry.
- Steps are slot-based (line 1, line 2) because a line does not exist until `AddLine` has run; the runner reads the
  new line ids back from the facility between steps.
- A refused step marks its dependents as blocked. Unrelated steps still run, and the report says what happened to each.
- A building takes days to finish, so for a new facility the first step is the build and the rest waits in a small
  pending book that the session checks every tick.

Sharing uses a text form: a prefix and version, an encoding tag (base32 with Crockford letters, or base64url), the
payload and a CRC32 checksum. The parser is tolerant of whitespace, case, look-alike letters and surrounding prose, and
reports the exact line and column of what is wrong. Older versions are migrated with notes and unknown recipes and
machines are renamed through a table or reported, never silently dropped.

The library, its folders and tags, the pending book and the text share one binary layout rule: a version byte, then
additive fields. They are stored under their own keys in the app save store, like the watchlist, not in the economy
snapshot, because they are player conveniences and must not affect `stateHash`.

## Consequences

- No new command, no snapshot section, no golden value changes. Replays recorded before this feature stay valid.
- Applying cannot do anything a player could not do by hand; a blueprint cannot cheat cost, research or limits.
- A plan can be previewed as a dry run (what would change, what is left out, what it costs) because it is only data.
- Matching a finished building to its pending request is by region, type, name and newness, since the build result
  does not return the created id. This is documented as a gap and covered by tests.
- Presenters are pure and tested; the Compose screen stays thin.
