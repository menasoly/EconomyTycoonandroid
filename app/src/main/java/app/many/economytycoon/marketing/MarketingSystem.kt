package app.many.economytycoon.marketing

import app.many.economytycoon.core.StateHash

/**
 * The whole marketing module wired together: one state and the services that change it. The simulation owns one of
 * these; commands call the services and the tick pipeline calls [engine]. Other modules read through [demand],
 * [readModel] and the state.
 */
class MarketingSystem(
    val params: MarketingParams = MarketingParams(),
    val ledger: MarketingLedger = MarketingLedger(),
    worldSeed: Long = 0L,
    val state: MarketingState = MarketingState(),
) {
    val campaigns = CampaignService(state, params, ledger)
    val engine = MarketingEngine(campaigns, worldSeed)
    val promotions = PromotionService(state, params)
    val experiments = ExperimentService(campaigns)
    val damage = DamageService(state)
    val demand = DemandEffects(state, params, promotions)
    val ai = MarketingAi(params, worldSeed = worldSeed)
    val optimiser = BudgetOptimiser(params)
    val advisor = MarketingAdvisor()

    fun readModel(names: MarketingNames = PlainNames, roiOf: (Campaign) -> RoiReport? = { null }) =
        MarketingReadModel(state, campaigns, names, roiOf)

    fun stateHash(): StateHash = state.stateHash()

    /** Problems found in the state; empty when consistent. */
    fun invariants(): List<String> = MarketingInvariants.check(state)
}
