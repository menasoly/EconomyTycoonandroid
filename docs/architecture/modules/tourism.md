# Module: tourism

Destinations, visitor flows, accommodation, attractions and the businesses that sell them. See ADR-0016 for the
reasoning.

## Responsibility

Where visitors go and why, how rooms and tickets are booked and priced, how intermediaries and tour operators take a
share, how reviews and staffing feed back, what the visitors' spending does for local business, and the ledger intents
that follow. It does not own workers, airlines or weather; it reads seat capacity, staff supply and weather events
that the caller passes in.

Depends on: `core`, `finance` (settlement intents, owners), `world` (weather events).

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
