# Module: banking

Commercial banks as game actors, player or computer controlled. See ADR-0013 for the reasoning.

## Responsibility

Balance sheets, deposit competition, loan pricing and underwriting, expected-loss provisioning, capital and the
supervisory ladder, liquidity and reserves, the interbank market and central bank facilities, bank runs, resolution
and the guarantee fund, stress tests, margin analytics, bank ratings, dividends, computer-controlled banks and the
advisor for the player's bank. It does not post to the ledger, own companies or loans of companies, or decide the policy
rate; it reads monetary, sovereign, credit demand and savings conditions through narrow interfaces.

Depends on: `core` (money, rate, ids of companies and countries, binary, hashing, random, results), `finance`
(settlement intents, ledger owners, chart, credit rating). Used by `simulation` (`BankingRunner`, `MacroBankingInputs`), `persistence`, `session` and `ui`.

## Owned entities

| Type | File | Notes |
|---|---|---|
| `BankId`, `LoanSector`, `LoanStage`, `Depositor`, `DepositProduct`, `SecurityKind`, `SupervisoryRung`, `RunStage`, `PoolKey`, `LoanPool`, `DepositKey`, `DepositPool`, `SecurityHolding` | `BankingTypes.kt` | identifiers and the keys of the pools |
| `Bank`, `BankSnapshot`, `BankOperator`, `BankStatus` | `Bank.kt`, `BankSnapshot.kt` | the mutable bank (changed only by the service) and its immutable view |
| `BankPolicy`, `BankStrategy`, `BankArchetype` | `BankPolicy.kt`, `BankStrategy.kt` | the levers and the personality of a computer bank |
| `MonthlyResult` | `BankResult.kt` | one month's income statement, kept in the history |
| `BankingParams` | `BankingParams.kt` | every constant, grouped by area |
| `BankingBook` | `BankingBook.kt` | all state: banks, funds, markets, systemic stress, stress records, hidden advice |
| `BankAction`, `BankEvent` | `BankAction.kt`, `BankEvent.kt` | decisions and happenings |

## Rules (pure)

* `BankMath`, `RiskTables` - ratios, risk weights, expected-loss and migration tables.
* `CapitalEngine` - risk-weighted assets, CET1, tier 1, total and leverage ratios, buffers, rung and distribution limit.
* `LiquidityEngine` - liquid assets, LCR, NSFR, required and desired reserves.
* `LoanPricing`, `Underwriter` - the quote for a loan and the decision to make it.
* `Provisioning` - stage migration, allowance, write-off.
* `DepositMarket` - quotes, beta, share of new savings and churn.
* `CentralBankFacilities` - corridor, collateral haircuts, marginal and emergency lending.
* `InterbankMarket` - overnight market clearing with counterparty limits.
* `BankRuns` - confidence, stage with hysteresis, withdrawals.
* `LiquidityManager` - the order in which a shortfall is covered.
* `GuaranteeScheme` - insured amounts, levies, payout, state loan.
* `ResolutionPlanner`, `ResolutionEngine` - choose and carry out recapitalisation, merger or liquidation.
* `StressTester` - scenarios applied month by month to a copy of the books.
* `MarginAnalytics`, `BankRatingModel`, `Dividends`.

## Services

* `BankingService` - the only writer. `found`, `seedRivals`, `apply(action)`, `rumour`, `stressReport`, `step`.
* `MonthlyClose`, `DailyRound`, `ResolutionRunner`, `LoanMarket` (internal) - the pipeline behind `step`.
* `BankingActions` - checks who may do what (the player's own banks only; planners on computer banks only; supervised banks
  are restricted) and applies one action.
* `BankAiPlanner` - the strategy of each computer bank.
* `BankingIntents` - every ledger movement the package asks for.

## Read models and analysis

`BankView` (capital, liquidity, margin, dividend capacity for one bank), `BankAdvisor` and `BankAdvisorRules` (rules that give `BankInsight`s),
`StressReport`, `BankingService.market`, `fund`, `fundCoverage`, `systemicStress`, `recentEvents`.

## Persistence

`BankingCodec` writes one section (version 1) and verifies it on read. `BankActionCodec` encodes a `BankAction` for the
command log. `BankingService.stateHash()` and `hashInto` feed the economy hash.

## UI

Presenters in `ui/present`: `BankingPresenter` (sector overview and bank rows), `BankDetailPresenter` (statements, ratios,
policy, history), `BankStressPresenter`, `BankAdvicePresenter`, `BankEventText` (news and alerts text), `BankActionText`
(button and log text), `BankingLabels` (tones and plain words). Screens in `ui/compose/screens`: `BankingScreen`,
`BankDetailScreen`, `BankStressScreen`. They take models and callbacks, not the session, so the shell decides how a
callback becomes a command.

## Invariants

* After every operation `BankSnapshot.imbalance` is zero for every bank.
* Interbank lent equals interbank borrowed for each country.
* Money that leaves a failing bank is re-deposited at other banks of the country.
* The same seed and the same inputs give the same hash.

## Tests

Mirror the sources under `app/src/test/java/app/many/economytycoon/banking` and `ui/present`: rule tests, service and
action tests, codec round trips and hash stability, daily/monthly rounds, and scenario tests (a run, a failure and
resolution, a merger, a recession, supervision, save and restore in the middle of a crisis).

## Integration

* `simulation/BankingRunner` - `establish` (the system-only `Command.EstablishBanks`), `found` (`Command.FoundBank`, tag 118),
  `decide` (`Command.BankDecision`, tag 119, carries any action except founding), the daily `step` in the financial
  settlement phase after every other group of intents, and `normalise`, which maps unmodelled customers to the
  households account and keeps keys unique. `EstablishBanks` has tag 117.
* `simulation/MacroBankingInputs` - the adapter over the macro service, bonds, world and national accounts. Collateral
  prices have no index of their own in the game, so the output gap stands in for them.
* `Economy.banking` is hashed and saved (section `banking`) only once it is no longer pristine.
* Session: `hasBanking`, `bankingOverview`, `bankDetail`, `bankAdvice`, `bankStress`, `hideBankAdvice`; news for important
  bank events, alerts for the banks a company runs. Routes `banking`, `bank/<id>`, `bank-stress`, `found-bank`.
* Not done: the computer-run companies do not found banks, and the agenda has no bank items.
