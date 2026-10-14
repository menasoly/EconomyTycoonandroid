# World Simulation

Module: `world`, `macro`.

## Generation

World generation is seeded and deterministic: regions and countries, cities, resource deposits, climate zones, infrastructure, starting government policies. Presets and scenarios can supply authored worlds instead.

## Population

Population segments by income band per city. Growth, migration (toward higher income and employment), and need levels update on a slow cadence (not every tick) to save cost; fast-changing values (demand) are derived.

## Resources and climate

Deposits deplete according to extraction. Weather and seasons modulate agriculture and energy; seasonality tables in content.

## Infrastructure

Roads, rails, ports, airports, power and water networks as graphs with capacity and condition. Utilities constrain facilities. Maintenance spending is required.

## Real estate

Land parcels with zoning and valuation derived from location, infrastructure and demand. Leasing and purchase through ledger transactions.

## Macroeconomy

Aggregated indicators: inflation, GDP, unemployment, interest rate. Central bank policy rules respond with lag. Currency exchange from trade balance and rate differences. Macro is computed last in the tick (phase 12) and feeds the next tick only.

## Government

Policy agents set taxes, tariffs, subsidies and regulations at their own decision intervals, constrained by a budget account. Antitrust and regulation effects are applied as modifiers by rules, never by direct state overwrites.

## Level of detail

Distant regions are simulated at lower cadence (every N ticks) with aggregated stats; regions near the player or with active player facilities run at full cadence. The cadence is part of deterministic state.
