package app.many.economytycoon.carbon

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.LedgerOwner

/**
 * The carbon package's one writer. It keeps the emissions inventories from the facilities' daily activity, runs each
 * country's carbon policy (auctions, free allocation, the reserve, the yearly compliance cycle, taxes and levies), looks
 * after offsets and abatement projects and answers company commands. It reads the rest of the game through
 * [CarbonEnvironment] and never posts money: every payment comes back as ledger intents for the runner.
 *
 * The work is split over several files as extension functions on this class; they share the context of a call, [Ctx].
 */
class CarbonService(
    val state: CarbonState,
    val env: CarbonEnvironment,
    val calculator: EmissionCalculator,
    val random: RandomProvider,
    val params: CarbonParams = CarbonParams(),
    val catalog: AbatementCatalog = AbatementCatalog(),
    val greenHook: GreenSubsidyHook = GreenSubsidyHook.None,
) {
    /** What one call is doing: when it is, the events it raises and the money it moves. */
    internal inner class Ctx(val tick: Tick) {
        val year: Int = CarbonCalendar.yearOf(tick)
        val events = ArrayList<CarbonEvent>()
        val settle = SettlementBuilder(tick) { state.counters.settlement() }
        val cash = CashBook(env)

        fun emit(e: CarbonEvent) { events += e }
        fun result() = CarbonStepResult(events.toList(), settle.build())
    }

    // ---- reading -------------------------------------------------------------------------------

    /** The price of a tonne in [country] in [year]: the tax, the levy rate or the market price of a permit; zero where carbon is not priced. */
    fun carbonPrice(country: CountryId, year: Int): Money {
        val regime = state.regime(country) ?: return Money.ZERO
        val step = regime.stepFor(year) ?: return Money.ZERO
        val fallback = referenceFallback(step)
        val permit = state.market(country)?.referencePrice(fallback) ?: fallback
        return regime.priceIn(year, permit)
    }

    internal fun referenceFallback(step: RegimeStep): Money {
        val start = Money.max(step.floor, CarbonDefaults.STARTING_PRICE)
        return if (step.ceiling.isPositive) Money.min(start, step.ceiling) else start
    }

    /** The countries whose rules list [company]'s accounts, with the company's obligation outstanding, if any. */
    fun outstanding(company: CompanyId, country: CountryId): Tonnes =
        state.compliance.account(company, country)?.pending?.outstanding ?: Tonnes.ZERO

    /** Permits the company holds that can be handed in for the pending year of [country], or nothing if none is pending. */
    fun usablePermits(company: CompanyId, country: CountryId): Tonnes {
        val pending = state.compliance.account(company, country)?.pending ?: return Tonnes.ZERO
        val regime = state.regime(country) ?: return Tonnes.ZERO
        val market = state.market(country) ?: return Tonnes.ZERO
        return market.holdings.held(company, firstValidVintage(regime, pending.year), pending.year)
    }

    internal fun firstValidVintage(regime: CarbonRegime, forYear: Int): Int =
        if (regime.bankingAllowed) forYear - regime.vintageLifeYears + 1 else forYear

    // ---- the daily step ---------------------------------------------------------------------------

    /**
     * Runs one day. [activities] are the day's facility reports, as adapted by [ProductionActivities]. Returns the events
     * for the news and the ledger intents for the runner to settle. A tick already processed is ignored.
     */
    fun advance(tick: Tick, activities: List<FacilityActivity>): CarbonStepResult = runDay(Ctx(tick), activities)

    // ---- policy --------------------------------------------------------------------------------

    /**
     * Puts [regime] in force for its country. Installing a cap-and-trade regime opens the permit market and, if its
     * first step has already begun, issues the rest of this year's supply in proportion to the days left.
     */
    fun installRegime(regime: CarbonRegime, tick: Tick): Outcome<List<CarbonEvent>> {
        val valid = regime.validate()
        if (valid is Outcome.Rejected) return valid
        val ctx = Ctx(tick)
        val before = state.regime(regime.country)?.kind ?: RegimeKind.NONE
        state.installRegime(regime)
        if (regime.tradesPermits) state.marketFor(regime.country)
        if (before != regime.kind) ctx.emit(CarbonEvent.RegimeChanged(tick, regime.country, before, regime.kind))
        ensureYears(ctx)
        return Outcome.ok(ctx.events.toList())
    }

    fun setBorderPolicy(policy: BorderPolicy): Outcome<Unit> {
        if (policy.phaseInYears < 1) return Outcome.reject(Rules.INVALID_ARGUMENT, "the phase-in lasts at least a year")
        if (policy.exportRebatePpm !in 0L..1_000_000L) return Outcome.reject(Rules.INVALID_ARGUMENT, "the rebate is a share between 0 and 100%")
        if (policy.minIntensityGrams < 0L) return Outcome.reject(Rules.INVALID_ARGUMENT, "the intensity threshold cannot be negative")
        state.border.set(policy)
        return Outcome.ok(Unit)
    }

    fun clearBorderPolicy(country: CountryId): Outcome<Unit> {
        if (state.border.policy(country) == null) return Outcome.reject(Rules.UNKNOWN_ENTITY, "that country has no border adjustment")
        state.border.remove(country)
        return Outcome.ok(Unit)
    }

    // ---- commands ------------------------------------------------------------------------------

    /** Applies a company's command. The effect carries the money to move; nothing is changed if the command is rejected. */
    fun execute(command: CarbonCommand, tick: Tick): Outcome<CarbonEffect> {
        if (!env.isActive(command.company)) return Outcome.reject(Rules.UNKNOWN_ENTITY, "that company is not trading")
        val ctx = Ctx(tick)
        return when (command) {
            is CarbonCommand.BidAtAuction -> bid(ctx, command)
            is CarbonCommand.CancelBid -> cancelBid(command)
            is CarbonCommand.PlaceOrder -> placeOrder(ctx, command)
            is CarbonCommand.CancelOrder -> cancelOrder(command)
            is CarbonCommand.Surrender -> surrender(ctx, command)
            is CarbonCommand.BuyOffsets -> buyOffsets(ctx, command)
            is CarbonCommand.RetireOffsets -> retireOffsets(ctx, command)
            is CarbonCommand.FundOffsetProject -> fundOffsetProject(ctx, command)
            is CarbonCommand.StartAbatement -> startAbatement(ctx, command)
            is CarbonCommand.CancelAbatement -> cancelAbatement(ctx, command)
            is CarbonCommand.SetPolicy -> setPolicy(command)
        }
    }

    /** Assesses the border adjustment on [flows] and returns the charges and rebates as intents. */
    fun assessTrade(tick: Tick, flows: List<TradeFlow>, footprints: ProductFootprints): CarbonStepResult = assessBorder(Ctx(tick), flows, footprints)

    private fun setPolicy(c: CarbonCommand.SetPolicy): Outcome<CarbonEffect> {
        val regime = state.regime(c.country)
        if (regime == null || !regime.isRegulated) return Outcome.reject(Rules.WRONG_STATE, "that country has no carbon policy")
        state.compliance.open(c.company, c.country).policy = CompliancePolicy(c.autoOffsets, c.autoBorrow)
        return Outcome.ok(CarbonEffect.of("Compliance preferences saved"))
    }

    internal fun company(id: CompanyId): LedgerOwner = SettlementBuilder.company(id)
    internal fun government(country: CountryId): LedgerOwner = SettlementBuilder.government(country)
    internal val contractors: LedgerOwner get() = SettlementBuilder.contractors
}
