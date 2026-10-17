# Module: marketing

Advertising and promotion. Package `app.many.economytycoon.marketing`; presenters in `ui/present`; screen in
`ui/compose/screens/MarketingScreen.kt`. Decision record: ADR-0009. Depends on core, finance (settlement intents),
world (population and income view) and advisor (insight shape). Nothing depends on it yet.

## Concepts

| Concept | Where | Notes |
|---|---|---|
| Channel, TargetSegment | Channel.kt | Seven channels with a spec each: reach ceiling, half-spend, carry-over, wear, spend limits, deposit, cancel fee |
| Audience | Audience.kt | People, income index and age bands of a region or city; `WorldAudienceSource` reads the world, `FixedAudienceSource` serves tests |
| Reach | ReachModel.kt | Saturating curve of spend per thousand people over a cost index |
| Brand stock | BrandStock.kt | Awareness and loyalty per `BrandKey(company, good, region)`; `StockDynamics.step` and a closed-form `equilibrium` |
| Campaign | Campaign.kt, CampaignService.kt | Create, pause, resume, extend, change budget, refresh creative, cancel with refund rules |
| Engine | MarketingEngine.kt | One call per day: spend, exposure, stock update, events, intents |
| Promotion | Promotion.kt, PromotionService.kt | Price cut with a margin floor; clearance may go below cost |
| A/B test | AbTest.kt, ExperimentService.kt | Creative challenger; seeded, needs a minimum number of responses |
| Brand damage | BrandDamage.kt, DamageService.kt | Taint that blunts exposure and recovers |
| Demand effects | DemandEffects.kt | Pure multipliers for the market code |
| Competition | Competition.kt | Share of voice and counter-campaign proposals |
| Analysis | ModelledLift.kt, RoiAnalysis.kt, BudgetOptimiser.kt | Lift, ROI and payback, greedy allocation with constraints |
| Advice | MarketingAdvisor.kt | Fourteen rules, codes `mkt-*`, topic MARKET |
| Read model | MarketingReadModel.kt | Immutable views for presenters |
| AI | MarketingAi.kt | Pure proposals for computer-controlled companies |
| State | MarketingState.kt, MarketingCodec.kt, MarketingInvariants.kt | Owned maps, codec version 1, canonical hash, invariant checks |
| System | MarketingSystem.kt | Wires the services around one state |

## Daily flow

1. `engine.advance(tick, audiences, quality)` starts scheduled campaigns, charges the day, updates exposure and the
   brand stocks, wears creatives, resolves tests and ends finished campaigns.
2. The caller posts `DayReport.intents` through the settlement engine and passes the results to
   `engine.reportPayments`, which counts missed days and may suspend a campaign.
3. Markets ask `DemandEffects` for multipliers while building demand.

## Invariants

Checked by `MarketingInvariants` and used in the tests: ratios stay within 0..1,000,000 ppm, ids are below their
allocator counters, a live campaign has a positive budget and days, a test belongs to an existing campaign,
and no two intents share a key.

## Determinism

No floating point in state changes, all iteration over `TreeMap` or `EnumMap`, random draws only through
`RandomProvider.deriveSeed(worldSeed, name, id)`. Two systems with the same seed and commands have equal hashes.

## Integration

- Commands: CreateCampaign, PauseCampaign, ResumeCampaign, ExtendCampaign, SetCampaignBudget, RefreshCreative,
  CancelCampaign, StartAbTest, LaunchPromotion, CancelPromotion (codec tags 81 to 90, activity area Marketing).
- `simulation/MarketingRunner` validates ownership, settles command-time payments, advances the engine in the demand
  phase, nets campaign bills against the marketing department budget and reports payments after settlement.
- `simulation/AiMarketing` turns `MarketingAi` proposals into the same commands a player sends; `MarketingPositions`
  builds its views from sell rules.
- Snapshot section `marketing` and the economy hash are written only when the state is not pristine.
- Session: `MarketingReader`, `SessionMarketingHost`, route `Route.Marketing`, news, alerts and advisor insights
  (`mkt-` codes), glossary, tips and help.
- Overlap with `Brand` and the department budget: see ADR 0009. Premium is the larger of the two, the budget is an
  envelope for campaign bills.
- Not wired: manifest and catalog entries (the files do not exist in this tree), i18n keys, the agenda hook.
