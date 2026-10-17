package app.many.economytycoon.talent

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.SettlementIntent

data class BrandReport(val intents: List<SettlementIntent>, val effects: List<WorkforceEffect>, val events: List<TalentEvent>)

/**
 * Employer branding. A company's standing among job seekers follows how it pays and treats its people; advertising
 * makes it known; a recruiting campaign turns that standing into graduates hired for open posts.
 */
class BrandService(
    private val state: TalentState,
    private val taxonomy: SkillTaxonomy,
    private val markets: SkillMarketService,
    private val seed: Long,
    val params: BrandParams = BrandParams(),
) {
    fun brand(company: CompanyId, region: RegionId): EmployerBrand? = state.brands[BrandKey(company, region)]
    fun campaign(id: RecruitCampaignId): RecruitCampaign? = state.campaigns[id]
    fun campaigns(): List<RecruitCampaign> = state.campaigns.values.toList()
    fun activeCampaigns(company: CompanyId): List<RecruitCampaign> = state.campaigns.values.filter { it.isActive && it.company == company }

    fun appeal(company: CompanyId, region: RegionId): Long = brand(company, region)?.appealPpm ?: params.neutralPpm

    /** How strongly job seekers in the region are drawn to the company, 0..1,000,000 (neutral 500,000). */
    fun brandOf(company: CompanyId, region: RegionId): Long {
        val b = brand(company, region) ?: return params.neutralPpm
        val reach = 300_000L + Ppm.mul(b.awarenessPpm, 700_000L)
        return (params.neutralPpm + Ppm.mul(b.appealPpm - params.neutralPpm, reach)).coerceIn(0L, Ppm.ONE)
    }

    /** Extra share of the best candidates a company gets on hires of [role] in its region because of running campaigns. */
    fun hireBoostPpm(company: CompanyId, region: RegionId, role: RoleId): Long {
        if (state.campaigns.values.none { it.isActive && it.company == company && it.region == region && it.role == role }) return 0L
        val aw = brand(company, region)?.awarenessPpm ?: 0L
        return Ppm.mul(params.bestShareBoostPpm, 300_000L + Ppm.mul(aw, 700_000L))
    }

    fun launch(company: CompanyId, region: RegionId, role: RoleId, budget: Money, days: Int, tick: Tick, inputs: TalentInputs): Outcome<Pair<RecruitCampaign, List<SettlementIntent>>> {
        if (taxonomy.profile(role) == null) return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown role")
        if (days < params.minCampaignDays || days > params.maxCampaignDays) return Outcome.reject(Rules.INVALID_ARGUMENT, "a campaign runs ${params.minCampaignDays}..${params.maxCampaignDays} days")
        if (budget < params.minBudget) return Outcome.reject(Rules.INVALID_PRICE, "budget below the minimum")
        if (activeCampaigns(company).size >= params.maxCampaignsPerCompany) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many campaigns")
        if (state.campaigns.values.any { it.isActive && it.company == company && it.region == region && it.role == role }) return Outcome.reject(Rules.DUPLICATE, "already recruiting for that role there")
        if (inputs.cashOf(company) < budget) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "cannot afford the campaign")
        val c = RecruitCampaign(state.counters.campaign(), company, region, role, budget, tick, tick + days.toLong())
        state.campaigns[c.id] = c
        return Outcome.ok(c to listOfNotNull(TalentLedger.fee("campaign", c.id.value, company, budget, tick, "recruiting campaign")))
    }

    fun cancel(id: RecruitCampaignId, company: CompanyId): Outcome<RecruitCampaign> {
        val c = state.campaigns[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown campaign")
        if (c.company != company) return Outcome.reject(Rules.NOT_OWNER, "not your campaign")
        if (!c.isActive) return Outcome.reject(Rules.WRONG_STATE, "campaign is over")
        c.restore(CampaignStatus.CANCELLED, c.hired, c.carryPpm)
        return Outcome.ok(c)
    }

    /** Monthly: standing follows pay and morale, awareness follows advertising, campaigns hire, old ones end. */
    fun monthly(tick: Tick, inputs: TalentInputs, trainingShare: (CompanyId) -> Long = { 0L }): BrandReport {
        val effects = ArrayList<WorkforceEffect>(); val events = ArrayList<TalentEvent>()
        updateAppeal(inputs, trainingShare)
        for (c in state.campaigns.values.toList()) if (c.isActive) runCampaign(c, tick, inputs, effects, events)
        prune(tick)
        return BrandReport(emptyList(), effects, events)
    }

    private fun updateAppeal(inputs: TalentInputs, trainingShare: (CompanyId) -> Long) {
        data class Acc(var heads: Long = 0L, var wage: Long = 0L, var morale: Long = 0L)
        val acc = java.util.TreeMap<BrandKey, Acc>()
        for (f in inputs.cohorts) {
            if (f.headcount <= 0L) continue
            val a = acc.getOrPut(BrandKey(f.key.company, f.key.region)) { Acc() }
            a.heads += f.headcount; a.wage += f.wageRatioPpm * f.headcount; a.morale += f.moralePpm * f.headcount
        }
        // companies that have no staff left in a region slowly fade there
        for (k in state.brands.keys) if (k !in acc) acc[k] = Acc()
        for ((k, a) in acc) {
            val b = state.brands.getOrPut(k) { EmployerBrand() }
            val wageRatio = if (a.heads > 0L) a.wage / a.heads else Ppm.ONE
            val morale = if (a.heads > 0L) a.morale / a.heads else 600_000L
            val target = (params.neutralPpm + Ppm.mul(wageRatio - Ppm.ONE, params.wageWeightPpm) + Ppm.mul(morale - 600_000L, params.moraleWeightPpm) +
                Ppm.mul(trainingShare(k.company), params.trainingWeightPpm)).coerceIn(params.minAppealPpm, params.maxAppealPpm)
            b.appealPpm = Ppm.approach(b.appealPpm, target, params.appealPaceMonthlyPpm)
            val fame = Ppm.mul(Ppm.clamp(Ppm.ratio(a.heads, params.fameHeadcount)), params.fameFloorPpm)
            val decayed = b.awarenessPpm - Ppm.mul(b.awarenessPpm, params.awarenessDecayPpm)
            val adGain = state.campaigns.values.filter { it.isActive && it.company == k.company && it.region == k.region }.sumOf { monthlyGain(it) }
            b.awarenessPpm = (maxOf(decayed, fame) + adGain).coerceIn(0L, Ppm.ONE)
        }
    }

    private fun monthlyGain(c: RecruitCampaign): Long {
        val perMonth = Money(c.budget.raw * 30L / c.durationDays.coerceAtLeast(1L))
        val sat = Ppm.ratio(perMonth.raw, perMonth.raw + params.halfSaturationSpend.raw)
        return Ppm.mul(sat, params.awarenessGainCapPpm)
    }

    private fun runCampaign(c: RecruitCampaign, tick: Tick, inputs: TalentInputs, effects: MutableList<WorkforceEffect>, events: MutableList<TalentEvent>) {
        if (tick == c.startTick || tick.value <= c.startTick.value) return
        val profile = taxonomy.profile(c.role)
        val open = inputs.cohorts.filter { it.key.company == c.company && it.key.region == c.region && it.key.role == c.role && it.openings > 0L }
        if (profile != null && open.isNotEmpty()) {
            val pool = markets.poolSize(c.region, profile.primary)
            val rate = Ppm.mul(params.monthlyConversionPpm, 300_000L + brandOf(c.company, c.region)) * 2L
            val exact = pool * rate + c.carryPpm
            var budget = exact / Ppm.ONE
            var carry = exact % Ppm.ONE
            val r = DeterministicRandom(RandomProvider.deriveSeed(seed, "brand.campaign", c.id.value * 1_000_003L + tick.value))
            if (carry > 0L && r.nextLong(Ppm.ONE) < carry) { budget += 1L; carry = 0L }
            var left = budget
            var total = 0L
            for (f in open) {
                if (left <= 0L) break
                val n = minOf(f.openings, left)
                effects.add(WorkforceEffect.Hire(f.key, n)); left -= n; total += n
            }
            c.restore(c.status, c.hired + total, if (left > 0L) 0L else carry)
            if (total > 0L) events.add(TalentEvent.GraduatesRecruited(c.id, c.company, total, tick))
        }
        if (tick >= c.endTick) {
            c.restore(CampaignStatus.DONE, c.hired, 0L)
            events.add(TalentEvent.CampaignEnded(c.id, c.company, c.hired, tick))
        }
    }

    internal fun prune(tick: Tick) {
        state.campaigns.entries.removeAll { !it.value.isActive && tick.value - it.value.endTick.value > params.keepDays }
    }
}
