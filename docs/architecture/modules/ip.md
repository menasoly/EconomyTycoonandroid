# Module: ip (patents, trade secrets and licensing)

Package `app.many.economytycoon.ip`. Depends on core, content, finance (charge types only), production
(`ProductionModifiers`) and advisor (`Insight`). Only the simulation, session and screens depend on it (see Integration).

## Responsibilities

- File, examine, grant, renew, lapse and revoke patents, country by country.
- Keep trade secrets and let them leak, be worked out or be found by others.
- Choose between the two for a technology (`IpChoice`) and value what a patent protects (`PatentValuer`).
- License, pool, cross-license, audit and enforce licences, including FRAND and compulsory ones.
- Detect infringement, send demands, litigate, settle, appeal, challenge validity.
- Let companies design around a patent, buy and sell patents, and meet scripted patent holders.
- Apply the R&D tax credit and patent box, and turn rights into production effects.
- Plan for computer-controlled companies and advise the player.

## Layout

| Area | Files |
|---|---|
| Ids and dice | IpIds, IpDice |
| Records | Patent, TradeSecret, License, Pool, Dispute, Npe, Programmes, TechField, LegalRegime |
| Tuning | IpParams |
| State | IpState, IpCodec, IpEvents |
| Rules | PatentRules, Royalty, Frand, Litigation, IpChoice |
| Services | PatentOffice, TradeSecretService, LicenseBook, PoolService, CrossLicense, DisputeService, DesignAroundService, PatentMarket, NpeService, CompulsoryService, Standards, IpTax, PatentValuer |
| Effects | IpEffects, IpProductionModifiers |
| Orchestration | IpEngine, IpPlanner, IpContext |
| Commands | IpCommand, IpCommandCodec |
| Reading | IpViews, IpAdvisor, IpNews |
| Screens | ui/present/IpPresenter, IpDealsPresenter, IpDisputePresenter, ui/compose/screens/IpScreen |

## Daily order

office (examination, fees, expiry), secrets (hazard draws), licence book (royalties, renewals, offers),
disputes (stages, detection), design-around, compulsory requests; on month start: listing expiry, NPE moves,
fire sales for distressed companies, the planner; every quarter pruning; then tax. Royalties read usage
reports, so the host calls the engine after sales are recorded and before financial settlement.

## Invariants

- Every id handed out is unique and increases; `IpIds.restore` rejects counters that would reuse one.
- A patent has at least one filing; a licence names at least one patent; a pool member owns what it lists.
- A secret's status only moves forward; once not live it never returns.
- A charge is always positive and never from a party to itself.
- State hash is the same after encode and decode, and a decoded state continues identically.
- Iteration is over sorted maps; no hash order reaches a result.

## Tests

Unit tests per rule and service, edge cases at every limit in `IpParams`, codec round trips with hash checks,
determinism tests running two engines from one seed, and scenario tests for a full dispute, a company closing,
distress sales and a yearly tax credit. Shared fixtures are in `IpTestSupport`.

## Integration

- Commands: `EstablishIp` (system only, codec tag 120) opens the patent office and seeds the scripted holders; `Command.Ip(IpCommand)` (codec tag 121, body written by `IpCommandCodec`) carries every decision of a player or computer company. `simulation/IpRunner` runs both. A refused command changes nothing; a command's own payments are settled at once.
- Switch: nothing exists until `EstablishIp`, which `NewGame.start` issues and the command log replays. Without it there is no hash contribution, no snapshot sections `ip` and `ipsales`, no sales recording and no random draw, so older games play exactly as before.
- State: `Economy.ip` is rebuilt around the decoded `IpState` on load (`restoreIp`). `IpSalesLog` keeps 400 days of sales by company, technology, country and industry family; it is saved beside the module (`ipsales`) and hashed, so a restored game prices royalties exactly as the saved one would have.
- Context: `EconomyIpContext` answers the module from the ledger (cash, margins, research spend), the research engine (known technologies), the workforce and production (who knows a secret, where a technology is worked) and the sales log. `Fulfillment` records each trade under the technologies behind the good. There is no public-health emergency in the economy, so compulsory licences on that ground are never granted.
- Tick: the module's day runs at the end of fulfilment, after the day's sales are on record, and its charges join the settlement batch after every other group (royalties and damages are deferrable, fees are not). Companies that close are wound up in `Economy.retire`.
- Production: `IpModifiers` wraps the other modifiers. For a recipe with a `requiredTech` it adds the cost and quality effects of the company's rights and stops the line while a court order is in force. Production clamps total input savings to 0..90%, so a penalty only cancels savings that exist.
- Computer competitors: the module's own monthly planner files, licenses, sues and settles for them; `AiIp` adds an immediate protection decision when a computer company completes research.
- Session and UI: `IpSessionAdapter` (`GameSession.ipView`, `ipInsights`), news from `IpNews`, alerts from the last three days of events, advisor insights, `Route.Ip`, a More-menu entry once the office is open, a "Protect this" link on researched technologies, glossary, tips and a help topic.
