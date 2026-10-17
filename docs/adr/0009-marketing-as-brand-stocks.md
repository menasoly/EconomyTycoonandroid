# ADR-0009: Marketing as brand stocks driven by paid campaigns

Status: accepted

## Context

Companies can already set a marketing department budget and carry a coarse brand value, but nothing connects money
spent on advertising to what households buy. Players need to choose where to advertise (press, radio, television,
online, sponsorship, in-store, loyalty), for which good and place, for how long and for whom, and to see whether it
paid off. Computer-controlled competitors need the same levers, and none of it may break replay: the same commands
must give the same hash on every device.

Two simple designs were rejected. A flat "marketing level" per company ignores that a campaign for beer in one region
does nothing for bread in another. Per-household simulation of exposure is far too costly and gives nothing the
market model can use.

## Decision

Marketing keeps two fixed-point stocks per company, good and region: awareness (how many know the brand) and loyalty
(how many of those prefer it). Both are ratios in parts per million.

- A campaign buys reach. Reach follows a saturating curve of daily spend per thousand people, scaled by a cost index
  that rises with local income. Each channel has its own ceiling, half-spend point, carry-over, wear-out speed,
  minimum and maximum spend, deposit and cancellation fee.
- Exposure is smoothed over days (carry-over), so a campaign keeps working for a while after it stops. Creative
  freshness wears out and can be refreshed or replaced after an A/B test.
- Awareness rises towards full with exposure and decays towards zero without it. Loyalty grows only from aware
  people, is pulled by product quality and is capped by awareness. Brand damage (recall, scandal and the like) adds
  a taint that blunts exposure and heals over time.
- Share of voice against rivals scales a campaign's effect, so a louder competitor reduces ours.
- The effect on demand is exposed only as pure functions (`DemandEffects`): an appeal multiplier for a brand, a
  category shift for the whole good, relative shares among sellers and pricing power. The market code decides how
  to apply them; marketing never edits markets.
- Money moves only as settlement intents (deposit, daily spend, creative, test, refund) with deterministic keys, so
  the finance layer posts each payment exactly once. Unpaid days suspend a campaign.
- Price promotions are separate objects with a margin check, and A/B tests resolve with a seeded draw, so outcomes
  depend only on the world seed and the ids involved.
- Analysis is pure: modelled lift, channel comparison, ROI reports, a greedy budget optimiser with constraints, and
  advisor insights in the existing `Insight` shape.
- Computer-controlled competitors use `MarketingAi`, a pure function from views to proposals; the caller turns
  proposals into the same commands a player would issue.

The state has its own codec (with a version) and canonical hash like the other modules, and every iteration goes
through sorted maps.

## Consequences

- Advertising has a measurable, explainable effect, and a campaign that is stopped fades instead of vanishing.
- The balance lives in `MarketingParams` and the channel table, so tuning needs no code change elsewhere.
- Integration needs several shared-file edits (commands, executor, snapshot section, tick hook, demand coupling);
  they are wired in the shared files.
- The older company brand value and the marketing department budget overlap with this model. Until the integration
  step reconciles them, only one of them may feed demand, or the effect is counted twice.
- Adding the section changes the snapshot hash of every save, so golden hashes must be recomputed.
