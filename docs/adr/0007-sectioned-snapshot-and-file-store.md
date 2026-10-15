# ADR-0007: Sectioned binary snapshot in plain files, not SQLite

Status: accepted

## Context

A save has to capture the whole economy exactly: every ledger entry, order, loan, contract, grant, tender and the
state of the random generators. Loading a save must give a simulation that behaves, tick for tick, as the original
would have. The game also has to keep several saves, autosave without stalling the screen, survive being killed
half-way through a write, and open saves written by earlier versions.

The first idea was a SQLite database through Room. It gives queries, migrations and a familiar API. It also pulls
the engine towards row-shaped state, hides the order in which things are written, and ties the save format to a
library that the pure Kotlin tests cannot run without a device or an emulator.

## Decision

A save is one byte stream made of named sections, one per module that owns state. Each module has its own codec that
reads and writes its section with the shared varint and fixed-point primitives, and the stream records the version of
each section. A header carries the format version, the seed, the tick and the hash of the economy taken at save time.
Older streams are brought forward by a chain of migrations before they are read, and a stream with a version this
build does not know is refused.

Slots are plain files in the app's private folder, one file per key. A write goes to a temporary file, is flushed,
and is then renamed over the slot, so a crash leaves either the old save or the new one. The load screen reads the
header of each slot to show its tick and label.

Loading rebuilds the economy, recomputes the hash and compares it with the header. A mismatch is reported as a
damaged save and nothing is half-loaded.

## Consequences

- Saves round-trip in ordinary JVM tests with no Android dependency, and the same code produces the hash used by
  the replay checks.
- Every module that adds state must add a codec, a section and a round-trip test. The snapshot test fails when a
  module is missing from the hash or the stream.
- Changing a module's layout means bumping the format version and adding a migration for the old layout.
- There is no ad-hoc querying of a save from outside the game. Exports are produced by the game itself as files
  (CSV) instead.
- Saves are compact: counts and ids are varints and money is stored as raw integers.
