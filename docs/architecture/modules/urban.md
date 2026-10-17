# Module: urban

City growth and urban planning. Package `app.many.economytycoon.urban`. Depends on core, finance (settlement
intents only) and nothing else.

## Responsibilities

- Per-city plan: districts, zoning, density, building stock, rent, vacancy, tenant protection.
- Land value, housing, development, gentrification.
- Transit and road network, travel times, mode choice, traffic assignment, congestion.
- Municipal budget, capital projects, council voting, lobbying, petitions, concessions.
- Urban events, liveability index and ranking, planning advisor, heuristic municipal mayor, map overlays.

## Shape

| Part | Files |
|---|---|
| Foundations | UrbanIds, UrbanParams, Grid, CityGeometry |
| Land and buildings | Zoning, BuildingStock, District, Externalities, LandValue, Housing, Development, Gentrification |
| Mobility | Congestion, TransitNetwork, TravelTimes, ModeChoice, TrafficAssignment |
| Government | Budget, CapitalProjects, Council, Lobbying, Petitions, Concessions |
| Events and scoring | UrbanEvents, Liveability, UrbanAnalysis, PlanningAdvisor, MunicipalAi |
| State | CityPlan, UrbanState, UrbanCodec, CityFactory |
| Service | UrbanCommands, UrbanExecutor, UrbanService, UrbanDaily, UrbanMonthly, UrbanEffects, UrbanLedger, UrbanNotices |
| Read side | UrbanViews, UrbanReader, UrbanOverlay |

Presenters: `ui/present/UrbanPresenter`, `UrbanTransitPresenter`, `UrbanCouncilPresenter`,
`UrbanBudgetPresenter`, `UrbanMapPresenter`. Screens: `UrbanScreen`, `UrbanCouncilScreen`, `UrbanTransitScreen`,
`UrbanBudgetScreen`.

## Update cycle

Daily: capital projects, petitions, concessions. Monthly, in this order: events, surfaces and land value,
housing and rents, development, gentrification, traffic assignment, ageing and maintenance, finances and
concession settlements, council mood and election, liveability. Land value precedes housing because rents
follow value; traffic follows development so new floor area is loaded in the same month; finances follow
traffic because tolls and fares depend on flows.

## Determinism

Fixed-point only. Random streams are named `urban.<topic>` and keyed by city id. Every event draws a fixed
number of values whether or not it fires. Iteration is over sorted collections.

## Money

The service returns `SettlementIntent` values with stable keys (`urban:fee:<petition>`,
`urban:lobby:<petition>:<n>`, `urban:cx:<concession>:<tick>` and so on). It never posts. A city is paid and
pays as `LedgerOwner.government(country)` because there is no city-level ledger owner.

## Saves

`UrbanCodec` (version 1) writes the id counters, then every plan in city order. `UrbanState.hashInto` covers
all of it. After loading call `UrbanService.refreshScores()`.
