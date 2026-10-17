# Module: carbon

Package `app.many.economytycoon.carbon`. Decision record: [ADR-0012](../../adr/0012-carbon-market.md).
Depends on: core, content, finance, production, advisor.

## Responsibility

Counts the carbon dioxide that facilities release, lets governments put a price on it, and gives companies the means
to deal with that price: pay, buy permits, buy credits or emit less. It owns the inventories, the permit markets, the
credit projects, the abatement projects, the compliance records, the statements and the ratings. It does not own cash,
production or companies; it reads them and returns the money it wants moved as intents.

## Units

| Type | Meaning | Scale |
|---|---|---|
| `Tonnes` | Mass of CO2 | raw kilograms, 1,000 per tonne |
| `Money` | Cash | 10,000 per unit |
| `Rate` | Shares | parts per million |
| grams per unit | Intensity of a good or a grid | plain `Long` |

`CarbonCalendar` uses the game's 360-day year and 30-day month. Auctions fall on day 15 of each quarter (days 15, 105,
195, 285). A year or month is closed at the start of the day that begins the next one, before that day's emissions are
observed.

## Parts

### Emission accounting

* `EmissionFactors.kt`: `FuelFactor`, `EmissionFactorTable` (grams per unit of each fuel by good key, process factors
  by recipe, the electricity key) and `StandardFactors.table`.
* `Footprint.kt`: `EmissionScope` (direct, indirect, biogenic), `EmissionLine`, `FacilityActivity` (one day of one
  facility: consumed goods, batches by recipe, electricity used and produced, output value, energy spend),
  `EmissionCalculator`, and `ProductFootprints` (embedded grams per unit of a good, recursive through recipes with a
  depth limit and memo; a recipe with several producers averages them).
* `ProductionActivities.kt`: the adapter from `FacilityReport` and `Facility` to `FacilityActivity`.
* `Inventory.kt`: `FacilityRecord` (smoothed daily figures, active abatement effects), `CompanyYear`, `NationalYear` and
  `InventoryBook`. `CompanyYear.dominantFamily()` and `intensityGramsPerUnit` feed benchmarks.

### Regimes

`Regime.kt` defines `RegimeKind` (NONE, CARBON_TAX, CAP_AND_TRADE, INTENSITY_STANDARD), `RegimeStep` (the phase-in unit),
`CarbonRegime` and `RegimePresets`. A step carries coverage by industry family, a threshold, the tax rate and its
escalator, the cap and its yearly cut, the free share, floor and ceiling, penalty, intensity benchmark, offset limit,
whether indirect emissions are covered, and the share of an abatement project the state pays back. `CarbonRegime`
carries banking, vintage life, borrowing limits, holding limit, surrender window, make-good and repeat-penalty rules,
eligible credit kinds, reserve rule and green-fund share.

### Permit market

* `PermitHoldings.kt`: lots by company and vintage with cost basis, taken oldest first; expiry; holding limit checks.
* `Auction.kt`: `AuctionBid`, `AuctionResult`, `AuctionClearing` (uniform price, highest bids first, pro rata at the margin, floor, ceiling sales).
* `OrderBook.kt`: `PermitOrder`, `OrderMatcher` (price-time priority, trade at the resting price, capacity checked at
  each match through a callback so cash and permits are current), `BookLevel` depth.
* `Reserve.kt`: `ReserveDecision`, `StabilityReserve` (withhold above the upper band, release below the lower, cancel above
  the maximum stock, price-triggered release), `FreeAllocation` (largest-remainder split by weight).
* `PermitMarket.kt`: one country's state: supply per year, holdings, bids, orders, auction and trade history, price
  series, reserve and cancelled and expired totals.

### Compliance and levies

* `Compliance.kt`: `ComplianceAccount` per company and country (policy, pending year, carry, violations, history),
  `PendingYear`, `ComplianceRecord`, `ComplianceStatus`.
* `Levies.kt`: `Levies`, the monthly carbon tax with threshold catch-up, and `IntensityAssessment`, the levy on the excess over the benchmark.

### Credits and abatement

* `OffsetKind.kt`, `Offsets.kt`, `OffsetLifecycle.kt`: four kinds (reforestation, methane capture, clean cooking, direct
  air capture), `OffsetProfiles` (cost, delay, additionality, buffer, risk), the project lifecycle and the open market's
  stock and price pressure.
* `AbatementEffect.kt`, `Abatement.kt`: the three measure kinds, `AbatementCatalog` (options per facility from its
  footprint), `AbatementOption` (capex, running cost, tonnes saved, cost per tonne, payback), `MacCurve`, projects.

### Border

`Border.kt`: `BorderPolicy` (start year, phase-in, minimum intensity, covered goods, export rebate), `TradeFlow`,
`BorderAdjuster` (imports pay the difference between the two countries' carbon prices on the carbon embedded in the goods; exporters may get that cost back) and `BorderPolicy.goods` (empty means every good over the intensity threshold). `BorderBook` holds the policies.

### Reporting

`Reporting.kt`: `AnnualStatement`, `Benchmarks` (median and best by industry family), `RatingModel` (intensity 400,
trend 150, compliance 250, offsets 100, disclosure 100 points; grades AAA to D), `NationalReports`, `ReportBook`.

### Service

* `CarbonService.kt`: the public surface (`installRegime`, `setBorderPolicy`, `execute`, `advance`, `assessTrade`,
  `carbonPrice`, `usablePermits`) and the per-call context.
* `CarbonCycle.kt`: the daily step and the period closes.
* `CarbonSupply.kt`: yearly supply, free allocation, expiry, reserve review.
* `CarbonTrading.kt`: bids, auctions, orders, trades, surrender.
* `CarbonProjects.kt`: credits, abatement, border assessment.
* `CarbonSettlement.kt`: `CarbonSettlement`, `SettlementBuilder` with the ledger conventions.
* `CarbonEnvironment.kt`: `CarbonEnvironment`, `FixedEnvironment`, `CashBook`, `GreenSubsidyHook`.
* `CarbonState.kt`, `CarbonCodec.kt`: the owner of everything and its save format.
* `CarbonViews.kt`: immutable read models; `CarbonAi.kt`: the computer companies' decisions; `CarbonAdvice.kt`: advisor
  insights; `CarbonEvents.kt`, `CarbonCommands.kt`: events and commands.

### Presentation

`ui/present/CarbonPresenter.kt` and `CarbonMarketPresenter.kt` are pure and work against `CarbonDesk`, a small interface
the session implements. `ui/compose/screens/CarbonScreen.kt` is a thin screen over them.

## The day

`CarbonService.advance(tick, activities)` runs these steps, in this order, once per tick:

1. On the first ever tick, seed the open credit market.
2. If the tick starts a month, close the period: update the grid intensity, record the month's monitoring, assess taxes,
   pay running costs of abatement, close the credit market's month, add the market credits, and at a year start close
   the year.
3. Make sure supply exists for the current year (new yearly supply, free allocation, expiry of old vintages, reserve
   review).
4. Observe the day: add each facility's footprint to its company-year and the national inventory, keep the smoothed daily
   figures, apply abatement effects.
5. Allocate free permits that were waiting for a weight.
6. Hold the auction if it is an auction day.
7. Settle pending years whose deadline is today.
8. Advance credit projects.
9. Advance abatement projects.
10. Remember the tick.

A tick already processed returns an empty result.

## Money

All of it leaves as `CarbonSettlement`. Payments are `SettlementIntent`s; adjustments are `TransactionDraft`s.

| What | Reason | Deferrable | Notes |
|---|---|---|---|
| Permit or credit purchase from the government or the market | PURCHASE | no | Asset is intangible; pre-checked against the `CashBook` |
| Secondary trade | PURCHASE | no | Seller's gain or loss is booked from the lot's cost basis |
| Penalty | FEE | yes | statutory charge |
| Carbon tax, intensity levy | TAX | yes | statutory charge |
| Abatement capital | PURCHASE | no | contractors are `LedgerOwner.households()` |
| Abatement running cost | FEE | yes | monthly |
| Subsidy | SUBSIDY | no | limited by the green fund and the treasury |
| Surrender, retirement, expiry | ADJUSTMENT | n/a | write-down at cost basis |

## Compliance cycle

At year close each regulated company with an active account gets a `PendingYear`: verified covered emissions, plus any
shortfall carried in, is the obligation; the deadline is the close plus the regime's surrender window. A company can
surrender permits by command at any time before the deadline. At the deadline:

1. Surrender the usable permits it holds, oldest vintage first.
2. If the company's policy allows, retire credits up to the regime's offset limit.
3. If the policy allows borrowing, surrender next year's permits up to the borrow limit; interest is carried as extra
   tonnes in the next obligation.
4. Fine the rest at the penalty, raised for each consecutive year of breach; carry it forward if the regime says so.

A removed or replaced regime settles pending years as unregulated. Inactive companies are skipped and not fined.

## Events

`CarbonEvent`: `RegimeChanged`, `AuctionCleared`, `PermitsAllocated`, `ReserveAdjusted`, `YearClosed`, `TrueUp`,
`TaxCharged`, `PermitsExpired`, `OffsetProjectChanged`, `AbatementDone`, `AbatementRetired`, `BorderLevy`, `RatingChanged`.

## Save format

`CarbonCodec.write` begins with a varint version (currently 1) and then writes, in a fixed order: regimes, markets,
inventory, compliance, offsets, abatement, border, reports, grid, green fund and receipt maps, counters and `lastTick`.
All maps are written in key order. An unknown version throws `ByteReader.CorruptDataException`. The state hash is
`CanonicalHasher` over the encoded bytes, section tag `carbon`.

## Tests

Tests mirror the source paths under `app/src/test/java/app/many/economytycoon/carbon/` and `ui/present/`.

* Unit: `TonnesTest`, `EmissionCalculatorTest`, `RegimeTest`, `LeviesTest`, `AuctionClearingTest`, `OrderMatcherTest`,
  `PermitHoldingsTest`, `StabilityReserveTest` (with free allocation), `OffsetsTest`, `AbatementTest`, `BorderTest`,
  `InventoryTest`, `ReportingTest`, `CarbonSettlementTest`.
* Service: `CarbonAuctionTest`, `CarbonMarketTest`, `CarbonComplianceTest`, `CarbonLevyTest`, `CarbonOffsetServiceTest`,
  `CarbonAbatementServiceTest`, `CarbonBorderServiceTest`, `ProductFootprintsTest`, `ProductionActivitiesTest`,
  `CarbonServiceTest`, `CarbonViewsTest`.
* Whole-package: `CarbonCodecTest` (round trip to the same bytes, hash stability, version refusal, truncated saves,
  resume from a save), `CarbonDeterminismTest` (same seed, same hash, activity order, replay), `CarbonScenarioTest` and
  `CarbonServiceScenarioTest` (multi-year stories), `CarbonAiTest`, `CarbonAdviceTest`.
* Presenters: `CarbonPresenterTest`, `CarbonMarketPresenterTest`, using `CarbonDeskFixture` over a real service.

`CarbonTestSupport` holds the shared fixture: a service on a fixed environment, helpers to feed days of activity,
and `busy(...)`, a standard multi-country story.

## Known limits

Efficiency savings are reported but not realised in production; border flows need a
fulfilment hook; the rating spread is published but not consumed.
