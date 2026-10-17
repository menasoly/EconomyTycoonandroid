package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.OwnerKind
import app.many.economytycoon.finance.SettlementIntent
import app.many.economytycoon.macro.GdpKind
import app.many.economytycoon.talent.CohortFact
import app.many.economytycoon.talent.CompanyFact
import app.many.economytycoon.talent.DefaultTaxonomy
import app.many.economytycoon.talent.EducationDemand
import app.many.economytycoon.talent.InstitutionKind
import app.many.economytycoon.talent.Ppm
import app.many.economytycoon.talent.TalentCommand
import app.many.economytycoon.talent.TalentEvent
import app.many.economytycoon.talent.TalentInputs
import app.many.economytycoon.talent.TalentResult
import app.many.economytycoon.talent.WorkforceEffect
import app.many.economytycoon.talent.WorldTalentView
import app.many.economytycoon.workforce.MarketKey

/**
 * Connects skills, education, training, migration and poaching to the economy. Games begin without the module; once
 * [establish] has run, every region has public schools, colleges and a university, the people of every company carry a
 * skill picture, and the module takes its turn each day. Until then nothing here does anything, nothing is saved or
 * hashed and no random number is drawn, so a game without it plays exactly as it did before.
 *
 * The module reaches back into the economy in a few narrow ways: it asks the workforce to add, move or remove people and
 * to raise a wage ([apply]), it tells it how many of a team are away on a course, it supplies the skill term of
 * productivity, and the qualified share of a region's people sets how its job seekers split over the skill tiers.
 * Money flows only through settlement intents: what a command costs is paid at once, and what the day's turn decides
 * joins the day's settlement batch. The treasury of a country pays for its public schools; a short treasury is topped up
 * the way it is for benefits.
 */
class TalentRunner(private val e: Economy) {
    private var pending: List<SettlementIntent> = emptyList()
    private val recent = ArrayDeque<TalentEvent>()

    /** True once the module has been switched on. */
    val active: Boolean get() = e.talentActive

    /** What the module reads of the world. */
    val world: WorldTalentView by lazy { WorldTalentView(e.world) }

    /** The module's events of the last few days, oldest first; alerts and the news read them. */
    fun recentEvents(): List<TalentEvent> = recent.toList()

    private fun fail(rule: String, message: String): Outcome<CommandResult> = Outcome.reject(rule, message)

    // ---- setting up ----------------------------------------------------------------------------

    /**
     * Switches the module on: every region gets a public school, vocational college and university sized for its young
     * people, and the qualified people it already has are put on record so its education level starts where the world
     * says it is.
     */
    fun establish(tick: Tick): Outcome<CommandResult> {
        if (active) return fail(Rules.WRONG_STATE, "skills and education are already set up")
        val regions = e.world.regions().sortedBy { it.id }
        if (regions.isEmpty()) return fail(Rules.WRONG_STATE, "there are no regions to educate")
        val tax = e.talent.taxonomy
        val params = e.talent.params.education
        var made = 0
        for (region in regions) {
            val youth = world.youthCohort(region.id)
            val wage = world.averageWage(region.id)
            e.talent.education.seedQualifications(region.id, world.workingAge(region.id), world.educationPpm(region.id))
            if (youth <= 0L) continue
            for (kind in InstitutionKind.values()) {
                val seats = when (kind) {
                    InstitutionKind.SCHOOL -> youth * 5L
                    InstitutionKind.VOCATIONAL -> youth / 2L + 20L
                    InstitutionKind.UNIVERSITY -> youth + 20L
                }
                val fields = if (kind == InstitutionKind.SCHOOL) listOf(DefaultTaxonomy.GENERAL) else tax.skillsOf(kind.pathway).map { it.id }
                if (fields.isEmpty()) continue
                val spend = EducationDemand.benchmarkSpend(kind, wage, params)
                val name = "${region.name} ${kind.label.lowercase()}"
                if (e.talent.education.registerPublic(region.country, region.id, kind, name, seats, spend, fields, tick) is Outcome.Ok) made++
            }
        }
        e.openTalent()
        return Outcome.ok(CommandResult(made.toLong(), "$made public schools and colleges opened"))
    }

    // ---- facts ---------------------------------------------------------------------------------

    /** The facts of the day from the workforce and the books: one line per cohort and per trading company. */
    fun inputs(tick: Tick): TalentInputs {
        val cohorts = e.workforce.all().map { c ->
            CohortFact(
                c.key, c.headcount, c.target, c.wage, e.laborMarkets.marketWage(MarketKey(c.key.region, c.key.role)),
                c.moralePpm, c.tenureTicks, c.loadPpm,
            )
        }
        val companies = e.companies.all().filter { it.isActive }.sortedBy { it.id }
            .map { CompanyFact(it.id, e.finance.ledger.cash(it.owner), it.kind == CompanyKind.AI) }
        return TalentInputs(tick, cohorts, companies)
    }

    // ---- the day -------------------------------------------------------------------------------

    /** Runs the module's day: its changes reach the workforce at once and its payments are held for settlement. */
    fun advance(tick: Tick, summary: TickSummary) {
        if (!active) return
        val report = e.talent.advance(tick, world, inputs(tick))
        pending = pending + report.intents
        apply(report.effects)
        note(report.events)
        summary.talentEvents = report.events
    }

    /**
     * Hands the day's payments to the settlement phase and starts over. A treasury that cannot pay for its schools is
     * topped up first, as it is for benefits and public services.
     */
    fun drain(tick: Tick): List<SettlementIntent> {
        val out = pending
        pending = emptyList()
        for ((owner, amount) in out.filter { it.payer.kind == OwnerKind.GOVERNMENT }.groupBy { it.payer }.mapValues { (_, list) -> Money.sum(list.map { it.total }) }.toSortedMap()) {
            val country = app.many.economytycoon.core.CountryId(owner.id)
            val cash = e.finance.ledger.cash(owner)
            if (cash < amount) e.finance.mint(tick, owner, amount - cash, "talent:gov:${country.value}:${tick.value}")
            e.macro.record(country, GdpKind.GOVERNMENT, amount)
            e.macro.recordSpending(country, amount)
        }
        return out
    }

    // ---- changes to the workforce --------------------------------------------------------------

    /**
     * Carries out what the module asked of the workforce, in the order given. An effect for a cohort that is gone does
     * nothing; the module drops its own picture of that cohort on its next day.
     */
    fun apply(effects: List<WorkforceEffect>) {
        val w = e.workforce
        for (fx in effects) when (fx) {
            is WorkforceEffect.Hire -> w.addPeople(fx.cohort, fx.count)
            is WorkforceEffect.Transfer -> w.movePeople(fx.from, fx.to, fx.count, fx.newWage)
            is WorkforceEffect.RaiseWage -> w.raiseWageTo(fx.cohort, fx.newWage)
            is WorkforceEffect.Morale -> w.adjustMorale(fx.cohort, fx.deltaPpm)
            is WorkforceEffect.TrainingShare -> w.setAwayShare(fx.cohort, fx.ppm)
            is WorkforceEffect.Depart -> w.removePeople(fx.cohort, fx.count)
            is WorkforceEffect.Emigrate -> w.removePeople(fx.cohort, fx.count)
        }
    }

    private fun note(events: List<TalentEvent>) {
        for (ev in events) recent.addLast(ev)
        while (recent.size > RECENT_CAP) recent.removeFirst()
    }

    // ---- commands ------------------------------------------------------------------------------

    private fun regionKnown(region: RegionId) = e.world.regions().any { it.id == region }

    /** What a command needs to be true of the world before the module sees it; null when it is fine. */
    private fun precheck(cmd: TalentCommand, inputs: TalentInputs): Outcome<CommandResult>? {
        fun unknownRegion(region: RegionId) = if (regionKnown(region)) null else fail(Rules.UNKNOWN_ENTITY, "unknown region")
        return when (cmd) {
            is TalentCommand.OpenInstitution -> unknownRegion(cmd.region)
                ?: if (world.countryOf(cmd.region) != cmd.country) fail(Rules.INVALID_ARGUMENT, "that region is not in that country")
                else if (e.talent.education.buildCost(cmd.kind, cmd.capacity, world.averageWage(cmd.region)) > inputs.cashOf(cmd.actor))
                    fail(Rules.INSUFFICIENT_FUNDS, "the buildings would cost more than the company has")
                else null
            is TalentCommand.ExpandInstitution -> {
                val i = e.talent.education.get(cmd.institution)
                if (i != null && i.owner == cmd.actor && e.talent.education.buildCost(i.kind, cmd.seats, world.averageWage(i.region)) > inputs.cashOf(cmd.actor))
                    fail(Rules.INSUFFICIENT_FUNDS, "the buildings would cost more than the company has")
                else null
            }
            is TalentCommand.OpenProvider -> unknownRegion(cmd.region)
            is TalentCommand.LaunchCampaign -> unknownRegion(cmd.region)
            is TalentCommand.HirePerson -> unknownRegion(cmd.region)
            is TalentCommand.OpenPosition -> unknownRegion(cmd.region)
            else -> null
        }
    }

    /** Carries out one decision of [company]. Whatever it costs is paid at once, so the next command sees what is left. */
    fun execute(company: CompanyId, cmd: TalentCommand, tick: Tick): Outcome<CommandResult> {
        if (!active) return fail(Rules.WRONG_STATE, "skills and education are not set up")
        if (cmd is TalentCommand.SetImmigrationPolicy) return fail("talent.policy-not-allowed", "a company cannot make immigration law")
        if (cmd.actor != company) return fail("talent.actor-mismatch", "the order names a different company")
        val inputs = inputs(tick)
        precheck(cmd, inputs)?.let { return it }
        return e.talent.execute(cmd, tick, inputs, world).map { r -> settleResult(r) }
    }

    /** A country's immigration rules, set by the game. */
    fun setPolicy(cmd: Command.SetImmigrationPolicy, tick: Tick): Outcome<CommandResult> {
        if (!active) return fail(Rules.WRONG_STATE, "skills and education are not set up")
        val policy = TalentCommand.SetImmigrationPolicy(cmd.country, cmd.opennessPpm, cmd.quotaPpm, cmd.skilledOnly)
        return e.talent.execute(policy, tick, inputs(tick), world).map { r -> settleResult(r) }
    }

    private fun settleResult(r: TalentResult): CommandResult {
        if (r.intents.isNotEmpty()) e.finance.settlement.settle(e.tick, r.intents)
        apply(r.effects)
        note(r.events)
        return CommandResult(message = r.note)
    }

    // ---- a company ceases to exist -------------------------------------------------------------

    /** Closes what a company ran in the module: its schools, courses, training, campaigns, ladders and covenants. */
    fun forget(company: CompanyId, tick: Tick) {
        if (!active) return
        val t = e.talent
        for (i in t.education.ownedBy(company)) t.education.close(i.id, company, tick)
        for (p in t.providers.providersOf(company)) t.providers.close(company, p.id, tick)
        for (p in t.training.activeOf(company)) t.training.cancel(p.id, company, tick)
        for (c in t.brand.activeCampaigns(company)) t.brand.cancel(c.id, company)
        for (l in t.careers.laddersOf(company)) t.careers.remove(l.id, company)
        for (c in t.poaching.covenants(company)) t.poaching.releaseCovenant(c.id, company)
    }

    private companion object {
        const val RECENT_CAP = 200
    }
}
