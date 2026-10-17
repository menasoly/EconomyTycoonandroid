# ADR-0012: Carbon pricing as one package with permits, offsets and abatement

Status: accepted

## Context

Heavy industry in the game burns fuel and uses electricity, and a government can decide to make that costly. The
game needs the whole chain: how much a facility emits, what a government charges for it, how a company meets the
charge, and what the charge does to the rest of the economy. Players should be able to choose between paying, buying
permits, buying credits and cutting emissions, and the computer-controlled companies should make the same choice for
the same reasons.

Three ways to model this were considered.

1. A flat per-tonne tax added to the existing tax code. Simple, but it has no market, no choice between abating and
   paying, and nothing for an economist to find interesting.
2. A permit price drawn from a curve. Easy to tune, but the price then has nothing to do with what companies do, and
   the "cut or buy" decision becomes decoration.
3. A permit market that clears from real bids, with the rules of existing schemes (auctions, free allocation, banking,
   a stability reserve, an offset limit, a fine for a shortfall) so that the price is found by supply and demand.

## Decision

Option 3, as one package, `carbon`, that owns everything about emissions and their price and nothing else.

* Emissions are accounted per facility per day from what production reports: fuels burned, process emissions of the
  recipes run, and electricity used at the national grid's intensity. Totals roll up to company-year and country-year
  inventories. Biogenic carbon is tracked and not priced.
* A country has at most one regime: none, a carbon tax, a cap and trade scheme or an intensity standard. A regime is
  a list of steps, each effective from a year, so coverage widens, free allocation shrinks and the cap tightens as a
  phase-in. Replacing a regime settles what is pending as unregulated.
* The permit market has quarterly uniform-price auctions, a secondary order book, per-vintage holdings with cost basis,
  banking inside a vintage life, borrowing from next year at interest, a price floor and a ceiling at which the
  government sells more, and a stability reserve that withholds permits when they are plentiful and releases them
  when the price spikes.
* Compliance runs on a deadline. At year close each company has an obligation; at the deadline the package surrenders
  permits the company holds, then credits up to the regime's limit, then optionally next year's permits, then fines
  what is left and carries it forward. A repeat offender pays more.
* Credits come from projects with a lifecycle (development, validation, issuing, completed or rejected), with
  additionality haircuts, a buffer held back, a delivery risk and a price that moves with the open market's stock.
* Abatement is a catalogue of measures per facility (efficiency retrofit, fuel switch, capture) with capital cost,
  running cost and a tonnes-per-year effect. The package builds the marginal abatement cost curve and the computer
  companies use it.
* Reporting produces yearly statements, intensity benchmarks against industry peers, national summaries, and a
  carbon rating that finance can use as a loan spread.
* A border adjustment charges imports the difference between the two countries' carbon prices on the carbon embedded
  in the goods, and can rebate exports, using product footprints computed recursively through the recipes.

Design rules that follow from the coding standards and that shape the package:

1. All state changes are deterministic. Amounts are fixed point (`Tonnes` in kilograms, `Money`, `Rate` in ppm). The
   only randomness is the open credit market's monthly supply, drawn from a named stream.
2. The package never moves money. It returns settlement intents (payments) and ledger drafts (write-downs of permits
   and credits) in a `CarbonSettlement`, and the runner posts them in the tick's settlement phase. Purchases are not
   deferrable; penalties, taxes and levies are deferrable statutory charges. A `CashBook` pre-check stops one call
   from promising the same cash twice, so there is no escrow.
3. The package reads the rest of the game only through the narrow `CarbonEnvironment` interface (cash, whether a
   company is active, treasury cash, facility owner and build cost) and through the day's production reports. Nothing
   in the base code depends on it.
4. Mutation happens only inside `CarbonService`. The views (`CarbonViews`) and presenters read immutable snapshots.
5. State is saved by `CarbonCodec` (version byte first) and hashed from the saved bytes, so the hash can never miss a
   field the save keeps.

## Consequences

* The price of carbon is an outcome. A tight cap, a hot economy or a shortage of credits all show up in auctions the
  same quarter. Tuning is done through regime parameters (cap, floor, ceiling, penalty, reserve thresholds), not by
  editing a curve.
* Players and computer companies use the same commands, so the AI is a heuristic over the same views, not a separate
  model.
* The package adds a snapshot section, eleven commands, one tick hook and a screen. Games that never install a regime write no section
  and hash exactly as before; economic results do not change while no regime is installed.
* Offsets and permits are intangible assets in the books with a cost basis per lot, so surrendering, retiring and
  expiring write them down at what was paid, and selling shows a real gain or loss.
* Efficiency retrofits reduce real fuel and electricity use through per-facility production modifiers, so the footprint
  falls because less is burned, not by a separate discount.
* A day is processed at most once per package (`lastTick`), which makes replays and double calls harmless.
* Adding a country's regime is data, not code. New regime kinds need a new `RegimeKind`, an entry in the codec (enums
  are written by ordinal and must only be appended to) and tests.

## Alternatives not taken

* Putting permits into the generic goods market. Permits have vintages, expire, are limited per holder and are
  auctioned, so they would need special cases in every part of the goods market.
* Escrowing cash for open bids. It would make bids safe but locks cash and complicates every cash read. Bids are
  checked when placed and again when the auction clears; a bidder who can no longer pay is dropped from that auction.
* A continuous price from an equilibrium formula. It would be exact and dull, and it could not show a squeeze, a
  price collapse after a recession or the effect of the stability reserve.
