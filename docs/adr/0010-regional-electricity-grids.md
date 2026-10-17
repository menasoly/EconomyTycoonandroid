# ADR-0010: Regional electricity grids with hourly merit-order dispatch inside a daily tick

Status: accepted

## Context

Electricity was a good like any other: power plants turned fuel into `electricity`, a regional ledger summed what was
used and split the bill between producers. That gave no price signal for scarcity, no reason to hold spare capacity,
no storage, no interconnection, and no consequence for a factory when supply failed. The game needs the opposite: power
that is cheap when wind blows and dear on a cold evening, plants that break, a grid operator who builds when it runs
short, contracts that fix a price, and blackouts that stop production.

The simulation advances one tick per day, so the daily rhythm of demand, which drives the whole problem, is not visible
at that resolution. The state must still be saved exactly and replay bit for bit, with no floating point in any rule.

## Decision

A new module, `energygrid`, models one grid per region. Each day is dispatched as 24 independent hours inside the
daily tick. The hour is a calculation, not a tick: no state exists between hours except the plants' output (for ramping),
the batteries' charge and the open blackout, all of which are saved.

* Plants (coal, gas, nuclear, hydro, wind, solar, storage) offer their capacity at marginal cost. A pure function
  (`DispatchEngine`) fills demand in merit order within ramp limits and minimum stable output; the price is the cost of
  the dearest plant that moved, lifted linearly toward a cap when reserve is thin. Unmet demand prices at the cap.
* Demand is a base draw per connection times a shape (hour of day, weekday, season, temperature) in parts per million.
  Short supply is shed by customer class in a fixed order; a stretch of unserved hours is one blackout with a damage
  figure, and each connected factory gets a `FacilityImpact` for the wiring to apply.
* Outages, maintenance and weather use only the named streams `energy.outage` and `energy.weather`, one partition per grid,
  with a fixed number of draws per day whatever happens, so adding a plant never shifts another grid's luck.
* Batteries plan the day by dynamic programming over charge levels against a cautious price forecast, and only
  commit when a trial dispatch shows a profit. Interconnectors move power from the cheap side to the dear side with
  losses and a wheeling charge; the flow is halved until the prices do not cross.
* Contracts: power purchase agreements are contracts for difference settled monthly (baseload or as-produced), with a
  shortfall penalty and an exit fee. A yearly capacity auction pays available firm capacity, funded by a levy per MWh.
  Retail tariffs pass wholesale through with margins; a household price cap is paid for by the government.
* The grid operator (units with no company owner) plans investment by levelised cost per firm kilowatt, with share caps,
  and retires worn plant. Competitors use a separate pure heuristic (`UtilityAi`) over the same figures.
* Money leaves the module only as `SettlementIntent` values returned to the caller. Nothing posts to the ledger from
  inside the module. Everything is integer: kilowatts, kilowatt-hours, fixed-point money per megawatt-hour, ppm ratios.
* State is `EnergyGridState` with a codec (version byte) and canonical hash, a section of the snapshot like the others.

## Consequences

- Price and supply questions have an answer that depends on weather, outages, fuel and the other customers; a short
  grid hurts, so spare capacity and storage have value and the AI builds for the same reason the operator does.
- The daily tick stays the unit of simulation, so saves, replay and the tick pipeline keep their shape. A tick costs
  24 dispatches per grid, each over at most sixty units; the tests run a year of two linked grids in a second or two.
- The calculation per hour is a pure function and is tested against merit orders worked out by hand.
- The module depends on `core`, `finance` and `world` only. Facilities, fuel markets and weather reach it through the
  runner: a power plant facility becomes a unit, a factory becomes a load, fuel is bought by the
  runner from the day's heat demand.
- Fuel is not consumed through production. This is a deliberate split between a physical model the grid owns and a
  goods market the economy owns; the wiring must buy the fuel or plants run free.
- Adding the section changes the economy hash, so golden values are recomputed when it is wired in.
- Hourly resolution inside a tick is not available to other modules. If a later module needs hours, it reads
  `Grid.lastDay` (24 `HourProfile`s) rather than adding its own clock.
