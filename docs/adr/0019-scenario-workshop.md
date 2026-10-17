# ADR-0019: A scenario workshop of drafts, values and headless test runs

Status: accepted

## Context

Scenarios are Kotlin objects in `ScenarioCatalog`. Writing one means editing source, and there is no way to find out
whether it is winnable, trivially easy or a trap except to play it. The community asked for three things: to write their
own scenarios, to trade them as text, and to know before sharing one whether it is any good. The game already has the
pieces for the last part (a deterministic `Simulation`, a planner that plays a company, goal meters) but nothing that
uses them to judge a scenario, and nothing that holds an unfinished scenario at all: `ScenarioDef` rejects anything not
fully sound in its constructor.

Constraints. Everything stays deterministic and testable without Android. Nothing may register a scenario or change a
running game: a half-written draft must be safe to hold, edit, save and throw away. A shared scenario comes from a
stranger, so reading one must never throw, must say where it went wrong, and must never produce a draft the game would
choke on. Test runs must be cheap enough to put in a unit test and honest enough to trust.

## Decision

**A draft is a value, separate from a definition.** `ScenarioDraft` holds everything an author can set (identity, seed,
world, starting company, rule overrides, scripted events, weighted goals with bonus goals, extra ways to lose, timing,
difficulty tags, hints, the content packs it was written for) with no invariants beyond the type system. Any draft can
be held, edited and saved. `DraftValidator` is the only judge: it reports findings with a severity and a path such as
`goals[1].target`, the same path the editor uses for its fields and the text parser uses for source positions.
`DraftConverter` is the only way out: a sound draft becomes a `ScenarioDef` and `NewGameParams` as plain values, with a
note for each part the definition cannot hold. It registers nothing.

**Edits are pure functions, history lives in an editor.** A `DraftEdit` maps a draft to a draft or refuses with a
reason; edits refuse only what a draft cannot hold (a missing index, a full list). `DraftEditor` owns the current
draft, an undo and a redo stack with a limit, merges consecutive edits with the same key into one step (typing), and
caches the validation by draft hash. Every accepted edit leaves the draft in canonical order, so equal drafts are equal
as values and as hashes.

**Three forms of text, one model.** A binary `DraftCodec` (version byte, length-prefixed body) is what is stored. The
share code (`ETW1-`, Crockford base32, groups of five, a check symbol per line, deflate when it helps, a CRC32 trailer)
is what is traded: it survives being retyped, quoted in chat or wrapped by a mail client, and a damaged line is named.
A line-oriented DSL is what is written by hand and diffed: its parser is tolerant (comments, curly quotes, `:` or `=`),
collects every error with line and column, and suggests the nearest known word. Print then parse returns the same draft,
and print is a fixed point; tests assert both on random drafts.

**The harness plays the real game.** `WorkshopHarness` builds a `Simulation` from the converted configuration, founds the
player's company, places the starting sites, applies scripted events on their days, and checks goals, failure
conditions and a stall probe every few days. A custom draft cannot use `ScenarioRunner` (it is tied to the catalog), so
goal and failure evaluation is the harness's own, defined once in `HarnessMetrics`. Four policies play: idle, a greedy
expander and a conservative player (both the existing computer planner, registered for the player's company with
different personalities), and a scripted build order. A run is plain numbers (`RunMetrics`) and two runs of one draft
and seed are equal.

**Analysis is separate from running.** `BalanceAnalyst` takes drafts and run metrics and nothing else, so its rules are
tested with hand-made metrics in milliseconds. It rates difficulty from how far the best policy got and how fast
(extrapolated linearly), detects a dominant strategy, a scenario an idle player wins, softlocks, goals that can regress
or never hold together, high variance across seeds, a tag that disagrees with the rating, and a horizon too short to say.
Sweeps vary one number and report medians and trends; `AutoTuner` searches one number for a target difficulty band by
bisection, assuming the number moves difficulty one way and saying so when it does not.

**Regression is a recorded set of numbers.** A `Baseline` keeps one entry per policy and seed (outcome, key numbers,
hash), has its own versioned and checksummed format, and compares to a new set with an optional tolerance in ppm. A
difference is reported as a metric with before, after and size in percent, never as a bare hash mismatch.

**Content compatibility is checked, not assumed.** A draft records the packs it was written for as `id:major.minor`. A
pack is compatible with the same major and an equal or newer minor. `PackCompat` also checks that starting recipes exist
and can be afforded, and that goals can be met with the content that is loaded.

**One scoring formula.** `ScoreFormula` turns a run into integer points (progress, a win with medal and speed, bonus
goals, a logarithmic net worth term, penalties, a difficulty multiplier from the rating). A `ScoreSubmission` carries
the draft hash, seed, days and score with a checksum, and `Leaderboard.rank` is a total order, so ranking is the same on
every device.

**Library.** `WorkshopLibrary` is a shelf with folders, tags, notes, stars and search; `LibraryCodec` stores it in one
checksummed file under one `SaveStore` key, and an unreadable file loads as an empty shelf and is reported, not deleted.
`WorkshopSession` ties the editor, the shelf and the latest results together for the screens.

## Consequences

Good: authors can write, test, share and compare scenarios with no code and no release; a bad shared code is a message,
not a crash; balance claims are reproducible; the analysis is cheap to test. The package adds no state to the
simulation, no commands and no snapshot sections, so it cannot change an existing save or golden value.

Costs: the harness duplicates a few dozen lines of setup that live privately in `Bootstrap`. The linear projection of
time-to-goal is crude for scenarios with an S-shaped curve; the analysis says "not on track" rather than guess when
progress is flat. A draft can describe things (events, bonus goals, extra failure conditions) that the shipped
`ScenarioDef` cannot play yet; the converter lists them.

Rejected: reusing `ScenarioDef` as the draft type (its constructor throws on anything unsound, which makes editing
impossible); a JSON share format (no checksum story, easy to mangle when retyped); running balance tests on the
player's saved game (not reproducible); judging by a single policy (the dominant-strategy check needs several).
