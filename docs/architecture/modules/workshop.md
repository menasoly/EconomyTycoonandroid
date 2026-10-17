# Module: workshop

Scenario authoring and balance testing. See ADR-0019 for the reasoning.

## Responsibility

Holding, editing, validating, converting, sharing, filing and testing scenario drafts. It owns no simulation state and
registers nothing with the scenario catalog. Test runs happen in private `Simulation` instances that are discarded.
A converted draft is played through `NewGame.startCustom`; the definition (`ScenarioDef` with its events, failure
conditions and starting sites) is carried by the run and saved with the game, see ADR-0019.

Depends on: `core` (money, binary, hashing, results), `content` (read), `scenarios` (goal kinds, `ScenarioDef`, medals),
`simulation` (`Simulation`, `SimConfig`, commands), `session` (`NewGameOptions`), `persistence` (`SaveStore`). Nothing
depends on it. Presenters are in `ui/present`, screens in `ui/compose/screens`.

## Owned entities

| Type | File | Notes |
|---|---|---|
| `ScenarioDraft`, `WorldSettings`, `CompanySetup`, `StarterSite`, `RuleKey`, `RuleOverrides`, `ScriptedEvent`, `DraftGoal`, `FailCondition`, `Timing`, `DifficultyTag` | `DraftModel.kt` | the value; canonical order and hash |
| `DraftIssue`, `ValidationReport`, `DraftValidator` | `DraftValidation.kt` | findings with severity and a field path |
| `DraftEdit`, `DraftEditor` | `DraftEdit.kt`, `DraftEditor.kt` | pure edits; undo, redo, dirty mark, cached validation |
| `DraftTemplates`, `DraftDefaults` | `DraftTemplates.kt` | blank, family farm, sprint, shoestring, sandbox, land rush; remix of a built-in |
| `Conversion`, `NewGameParams`, `DraftConverter` | `DraftConversion.kt` | draft to `ScenarioDef` and parameters, with notes |
| `DraftCodec` | `DraftCodec.kt` | binary form |
| `ShareCodec`, `ShareBundle` | `ShareCodec.kt`, `ShareBundle.kt` | text codes; several in one text |
| `DraftPrinter`, `DraftParser`, `ParseResult`, `SourceMap` | `DraftText.kt` | the written form and its source positions |
| `DraftDiff` | `DraftDiff.kt` | readable difference between two drafts |
| `HarnessSetup`, `SiteOperator` | `HarnessSetup.kt` | founding the player's company and placing starters |
| `PlayerPolicy` and four policies | `Policies.kt` | idle, greedy, conservative, scripted |
| `RunMetrics`, `RunSample`, `SoftlockProbe` | `HarnessMetrics.kt` | what a run produced |
| `WorkshopHarness`, `HarnessSettings` | `WorkshopHarness.kt` | the run loop |
| `BalanceAnalyst`, `BalanceReport`, `DifficultyRating` | `BalanceAnalyst.kt`, `BalanceReport.kt` | findings from runs |
| `WorkshopStats`, `Summary`, `Trend` | `WorkshopStats.kt` | integer statistics |
| `BalanceStudy`, `SweepRunner`, `AutoTuner` | `BalanceStudy.kt`, `Sweep.kt`, `AutoTuner.kt` | many runs, one varying number, a search |
| `Baseline`, `BaselineCodec`, `Regression` | `Regression.kt` | record and compare |
| `PackCompat` | `PackCompat.kt` | content compatibility |
| `ScoreFormula`, `ScoreSubmission`, `Leaderboard` | `ScoreFormula.kt` | leaderboard-ready scoring |
| `WorkshopLibrary`, `LibraryCodec`, `LibraryStore` | `WorkshopLibrary.kt`, `LibraryStore.kt` | the shelf and its storage |
| `WorkshopSession` | `WorkshopSession.kt` | the screens' single entry point |
| `WorkshopRunner` | `WorkshopRunner.kt` | studies and sweeps on an injected executor, one at a time |

## Rules (pure)

* A draft is normalised after every edit: events sorted by day (stable), tags and packs sorted and distinct. Equal
  drafts are equal values and have equal `stateHash()`.
* An edit never makes a draft "invalid"; only the validator does. A draft with errors cannot be converted.
* Findings are ordered errors first, then by path and code, so the same draft always lists the same way.
* Share codes: `ETW1-`, 5-symbol groups, 8 groups a line, `:` and a check symbol closing each line, a flag byte (raw or
  deflated), CRC32 over the payload. Reading ignores case, spaces, dashes and look-alike letters, and a blank line ends a
  code. An unreadable code gives a line and column; it never throws.
* Scores are integers. The same run of the same draft always scores the same, and `Leaderboard.rank` is a total order
  (score, then fewer days, then seed, then name), with ties sharing a position.
* A run is deterministic in (draft, policy, seed, settings). The idle policy never issues a command.

## Persistence

`LibraryCodec` (magic `ETWL`, version 1, payload, CRC32) under key `workshop.library`; baselines (magic `ETWB`) under
`workshop.baseline.<entry id>`. Both refuse truncated or edited input with `ByteReader.CorruptDataException`, which the
store turns into "empty" and "damaged". The package itself adds nothing to a game save; a game started from a draft saves
its definition in the existing scenario section (version 2, `ScenarioDefCodec`).

## Tests

Unit tests per class; codec round trips with truncation and bit flips; a seeded fuzz suite that pushes 60 random drafts
through every codec and the written form; analysis tests on hand-made `RunMetrics` (fast); a short harness suite on a
tiny world for determinism and policy behaviour (about 20 s in total); presenter tests with no Android. Screens are not
compiled in the author build.
