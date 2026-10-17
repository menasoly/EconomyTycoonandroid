# Module: blueprint

Saved facility set-ups that can be validated, priced, applied, varied, shared and kept in a library.

## Responsibilities

- Capture a facility from a read view into an immutable `Blueprint` (`BlueprintCapture`, `FacilityView`).
- Validate a blueprint against content, research and a site (`BlueprintValidator`) and estimate its build cost, running
  cost and payback (`CostEstimator`, `BlueprintAnalyzer`).
- Plan how to apply it to an existing or new facility (`ApplyPlanner`, `ApplyPlan`, `ApplyReport`).
- Produce variants: mirror, upgrade tier, recipe-family swap, scaling (`BlueprintVariants`).
- Share as versioned text with a checksum and parse it tolerantly (`BlueprintText`).
- Keep many blueprints (`BlueprintLibrary`, `LibraryCodec`, `BlueprintStore`) and pending builds (`PendingApplyBook`).
- Check compatibility with the current content and describe migrations (`BlueprintCompat`).
- Diff two blueprints (`BlueprintDiff`).

## Public surface

| type | use |
| --- | --- |
| `Blueprint`, `BlueprintSpec`, `LineSpec` | the value; `Blueprint.of(...)`, `spec.canonicalHash()` |
| `BlueprintHost` / `EconomyBlueprintHost` | the only door to the running game |
| `BlueprintController` | library, analysis, planning, pending builds in one place for the session |
| `ApplyPlanner.plan(blueprint, target, research, options)` | returns an `ApplyPlan` of `StepAction`s |
| `StepAction.toCommand(company, facility, lines)` | the existing `Command` for a step, or null if its line is not known yet |
| `BlueprintText.encode / parse / parseAll` | the share format |
| `BlueprintCodec`, `LibraryCodec`, `PendingApplyBook` store | binary layouts with a version byte |

## Dependencies

Uses core, content, world, workforce, production, companies, simulation (read) and session (`Formatting`). Nothing
outside session, app and ui depends on it. It does not import `android.*`.

## Invariants

- A blueprint holds no per-game id; equal set-ups have equal canonical hashes, whatever their name, note or folder.
- Planning is pure: the same blueprint, target, research and options always give the same plan.
- A plan is applied only by submitting its commands; a step never runs before the steps it depends on succeeded.
- Re-applying a blueprint to a facility that already matches it plans nothing.
- Library operations keep folder paths normalised, tags lowercase, names within limits, and never exceed the limits.
- Decoding unknown or damaged bytes drops the damaged entries and reports them; it never throws.

## Presenters

`ui/present/Blueprint*Presenter.kt` turn the above into rows, tiles and facts (library list, detail, import dialog with
error rows, apply preview and report, comparison). `BlueprintApplyRunner` walks a plan and submits each command.
`BlueprintsScreen` (Compose) is a thin view over them.

## Tests

Unit tests per class, codec round trips and hash stability, tolerant parser cases with exact error positions, library
invariants after each mutation, determinism, and integration tests that apply plans to the real executor.

## Not done


