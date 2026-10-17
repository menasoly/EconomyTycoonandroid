package app.many.economytycoon.simulation

import app.many.economytycoon.companies.Department
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Price
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.PaymentLine
import app.many.economytycoon.finance.SettlementIntent
import app.many.economytycoon.finance.SettlementResult
import app.many.economytycoon.finance.SettlementStatus
import app.many.economytycoon.marketing.AudienceSource
import app.many.economytycoon.marketing.BrandKey
import app.many.economytycoon.marketing.Campaign
import app.many.economytycoon.marketing.CampaignId
import app.many.economytycoon.marketing.CampaignRequest
import app.many.economytycoon.marketing.MarketingEvent
import app.many.economytycoon.marketing.Ppm
import app.many.economytycoon.marketing.PromotionId
import app.many.economytycoon.marketing.PromotionRequest
import app.many.economytycoon.marketing.QualitySource
import app.many.economytycoon.marketing.WorldAudienceSource

/**
 * Connects the marketing module to the economy: validates and pays for the player's and the computers' marketing
 * commands, runs the module once a day, and hands the market the few numbers it reads (category demand, brand
 * premium, shelf price). Marketing money is only ever moved through settlement intents.
 */
class MarketingRunner(private val e: Economy) {
    val audiences: AudienceSource = WorldAudienceSource(e.world)
    private val quality: QualitySource = QualitySource.NEUTRAL

    private var dayIntents: List<SettlementIntent> = emptyList()
    private var daySpend: Map<CompanyId, Money> = emptyMap()

    /** True while nothing has ever been booked; the market and the saves then behave exactly as before marketing existed. */
    val idle: Boolean get() = e.marketing.state.isPristine()

    private fun cash(company: CompanyId): Money = e.finance.ledger.cash(LedgerOwner.company(company))

    private fun fail(rule: String, message: String): Outcome<CommandResult> = Outcome.reject(rule, message)

    private fun ownCampaign(company: CompanyId, id: CampaignId): Outcome<Campaign> {
        val c = e.marketing.state.campaign(id) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown campaign")
        if (c.company != company) return Outcome.reject(Rules.NOT_OWNER, "that campaign belongs to another company")
        return Outcome.ok(c)
    }

    // ---- paying ----------------------------------------------------------------------------------

    /**
     * Settles the intents of a command at once. Keys of charges that carry no amount of their own are made unique by the
     * campaign's running spend, so two top-ups of the same size on one day are both charged.
     */
    private fun pay(intents: List<SettlementIntent>, campaign: Campaign, tick: Tick): Boolean {
        if (intents.isEmpty()) return true
        val keyed = intents.map { i ->
            if (CHARGE_PREFIXES.any { i.key.startsWith(it) }) i.copy(key = i.key + ":" + campaign.spentTotal.raw) else i
        }
        val results = e.finance.settlement.settle(tick, keyed)
        return keyed.all { k -> results.any { it.intent.key == k.key && (it.status == SettlementStatus.PAID || it.status == SettlementStatus.DEFERRED) } }
    }

    private fun paid(booking: app.many.economytycoon.marketing.Booking, message: String, tick: Tick): Outcome<CommandResult> =
        if (pay(booking.intents, booking.campaign, tick)) Outcome.ok(CommandResult(booking.campaign.id.value, message))
        else fail(Rules.INSUFFICIENT_FUNDS, "the payment could not be made")

    // ---- commands --------------------------------------------------------------------------------

    fun create(company: CompanyId, cmd: Command.CreateCampaign, tick: Tick): Outcome<CommandResult> {
        if (e.content.goodOrNull(cmd.good) == null) return fail(Rules.UNKNOWN_ENTITY, "unknown product")
        if (e.world.regions().none { it.id == cmd.region }) return fail(Rules.UNKNOWN_ENTITY, "unknown region")
        val request = CampaignRequest(company, cmd.good, cmd.region, cmd.city, cmd.channel, cmd.segment, cmd.dailyBudget, cmd.days, cmd.tier, cmd.startTick)
        val booking = when (val r = e.marketing.campaigns.create(request, tick, cash(company), audiences)) {
            is Outcome.Rejected -> return Outcome.Rejected(r.rejection)
            is Outcome.Ok -> r.value
        }
        if (!pay(booking.intents, booking.campaign, tick)) {
            e.marketing.campaigns.voidBooking(booking.campaign.id)
            return fail(Rules.INSUFFICIENT_FUNDS, "the booking could not be paid for")
        }
        return Outcome.ok(CommandResult(booking.campaign.id.value, "booked"))
    }

    fun pause(company: CompanyId, id: CampaignId): Outcome<CommandResult> =
        ownCampaign(company, id).flatMap { e.marketing.campaigns.pause(id) }.map { CommandResult(id.value, "paused") }

    fun resume(company: CompanyId, id: CampaignId, tick: Tick): Outcome<CommandResult> =
        ownCampaign(company, id).flatMap { e.marketing.campaigns.resume(id, tick) }.map { CommandResult(id.value, "resumed") }

    fun extend(company: CompanyId, id: CampaignId, extraDays: Int, tick: Tick): Outcome<CommandResult> =
        ownCampaign(company, id).flatMap { e.marketing.campaigns.extend(id, extraDays, tick, cash(company)) }.flatMap { paid(it, "extended", tick) }

    fun setBudget(company: CompanyId, id: CampaignId, dailyBudget: Money, tick: Tick): Outcome<CommandResult> =
        ownCampaign(company, id).flatMap { e.marketing.campaigns.setBudget(id, dailyBudget, tick, cash(company)) }.flatMap { paid(it, "budget changed", tick) }

    fun refresh(company: CompanyId, id: CampaignId, tier: app.many.economytycoon.marketing.CreativeTier, tick: Tick): Outcome<CommandResult> =
        ownCampaign(company, id).flatMap { e.marketing.campaigns.refreshCreative(id, tier, tick, cash(company)) }.flatMap { paid(it, "new creative", tick) }

    fun cancel(company: CompanyId, id: CampaignId, tick: Tick): Outcome<CommandResult> =
        ownCampaign(company, id).flatMap { e.marketing.campaigns.cancel(id, tick) }.map { r ->
            // A refund is money coming back; if it cannot be posted the advertiser simply forgoes it, the campaign stays cancelled.
            pay(r.intents, r.campaign, tick)
            CommandResult(id.value, "cancelled, ${r.refund} refunded")
        }

    fun startTest(company: CompanyId, cmd: Command.StartAbTest, tick: Tick): Outcome<CommandResult> =
        ownCampaign(company, cmd.campaign).flatMap { c ->
            e.marketing.experiments.start(cmd.campaign, cmd.challenger, cmd.days, tick, cash(company)).flatMap { launch ->
                if (pay(launch.intents, c, tick)) Outcome.ok(CommandResult(launch.test.id.value, "test started"))
                else fail(Rules.INSUFFICIENT_FUNDS, "the test could not be paid for")
            }
        }

    fun launchPromotion(company: CompanyId, cmd: Command.LaunchPromotion, tick: Tick): Outcome<CommandResult> {
        if (e.content.goodOrNull(cmd.good) == null) return fail(Rules.UNKNOWN_ENTITY, "unknown product")
        if (e.world.regions().none { it.id == cmd.region }) return fail(Rules.UNKNOWN_ENTITY, "unknown region")
        val request = PromotionRequest(company, cmd.good, cmd.region, null, cmd.days, cmd.discountPpm, cmd.listPrice, cmd.unitCost, cmd.elasticityPpm, cmd.clearance)
        return e.marketing.promotions.launch(request, tick).map { CommandResult(it.promotion.id.value, "promotion launched") }
    }

    fun cancelPromotion(company: CompanyId, id: PromotionId, tick: Tick): Outcome<CommandResult> {
        val p = e.marketing.state.promotion(id) ?: return fail(Rules.UNKNOWN_ENTITY, "unknown promotion")
        if (p.company != company) return fail(Rules.NOT_OWNER, "that promotion belongs to another company")
        return e.marketing.promotions.cancel(id, tick).map { CommandResult(id.value, "promotion cancelled") }
    }

    /** A company that has ceased to exist ends its campaigns (the deposit is kept by the agency) and its promotions. */
    fun forget(company: CompanyId, tick: Tick) {
        e.marketing.campaigns.endAllOf(company, tick)
        for (p in e.marketing.state.promotions()) if (p.company == company) e.marketing.promotions.cancel(p.id, tick)
    }

    // ---- the day ---------------------------------------------------------------------------------

    /**
     * Public blows to a company's name, taken from what the game already reports: a walk-out costs the brand a little,
     * a pollution fine more, and one that shuts the plant more again. Only companies with a brand somewhere are touched.
     */
    fun recordDamage(
        tick: Tick,
        unionEvents: List<app.many.economytycoon.unions.UnionEvent>,
        regulationEvents: List<app.many.economytycoon.regulation.RegulationEvent>,
    ): List<MarketingEvent> {
        if (e.marketing.state.stockMap.isEmpty()) return emptyList()
        val out = ArrayList<MarketingEvent>()
        fun hit(company: CompanyId, kind: app.many.economytycoon.marketing.DamageKind, severityPpm: Long) {
            val r = e.marketing.damage.record(company, null, null, kind, severityPpm, tick)
            if (r is Outcome.Ok) out += r.value.second
        }
        for (ev in unionEvents) {
            if (ev is app.many.economytycoon.unions.UnionEvent.Struck) e.unions.get(ev.union)?.let { hit(it.company, app.many.economytycoon.marketing.DamageKind.LABOUR_DISPUTE, STRIKE_SEVERITY_PPM) }
        }
        for (ev in regulationEvents) {
            if (ev is app.many.economytycoon.regulation.RegulationEvent.Fined) {
                hit(ev.fine.company, app.many.economytycoon.marketing.DamageKind.SCANDAL, if (ev.fine.closed) CLOSURE_SEVERITY_PPM else FINE_SEVERITY_PPM)
            }
        }
        return out
    }

    /** Runs marketing forward one day. The payments wait for the settlement phase; the events are returned now. */
    fun advance(tick: Tick): List<MarketingEvent> {
        if (idle) { dayIntents = emptyList(); daySpend = emptyMap(); return emptyList() }
        val report = e.marketing.engine.advance(tick, audiences, quality)
        dayIntents = report.intents
        daySpend = report.spendByCompany
        return report.events
    }

    /**
     * The day's campaign bills for the settlement phase. The marketing department budget is the company's marketing
     * envelope: the bills are paid out of it first, so a company that books campaigns is not charged twice for them,
     * and only what is left of the budget is paid as general marketing. [departmentIntents] are rewritten accordingly.
     */
    fun settlementIntents(departmentIntents: List<SettlementIntent>): List<SettlementIntent> {
        if (dayIntents.isEmpty()) return departmentIntents
        val prefix = "dept:" + Department.MARKETING.name.lowercase() + ":"
        val netted = ArrayList<SettlementIntent>(departmentIntents.size)
        for (i in departmentIntents) {
            val spent = if (i.key.startsWith(prefix)) spendOf(i.payer) else Money.ZERO
            if (spent.raw <= 0L) { netted += i; continue }
            val left = i.total - spent
            if (left.raw <= 0L) continue
            netted += i.copy(lines = listOf(PaymentLine(i.lines.first().payerDebit, i.lines.first().payeeCredit, left)))
        }
        return netted + dayIntents
    }

    private fun spendOf(owner: LedgerOwner): Money =
        daySpend.entries.firstOrNull { LedgerOwner.company(it.key) == owner }?.value ?: Money.ZERO

    /** Tells marketing how the day's payments went; returns the events that caused. */
    fun reportPayments(tick: Tick, results: List<SettlementResult>): List<MarketingEvent> {
        val mine = results.filter { it.intent.key.startsWith(app.many.economytycoon.marketing.MarketingLedger.PREFIX + ":") }
        dayIntents = emptyList(); daySpend = emptyMap()
        return if (mine.isEmpty()) emptyList() else e.marketing.engine.reportPayments(tick, mine)
    }

    // ---- what the market reads ---------------------------------------------------------------------

    /** Scales a region's demand for a good by the pull of advertising on the whole category. */
    fun scaleDemand(shiftPpm: Long, good: GoodId, region: RegionId): Long {
        if (e.marketing.state.stockMap.isEmpty()) return shiftPpm
        return mulDiv(shiftPpm, e.marketing.demand.categoryShift(good, region), Ppm.ONE)
    }

    /**
     * The premium a company can ask for one product in one region: the better of its company-wide brand and its
     * advertised brand for that product. They describe the same goodwill, so they are never added together.
     */
    fun premium(company: CompanyId, good: GoodId, region: RegionId, companyBrandPpm: Long): Long {
        if (e.marketing.state.stockMap.isEmpty()) return companyBrandPpm
        return maxOf(companyBrandPpm, e.marketing.demand.pricingPower(BrandKey(company, good, region)))
    }

    /** The price on the shelf: the list price less any promotion running today. */
    fun shelfPrice(company: CompanyId, good: GoodId, region: RegionId, tick: Tick, price: Price): Price {
        if (e.marketing.state.promotions().isEmpty()) return price
        val cut = e.marketing.promotions.priceOn(company, good, region, tick, price.perUnit)
        return Price(Money.max(cut, Money(1L)))
    }

    private companion object {
        val CHARGE_PREFIXES = listOf("mkt:dep:", "mkt:fx:", "mkt:ab:")
        const val STRIKE_SEVERITY_PPM = 200_000L
        const val FINE_SEVERITY_PPM = 250_000L
        const val CLOSURE_SEVERITY_PPM = 500_000L
    }
}
