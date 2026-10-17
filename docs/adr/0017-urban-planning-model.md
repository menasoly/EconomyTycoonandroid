# ADR-0017: Urban planning as a district grid with its own partition

Status: accepted

## Context

Cities so far are single points: a population, a wage and a market. Players and rivals decide where to build
factories, shops and housing, but nothing explains why one part of a city is expensive and another is not, why
commutes get longer as a city grows, or why a rezoning fight can decide whether a project is built at all. The
cost of land, rent, congestion and the goodwill of the council are the questions a real developer asks first.

A full agent-based city would be far too heavy for a phone, and anything that draws on floating point or hash
order would break replay.

## Decision

Each city gets an urban plan: a 12 by 12 cell grid grouped into 9 districts of 4 by 4 cells. A district has a
zone (residential, commercial, industrial, mixed, green, civic), density limits, a building stock with ages,
a rent and a vacancy, and tenant protection. Everything is integer arithmetic on fixed-point scales and the
only randomness comes from named streams of the shared provider, with a fixed number of draws per event.

Land value is a surface built from accessibility, amenities, pollution, noise, crime and the value of the
neighbouring cells. Housing supply and demand set rents and vacancy; developers start projects where the
return beats the cost, subject to the permitted floor area. Rising rents on poor districts build up
gentrification pressure that raises displacement, anger and, eventually, a backlash event.

Travel is modelled between districts, not between people. A gravity model spreads trips, a logit model with an
integer exponent table splits them across modes, and the method of successive averages loads roads and lines
until the flows settle. Road delay follows the BPR curve with exponent 4. Lines have capacity, speed, headway
and fare. Accessibility from the travel-time matrix feeds back into land value.

The city government has a budget (taxes, service levels, debt, a credit rating), a council of nine seats that
votes on petitions, and capital projects with phases and overruns. Companies and the city file rezoning and
variance petitions; lobbying is a company command plus a hook interface that other modules can implement.
Public-private concessions (toll roads, availability payments, transit operations) are tendered, awarded and
settled monthly.

The partition lives in its own state object with its own codec and canonical hash. It never posts to the
ledger and never writes to the world: it returns settlement intents and a small output record (housing
capacity, rent index, boom strength, liveability) that the orchestrator applies. A pure advisor turns the
model into ranked insights and a simple heuristic mayor uses the same commands as a human player.

## Consequences

- Fixed grid and district counts keep saves small and the tick cost bounded (a monthly pass touches 144 cells
  and 36 district pairs per city).
- Nothing outside the package depends on it, so it can be wired in with one tick hook, one snapshot section
  and a set of commands.
- The district model is separate from the existing parcel and zone types in the world package. The two
  describe different things (an owned plot versus a planning area) and are joined only through the outputs.
- Calibration is done in the parameters object; changing a default changes golden values and needs a new
  snapshot version only if the layout changes.
- Rejected petitions still burn an id, so ids are not dense. Tests rely on ordering, never on gaps.
