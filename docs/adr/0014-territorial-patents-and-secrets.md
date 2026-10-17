# ADR-0014: Patents are territorial rights, trade secrets are a separate asset, and the module never posts to the ledger

Status: accepted

## Context

Research gives a company a technology. Nothing yet decides who may use it, who may stop others, or what a rival
learns by watching. The game needs a protection layer with real trade-offs: a patent publishes the idea and expires,
a secret costs little but can leak, a licence turns a rival into a customer, and a lawsuit costs both sides whoever
wins. The layer must be deterministic, replayable and cheap enough to run for every company every day.

Three shapes were considered for the patent itself. One record per invention with a single "protected" flag is
simple but cannot say that a patent is strong in one country and refused in another. A global right with a country
list loses the fact that fees, examination, term and courts differ by place. A family of national filings under one
invention record keeps those facts where they live.

## Decision

- A patent is one record per invention holding one `Filing` per country. Examination, grant, fees, lapse, expiry
  and revocation happen per filing. The patent is in force while any filing is.
- A trade secret is its own record with a protection level, a count of people who know it and a hazard that
  rises with exposure and falls with security spending. Its life is drawn from the hazard, not decided up front.
  Filing a patent for the same technology ends the secret.
- Licences follow the patent. A licence names patents, a scope of industries and countries, a royalty basis and
  a term. Pools, cross-licences, FRAND commitments on standard-essential patents and compulsory licences are all
  licences with a flag, so the royalty and audit code exists once.
- Courts are modelled as a margin between the strength of the claim and the strength of the defence. Far from the
  line the result is certain; inside a grey band one seeded roll decides. The loser pays damages and a share of
  the winner's costs, so a weak case is worth settling and a strong one is worth the fee.
- Non-practising entities are scripted actors with an internal chest. They buy patents, send demands and sue.
- Randomness comes from a stateless `IpDice(worldSeed)` addressed by channel and keys, so a draw does not depend
  on how many other draws happened before it and replays agree after any change of order elsewhere.
- Rules read the rest of the game through the `IpContext` interface with neutral defaults. The module imports
  no companies, markets or research code beyond ids and content definitions.
- The module never posts to the ledger. Services record `IpCharge`s and `IpEvent`s in an `IpOutput`; the engine
  returns them and the host turns charges into settlement intents (`IpCharge.toIntent()`). Cash checks inside a
  tick go through a `Wallet` that remembers what has already been promised, so one pool of money is not spent
  twice.
- Commands are a sealed `IpCommand` hierarchy with a binary codec and a describer. The engine applies them
  through `execute`, which returns the same step result as the daily tick.
- Saved state has its own codec with a version byte and a canonical hash, as other modules do.

## Consequences

- Fees, royalties and damages are exact in fixed point, and every figure on the screens can be traced to a rule.
- Adding a country or a legal regime is data: a regime kind and its parameters. No rule branches on a country id.
- The host carries one integration seam (the context, the tick call, the charge settlement) and can switch the
  module off by not calling it.
- Because charges are returned, a company short of cash simply has its deferrable charges deferred by the
  settlement engine. The module marks which charges may be deferred and which are statutory.
- Parties that are not companies (NPEs) are mapped to the households ledger owner for settlement. Their internal
  chest is tracked inside the module and is not part of the money supply checks.
- Dispute outcomes depend on the order commands are applied within a tick, as for all commands.

## Alternatives not taken

- Posting to the ledger from inside the services. Rejected: it would make the module depend on finance state and
  make unit tests need a ledger.
- Stochastic outcomes for every lawsuit. Rejected: players could not reason about cases. Only the grey band rolls.
- Modelling reverse engineering as a research project. Rejected for now; it is a hazard on the secret.
