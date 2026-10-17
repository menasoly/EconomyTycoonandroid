# Module: ip (patents, trade secrets and licensing)

Package `app.many.economytycoon.ip`. Depends on core, content, finance (charge types only), production
(`ProductionModifiers`) and advisor (`Insight`).

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
