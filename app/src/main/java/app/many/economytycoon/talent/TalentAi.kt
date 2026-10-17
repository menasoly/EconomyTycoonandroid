package app.many.economytycoon.talent

import app.many.economytycoon.competitors.AiPersonality
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.workforce.CohortKey

/** How a computer-controlled company treats its people, derived from its general character. All ppm weights. */
data class TalentStance(
    /** How readily it trains its people when they fall short of the job. */
    val trainingPpm: Long,
    /** How willing it is to hire people away from rivals. */
    val poachingPpm: Long,
    /** How much it cares about being seen as a good employer. */
    val brandPpm: Long,
    /** How hard it works to keep people. */
    val retentionPpm: Long,
    /** How seriously it plans for the day key people leave. */
    val successionPpm: Long,
    /** How willing it is to build schools and training businesses. */
    val educationPpm: Long,
) {
    companion object {
        fun of(p: AiPersonality): TalentStance = TalentStance(
            trainingPpm = ((p.qualityFocus * 6L + p.expansionAppetite * 2L + (Ppm.ONE - p.costFocus) * 2L) / 10L).coerceIn(50_000L, 950_000L),
            poachingPpm = ((p.aggression * 7L + p.riskTolerance * 3L) / 10L).coerceIn(0L, 950_000L),
            brandPpm = ((p.qualityFocus * 5L + p.expansionAppetite * 5L) / 10L).coerceIn(50_000L, 950_000L),
            retentionPpm = ((p.qualityFocus * 4L + (Ppm.ONE - p.aggression) * 3L + (Ppm.ONE - p.riskTolerance) * 3L) / 10L).coerceIn(50_000L, 950_000L),
            successionPpm = ((Ppm.ONE - p.riskTolerance) * 6L / 10L + p.qualityFocus * 4L / 10L).coerceIn(50_000L, 950_000L),
            educationPpm = ((p.expansionAppetite * 5L + p.riskTolerance * 3L + p.qualityFocus * 2L) / 10L).coerceIn(0L, 950_000L),
        )
    }
}

data class TalentAiParams(
    val maxActionsPerRound: Int = 3,
    /** Productivity below this share of par makes a cohort worth training, scaled by the stance. */
    val trainBelowPpm: Long = 960_000L,
    val minCashDaysOfPay: Long = 60L,
    val lowMoralePpm: Long = 450_000L,
    val cheapWagePpm: Long = 900_000L,
    val retentionStrengthPpm: Long = 400_000L,
    val retentionDays: Int = 120,
    val hotPremiumPpm: Long = 1_100_000L,
    val poachRaiseBasePpm: Long = 120_000L,
    val poachRaiseSpanPpm: Long = 200_000L,
    val maxPoachHeads: Long = 10L,
    val campaignMinOpenings: Long = 5L,
    val campaignDays: Int = 90,
    val campaignDaysOfPayPerOpening: Long = 20L,
    val schoolCashDaysOfPay: Long = 400L,
    val schoolSeats: Long = 300L,
    val bondDays: Int = 360,
    val bondClawbackPpm: Long = 500_000L,
    val covenantMonths: Int = 12,
    val covenantMinHeads: Long = 8L,
    val covenantPenaltyDays: Long = 200L,
)

/**
 * The rule-based planner for the people side of a rival. It looks at its own cohorts, the markets and its rivals,
 * and returns a few commands per round, most pressing first. It holds no state: everything it needs is read from
 * the system, so a saved game replays the same decisions.
 */
class TalentAi(private val system: TalentSystem, val params: TalentAiParams = TalentAiParams()) {

    fun plan(company: CompanyId, personality: AiPersonality, tick: Tick, inputs: TalentInputs, world: TalentWorld): List<TalentCommand> {
        val stance = TalentStance.of(personality)
        val own = inputs.cohorts.filter { it.key.company == company && it.headcount > 0L }.sortedBy { it.key }
        if (own.isEmpty()) return emptyList()
        val cash = inputs.cashOf(company)
        val payroll = own.sumOf { it.wage.raw * it.headcount }
        val reserve = Money(payroll * params.minCashDaysOfPay)
        val out = ArrayList<TalentCommand>()
        fun room() = out.size < params.maxActionsPerRound

        if (room()) retention(company, stance, own, cash, reserve, tick)?.let { out += it }
        if (room()) counterPolicy(company, stance)?.let { out += it }
        if (room()) succession(company, stance, tick)?.let { out += it }
        if (room()) training(company, stance, own, cash, reserve, tick, inputs)?.let { out += it }
        if (room()) campaign(company, stance, own, cash, reserve)?.let { out += it }
        if (room()) covenant(company, stance, own, tick)?.let { out += it }
        if (room()) poach(company, stance, own, inputs, cash, reserve, tick)?.let { out += it }
        if (room()) ladder(company, stance, own)?.let { out += it }
        if (room()) school(company, stance, own, cash, payroll, world)?.let { out += it }
        return out
    }

    // ----- individual rules ------------------------------------------------------------------------------

    private fun retention(company: CompanyId, stance: TalentStance, own: List<CohortFact>, cash: Money, reserve: Money, tick: Tick): TalentCommand? {
        if (stance.retentionPpm < 350_000L) return null
        val at = own.firstOrNull {
            (it.moralePpm < params.lowMoralePpm || it.wageRatioPpm < params.cheapWagePpm) && system.poaching.retentionOf(it.key, tick) == 0L &&
                cash.raw - Money(it.wage.raw * 30L * it.headcount).raw > reserve.raw
        } ?: return null
        return TalentCommand.GrantRetention(company, at.key, params.retentionStrengthPpm, params.retentionDays)
    }

    private fun counterPolicy(company: CompanyId, stance: TalentStance): TalentCommand? {
        val current = system.poaching.counterPolicy(company)
        if (current != null) return null
        return TalentCommand.SetCounterPolicy(company, stance.retentionPpm >= 400_000L, 100_000L + stance.retentionPpm / 6L)
    }

    private fun succession(company: CompanyId, stance: TalentStance, tick: Tick): TalentCommand? {
        val su = system.succession
        val people = su.peopleOf(company)
        if (people.isEmpty()) return null
        for (pos in su.positionsOf(company)) {
            if (pos.isVacant) {
                val candidate = people.filter { it.position == null && it.role == pos.role }.maxByOrNull { su.readinessFor(it, pos.role) }
                if (candidate != null) return TalentCommand.AppointPerson(company, pos.id, candidate.id)
                continue
            }
            if (stance.successionPpm < 300_000L) continue
            val named = su.successorsOf(pos.id)
            if (named.size < 2) {
                val taken = named.map { it.person }.toSet() + setOfNotNull(pos.holder)
                val next = people.filter { it.id !in taken && it.position == null }.maxByOrNull { su.readinessFor(it, pos.role) }
                if (next != null) return TalentCommand.NameSuccessor(company, pos.id, next.id, developmentFor(stance))
            }
            val lagging = named.firstOrNull { it.development == DevelopmentKind.NONE }
            if (lagging != null) return TalentCommand.SetDevelopment(company, pos.id, lagging.person, developmentFor(stance))
        }
        return null
    }

    private fun developmentFor(stance: TalentStance) = when {
        stance.successionPpm >= 750_000L -> DevelopmentKind.EXECUTIVE_COURSE
        stance.successionPpm >= 550_000L -> DevelopmentKind.STRETCH
        else -> DevelopmentKind.MENTORING
    }

    private fun training(company: CompanyId, stance: TalentStance, own: List<CohortFact>, cash: Money, reserve: Money, tick: Tick, inputs: TalentInputs): TalentCommand? {
        val bar = params.trainBelowPpm + Ppm.mul(stance.trainingPpm, 100_000L)
        for (f in own) {
            if (system.training.activeOf(company).any { it.cohort == f.key }) continue
            val prod = system.skills.productivity(f.key) ?: continue
            if (prod.multiplierPpm >= bar) continue
            val skill = prod.weakest?.skill ?: continue
            val seats = (f.headcount * 150_000L / Ppm.ONE).coerceAtLeast(1L)
            val kind = if (f.tenureTicks >= 360L) ProgrammeKind.CERTIFICATION else ProgrammeKind.ON_THE_JOB
            val days = if (kind == ProgrammeKind.CERTIFICATION) 90 else 60
            val cost = system.training.quote(kind, days, f) * seats
            if (cash.raw - cost.raw < reserve.raw) continue
            val clause = if (kind.bondable && system.poaching.pendingOf(company).isEmpty() && stance.retentionPpm < 600_000L) RetentionClause(params.bondDays, params.bondClawbackPpm) else null
            return TalentCommand.StartTraining(company, f.key, skill, kind, seats, days, clause)
        }
        return null
    }

    private fun campaign(company: CompanyId, stance: TalentStance, own: List<CohortFact>, cash: Money, reserve: Money): TalentCommand? {
        if (stance.brandPpm < 350_000L) return null
        val active = system.brand.activeCampaigns(company)
        val f = own.filter { it.openings >= params.campaignMinOpenings && active.none { c -> c.region == it.key.region && c.role == it.key.role } }
            .maxByOrNull { it.openings } ?: return null
        val budget = Money(f.wage.raw * params.campaignDaysOfPayPerOpening * f.openings)
        if (budget < system.brand.params.minBudget || cash.raw - budget.raw < reserve.raw) return null
        return TalentCommand.LaunchCampaign(company, f.key.region, f.key.role, budget, params.campaignDays)
    }

    private fun covenant(company: CompanyId, stance: TalentStance, own: List<CohortFact>, tick: Tick): TalentCommand? {
        if (stance.retentionPpm < 450_000L) return null
        val f = own.firstOrNull { it.headcount >= params.covenantMinHeads && system.poaching.activeCovenants(company, tick).none { c -> c.cohort == it.key } && system.taxonomy.profile(it.key.role)?.band?.ordinal?.let { b -> b >= 2 } == true }
            ?: return null
        return TalentCommand.SignNonCompete(company, f.key, null, params.covenantMonths, Money(f.wage.raw * params.covenantPenaltyDays))
    }

    private fun poach(company: CompanyId, stance: TalentStance, own: List<CohortFact>, inputs: TalentInputs, cash: Money, reserve: Money, tick: Tick): TalentCommand? {
        if (stance.poachingPpm < 450_000L || system.poaching.pendingOf(company).size >= 2) return null
        for (mine in own.filter { it.openings > 0L }.sortedByDescending { it.openings }) {
            val profile = system.taxonomy.profile(mine.key.role) ?: continue
            if (system.markets.premium(mine.key.region, profile.primary) < params.hotPremiumPpm) continue
            val rival = inputs.cohorts.filter { it.key.company != company && it.key.role == mine.key.role && it.key.region == mine.key.region && it.headcount >= 6L && it.wageRatioPpm <= 1_100_000L }
                .sortedWith(compareBy<CohortFact> { it.wageRatioPpm }.thenBy { it.key }).firstOrNull() ?: continue
            if (system.poaching.activeCovenants(rival.key.company, tick).any { it.cohort == rival.key } && stance.poachingPpm < 800_000L) continue
            val heads = minOf(mine.openings, params.maxPoachHeads)
            val raise = params.poachRaiseBasePpm + Ppm.mul(stance.poachingPpm, params.poachRaiseSpanPpm)
            val wage = Money(rival.wage.raw + Ppm.mul(rival.wage.raw, raise))
            val outlay = PoachOdds.fee(wage, heads, system.params.poaching)
            if (cash.raw - outlay.raw < reserve.raw) continue
            val stanceToCovenant = if (stance.poachingPpm >= 800_000L) CovenantStance.BUY_OUT else CovenantStance.RESPECT
            return TalentCommand.SubmitOffer(company, mine.key, PoachTarget.Group(rival.key, heads), wage, Money.ZERO, stanceToCovenant)
        }
        return null
    }

    private fun ladder(company: CompanyId, stance: TalentStance, own: List<CohortFact>): TalentCommand? {
        if (stance.retentionPpm < 350_000L || system.careers.laddersOf(company).isNotEmpty()) return null
        val byRegion = own.groupBy { it.key.region }.entries.sortedBy { it.key }
        for ((_, cohorts) in byRegion) {
            val rungs = cohorts.filter { system.taxonomy.profile(it.key.role) != null }.sortedWith(compareBy<CohortFact> { it.wage.raw }.thenBy { it.key }).take(3)
            if (rungs.size < 2) continue
            return TalentCommand.CreateLadder(company, "Career path", rungs.mapIndexed { i, f ->
                if (i == 0) Rung(f.key, 0L, 0L, 0L) else Rung(f.key, 360L, 450_000L, 100_000L)
            })
        }
        return null
    }

    private fun school(company: CompanyId, stance: TalentStance, own: List<CohortFact>, cash: Money, payroll: Long, world: TalentWorld): TalentCommand? {
        if (stance.educationPpm < 550_000L || system.education.ownedBy(company).isNotEmpty()) return null
        if (cash.raw < payroll * params.schoolCashDaysOfPay) return null
        val shortages = system.markets.shortages().filter { row -> own.any { it.key.region == row.region } }
        val worst = shortages.firstOrNull { system.taxonomy.skill(it.skill).pathway == Pathway.VOCATIONAL } ?: return null
        val region = worst.region
        val fields = shortages.filter { it.region == region && system.taxonomy.skill(it.skill).pathway == Pathway.VOCATIONAL }.map { it.skill }.distinct().sorted().take(3)
        val wage = world.averageWage(region)
        return TalentCommand.OpenInstitution(
            company, world.countryOf(region), region, InstitutionKind.VOCATIONAL, "Company Technical College", params.schoolSeats,
            Money(wage.raw * 60L), EducationDemand.benchmarkSpend(InstitutionKind.VOCATIONAL, wage, system.params.education), fields,
        )
    }

    /** Providers are opened by the same rule, one step per round: the business first, then a course for the scarcest skill. */
    fun planProvider(company: CompanyId, personality: AiPersonality, tick: Tick, inputs: TalentInputs, world: TalentWorld): TalentCommand? {
        val stance = TalentStance.of(personality)
        if (stance.educationPpm < 600_000L) return null
        val mine = system.providers.providersOf(company)
        val shortage = system.markets.shortages().firstOrNull { system.taxonomy.skill(it.skill).pathway != Pathway.EXPERIENCE } ?: return null
        val cash = inputs.cashOf(company)
        if (mine.isEmpty()) {
            if (cash < system.providers.params.setupCost * 4L) return null
            return TalentCommand.OpenProvider(company, shortage.region, "Academy ${company.value}")
        }
        val p = mine.first()
        if (p.courseList().isNotEmpty() || p.region != shortage.region) return null
        val wage = world.averageWage(p.region)
        return TalentCommand.AddCourse(company, p.id, "Practical ${system.taxonomy.skill(shortage.skill).name}", shortage.skill, CourseKind.CERTIFICATE, 120L, Money(wage.raw * 25L))
    }
}
