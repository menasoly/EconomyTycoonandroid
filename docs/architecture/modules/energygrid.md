# Module: energygrid

Regional electricity grids. One `Grid` per region, advanced once per tick as 24 dispatched hours. See ADR-0010 for the
reasoning.

## Responsibility

Supply and demand for electricity: plants and their availability, load shape, price formation, shortage and blackout,
batteries, links between regions, long-term contracts, retail tariffs, the capacity market, grid investment, and the
ledger intents that follow from them. It does not own facilities, fuel markets or weather; it reads weather events and
fuel prices that the caller passes in.

Depends on: `core`, `finance` (settlement intents, owners, chart), `world` (climate zones and weather events).

## Owned entities

| Type | File | Notes |
|---|---|---|
| `GridId`, `UnitId`, `LoadId`, `PpaId`, `LinkId`, `BlackoutId`, `GridCounters` | `GridIds.kt` | typed ids and the counters that make them |
| `Technology`, `TechProfile`, `FuelPrices`, `GridUnits` | `Technology.kt` | fixed profile of each kind of plant and the unit conversions |
| `GenUnit`, `UnitStatus` | `GridUnit.kt` | one plant or battery; mutable fields change only through the service |
| `GridLoad`, `CustomerClass` | `GridLoad.kt` | a metered connection; shed rank decides who is cut first |
| `Grid`, `GridParams` | `Grid.kt` | units, loads, history, blackouts, outages, capacity round, levy |
| `Interconnector` | `Interconnector.kt` | capacity, loss, wheeling charge, counters |
| `Ppa`, `PpaProfile`, `PpaStatus` | `Ppa.kt` | contract for difference |
| `BlackoutRecord`, `FacilityImpact` | `Blackout.kt` | a run of dark hours and what it did to a facility |
| `EnergyGridState` | `EnergyGridState.kt` | the whole module state, hashable |

## Rules (pure)

* `DispatchEngine.dispatch(DispatchInput)` - one hour, merit order, ramps, scarcity price.
* `StoragePlanner` - day plan for a battery over its charge levels.
* `TradeDispatcher` - flow over links between grids for one hour.
* `LoadShape` - normalised demand curves by hour, weekday and season.
* `GridWeatherModel` - availability factors for wind, sun, water and temperature from the climate zone and weather events.
* `OutageModel` - forced outages and maintenance, randomness passed in.
* `Tariffs`, `PpaSettler`, `CapacityMarket`, `InvestmentPlanner`, `LoadShedder`, `BlackoutDamage`.

## Services

* `EnergyGridService` - the only writer. Grids, units, loads, links and agreements; `advanceDay`, `settleMonth`.
* `GridDayRunner` (internal) - the daily pipeline: weather, lifecycle, demand, storage plans, 24 hourly dispatches with
  trade, shedding and blackouts, finishing the day, agreement accrual, capacity auction, operator investment.
* `GridBilling` - turns a month into `SettlementIntent`s: customer bills, pool payments, capacity, price cap
  compensation, agreement settlements and penalties.
* `GridCommandExecutor` - checks that a company only touches its own plant and connections.

## Read models and analysis

`GridAnalytics` (load duration curve, load factor, carbon intensity, renewable share, fleet mix, capacity factor, price
statistics, loss-of-load and availability indices, reserve margin), `FuelDemand` (heat burned per fuel), `GridAdvisor`
(rules over `GridFacts`), `GridNews` (events to notes), `GridGlossary`, `UtilityAi` (build, retire, mothball, restart
and contract wishes for computer-controlled utilities), and in `ui/present` the `EnergyGridPresenter`.

## Events produced

`GridEvent`: unit failed, maintenance started or postponed, unit back, commissioned, ordered, retired, blackout started
and ended, price spike, capacity auction, agreement shortfall or lapse. They are returned in `DayReport`, never published.

## Persistence

`EnergyGridCodec` (version 1): grids with units, loads, history (720 days), last day's hours, blackouts (100), outages
(200) and capacity round; links; agreements; counters; last tick and last billed month. Round trips bit for bit.
`EnergyGridState.stateHash()` covers every field that affects a later day.

## Randomness

`energy.weather` and `energy.outage`, partition = grid id. 26 weather draws and a fixed number of outage draws per day.

## Limits

60 units and 400 loads per grid, 8 open agreements per company, agreements of 30 to 1800 days, history and records
trimmed to their caps.

## Tests

One test class per rule and service, plus `GridScenarioTest` (storage, links, outages, investment, capacity market,
price caps), `GridDeterminismTest` (same seed, save in the middle, repeatable billing), `EnergyGridCodecTest`, and the
presenter test. Shared worlds are in `GridTestSupport.kt`.
