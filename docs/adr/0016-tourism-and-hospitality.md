# ADR-0016: Tourism and hospitality with booking curves, revenue management and intermediaries

Status: accepted

## Context

Hotels, restaurants and attractions existed only as generic facilities that sold a good at a market price. That cannot
express what makes a holiday economy: visitors who choose a destination, who book weeks ahead, who arrive in a season,
who are sold by agencies and online platforms for a commission, whose rooms are blocked by tour operators, and whose
spending lifts the shops around the hotel. It also cannot express the hotelier's real decisions, which are about price
over time, overbooking, channel mix and how much to invest in quality.

The module has to run inside the one-tick-per-day simulation, save exactly, replay bit for bit and use no floating
point in any rule. Randomness must stay rare enough that two runs with the same seed and the same commands agree.

## Decision

A new module, `tourism`, models destinations, visitor flows, accommodation, attractions and the businesses that sell
them.

* A destination has drivers (climate, culture, nature, beach, nightlife, safety, access, price level) that become an
  appeal per visitor segment. Origin markets send a daily outbound volume; flows to destinations are an integer
  allocation by largest remainder, so totals are exact and the order is fixed.
* Demand is booked ahead. Each property keeps a fourteen-day horizon of stay dates. For each stay date and segment a
  booking curve says how much of the final demand arrives at each lead time. The first thirteen days of a run therefore
  have not seen full lead-time bookings and occupancy ramps up (the warm-up); this is a property of the model, not a
  fault, and the tests allow for it.
* Channels are direct, agency, online platform and tour operator. A request that cannot be placed through an
  intermediary spills into direct booking. Listing therefore wins share from competitors and costs commission, but a
  lone hotel gains no extra guests from a listing. Guests may trade up to the next room class.
* Cancellations and no-shows are expected values, not draws. Overbooking is sized by a binomial optimiser that weighs
  the walk cost against an empty room. Walked guests cost money and reviews.
* Revenue management is a pickup forecast and a dynamic price multiplier inside a floor and a ceiling. Fixed pricing
  stays available. The destination price level follows the revenue index, so it answers to dynamic pricing.
* Tour operators hold allotments: rooms per night at a net rate, a release time, a committed share and a shortfall
  charge. Ending a contract early costs a quarter of the remaining committed value. Operators settle before
  allotments each month, because an operator reads the month's sales off the allotments and the allotments then clear
  them.
* Reviews move a property rating from satisfaction (condition, service staff, price against value, walks). Staff come
  from a `StaffSupply` and seats from a `TransportCapacity`; both are interfaces the wiring implements over the
  workforce and logistics modules, with full-supply defaults for tests.
* Shocks (festival, strike, storm, outbreak, currency swing, advisory) are the only users of randomness, through the
  named stream `tourism.shock`, with a fixed number of draws per day. Seeds therefore diverge only after a random shock.
* Money leaves the module only as `SettlementIntent` values with keys unique per month. Nothing posts to the ledger
  from inside the module. Everything is integer: persons, room nights, fixed-point money, ppm ratios.
* State is `TourismState` with `TourismCodec` (version byte) and a canonical hash, a section of the snapshot.
* Computer-controlled hoteliers use a pure heuristic (`HotelierAi`) over the same figures the advisor uses.

## Consequences

- Questions about price, season, channel and quality have answers that depend on each other and on shocks, so a hotel
  that overprices, understaffs or lets its condition run down loses guests and reviews, and the advice list says why.
- The daily tick stays the unit of simulation. A day costs one allocation per destination and one booking pass per open
  property over fourteen stay dates; the tests run a year of a populated world in a few seconds.
- The module depends on `core`, `finance` and `world` (weather events) only. Transport, staff, residents and wages
  reach it through `DayInputs`.
- Occupancy of a full hotel sits near seventy per cent, not ninety, because cancellations and no-shows are priced in.
- Spill-to-direct makes platform listing a competitive tool rather than a free source of guests. Tuning it is a
  parameter change in `TourismParams`, not a rewrite.
- A hotel with only standard rooms registers denied demand for the classes it lacks. That is intended: it feeds the
  expansion advice.
- Adding the section changes the economy hash, so golden values are recomputed when it is wired in.
