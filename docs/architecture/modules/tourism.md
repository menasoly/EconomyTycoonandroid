# Module: tourism

Destinations, visitor flows, accommodation, attractions and the businesses that sell them. See ADR-0016 for the
reasoning.

## Responsibility

Where visitors go and why, how rooms and tickets are booked and priced, how intermediaries and tour operators take a
share, how reviews and staffing feed back, what the visitors' spending does for local business, and the ledger intents
that follow. It does not own workers, airlines or weather; it reads seat capacity, staff supply and weather events
that the caller passes in.

Depends on: `core`, `finance` (settlement intents, owners), `world` (weather events).
The simulation wires it in through `simulation/TourismRunner` (see Integration below); the package itself still imports nothing outside these three modules.

## Owned entities

| Type | File | Notes |
|---|---|---|
| `DestinationId`, `OriginId`, `PropertyId`, `AttractionId`, `OperatorId`, `AllotmentId`, `IntermediaryId`, `ShockId`, `TourismCounters` | `TourismIds.kt` | typed ids and the counters that make them |
| `VisitorSegment`, `SegmentProfile`, `Channel` | `Segments.kt` | who travels and how each segment books |
| `Destination`, `DestinationDrivers`, `DestinationDay`, `DestinationMonth` | `Destination.kt` | drivers, taxes, festival, history |
| `OriginMarket` | `Markets.kt` | outbound volume and currency strength |
| `Property`, `RoomClass`, `PropertyKind`, `RatePolicy`, `CancellationPolicy`, `NightBook`, `PropertyMonth` | `Accommodation.kt` | rooms, rates, the fourteen-day book, month totals |
| `ReviewBook` | `Reviews.kt` | rolling rating |
| `Attraction`, `AttractionKind` | `Attractions.kt` | capacity, ticket price, visits |
| `Intermediary`, `IntermediaryKind` | `Distribution.kt` | agency or platform, commission, reach |
| `TourOperator`, `Allotment`, `AllotmentStatus`, `ContractSide` | `TourOperators.kt` | blocked rooms and their terms |
| `Shock`, `ShockKind` | `Shocks.kt` | events that move demand or supply for a time |
| `TourismState` | `TourismState.kt` | the whole module state, hashable |

## Rules (pure)

* `Seasonality` - month and weekday index; festival boost.
* `Attractiveness` - drivers to an appeal per segment, with the reason lines.
* `VisitorFlows`, `Allocation`, `DemandForecast` - origin by segment by destination, largest-remainder splits.
* `AccommodationRules`, `RevenueManagement` - sellable rooms, pickup forecast, dynamic price, overbooking optimiser.
* `AttractionModel`, `AllotmentRules`, `LocalSpending`, `TourismTax`, `GuestSatisfaction`, `ShockGenerator`.
* `BookingEngine` and `StayRealizer` (internal) - place requests by channel and turn bookings into stays.

## Services

* `TourismService` - the only writer. Destinations, markets, properties, attractions, intermediaries, operators,
  allotments, campaigns, shocks; `advanceDay`, `settleMonth`.
* `TourismDayRunner` (internal) - the daily pipeline: shocks, flows, bookings, stays, attractions, reviews, spending,
  price level.
* `TourismSettlement` - the month as `SettlementIntent`s: revenue, commission, listing fees, running cost, wages,
  walk cost, VAT, levy, allotments, packages, tickets.
* `TourismCommandExecutor` - checks that a company only touches its own property, attraction, operator or platform.

## Read models and analysis

`TourismAnalytics` (property KPIs, market index, seasonality curve), `TourismAdvisor` (sixteen rules over
`PropertyFacts`), `TourismNews` (notes from events and from a settlement), `TourismGlossary`, `HotelierAi`.
Presenter: `ui/present/TourismPresenter`. Screen: `ui/compose/screens/TourismScreen`.

## Persistence

`TourismCodec`, version 1, written with `ByteWriter`; unknown versions raise `CorruptDataException`. The hash covers
every saved field in a fixed order with maps iterated by id.

## Invariants

* Booked room nights on a stay date never exceed sellable rooms plus the overbooking allowance.
* Arrivals by segment and by origin each add up to the destination's arrivals.
* Settlement keys are unique per month; a month cannot be settled twice.
* The same seed and the same commands give the same hash; randomness is used by shocks only.

## Known limits

The first thirteen days of a run ramp up (booking warm-up). Restaurants are not a separate entity; their income is part
of ancillary revenue and local spending. Package prices are net rates, not a retail price.

## Integration

- Commands: `EstablishTourism` (system only, codec tag 122) sets the market up; `Command.Tourism(TourismCommand)` (codec tag 123, body written by `persistence/TourismCommandCodec` with sub-tags 1 to 24) carries every decision of a player or computer company. `simulation/TourismRunner` runs both. Commands that cost money check the company's cash first (`TourismCommandExecutor.cost`), create nothing when it is short, and pay at once; a second campaign for the same town on the same day is refused so its payment key cannot repeat.
- Switch: nothing exists until `EstablishTourism`, which `NewGame.start` issues and the command log replays. Without it there is no hash contribution, no `tourism` snapshot section, no daily step and no random draw, so older games play exactly as before.
- Set-up: each region gets up to two destinations (coast, airport, port and capital first, then the biggest towns) with drivers read from the city (access from its airport, port and links, safety from the country's stability, culture and nightlife from size, nature from terrain and coast), the country's sales tax as VAT, a home market per region (a trip every five years per person, refreshed monthly from the population) and an overseas market, a booking platform, an agency, an operator, and per town a main hotel (given to a computer company when there are any), an inn and a four-star house, with a museum in capitals and a beach club on coasts.
- Tick: the day runs in the procurement phase after logistics, from the world's weather, `WorldTransport` and `WorldStaffSupply`, the country's minimum wage and the city's population. Computer hoteliers (`AiHoteliers`) review their houses on the fifth day of each month in the decisions phase. The month is billed on its last day as the last group of the settlement batch. A company that ceases to exist closes its hotels and attractions and ends its contracts (`Economy.retire`).
- Money: households and parties with no company behind them (visitors from abroad, suppliers, agencies without an owner) go through the households' pool, so the money supply is conserved.
- Local spending: the uplift of visitors' shop spending over residents', weighted by the destination's share of its region's people and capped at 15%, raises the demand shift of every consumer good in the region for the day (`TourismRunner.liftDemand`, applied in the demand phase). It changes what households buy, not how much money they have; visitors' own spending outside the room is not paid out separately.
- Session and UI: `GameSession.tourism`, `tourismNews`, `tourismInsights`; news (critical notes for everyone, the rest for towns where a player has a house or attraction, repeats held back, plus a monthly summary), alerts for hotels with an insight at priority 70 or more, advisor insights routed to `Route.Tourism`, the agenda's contract-end dates, a More-menu entry, glossary, tip and help topic. The screen is read-only; decisions are given as commands.
- Known gaps: hotel and restaurant facility types from the content packs still exist beside the property entity and are not converted; festivals are not on the agenda; the booking horizon ramps up over the first two weeks; a new unlisted hotel sells only through the direct channel until it is listed.
