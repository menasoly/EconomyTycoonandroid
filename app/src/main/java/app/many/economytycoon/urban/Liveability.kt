package app.many.economytycoon.urban

import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.mulDiv

enum class LiveabilityFactor(val label: String, val weightPermille: Int) {
    HOUSING("Housing", 160),
    COMMUTE("Commute", 140),
    SAFETY("Safety", 130),
    ENVIRONMENT("Environment", 120),
    SERVICES("Services", 150),
    GREEN("Green space", 80),
    AMENITY("Amenities", 90),
    JOBS("Jobs", 130),
}

/** A liveability score: the overall index and the score of each factor, all 0..1000. */
class LiveabilityScore(val index: Int, private val factorScores: IntArray) {
    fun of(f: LiveabilityFactor): Int = factorScores[f.ordinal]
    fun asMap(): Map<LiveabilityFactor, Int> = LiveabilityFactor.values().associateWith { factorScores[it.ordinal] }

    fun weakest(): LiveabilityFactor = LiveabilityFactor.values().minWithOrNull(compareBy({ factorScores[it.ordinal] }, { it.ordinal }))!!
    fun strongest(): LiveabilityFactor = LiveabilityFactor.values().maxWithOrNull(compareBy({ factorScores[it.ordinal] }, { -it.ordinal }))!!
}

data class RankEntry(val city: CityId, val rank: Int, val index: Int, val best: LiveabilityFactor, val worst: LiveabilityFactor)

/** Scores cities on how good they are to live in, and ranks them against each other. */
object Liveability {
    private fun linear(value: Long, best: Long, worst: Long): Int {
        if (best == worst) return 1000
        val t = (value - worst) * 1000L / (best - worst)
        return t.coerceIn(0L, 1000L).toInt()
    }

    private fun residentWeighted(plan: CityPlan, select: (Int) -> Long): Long {
        val total = plan.population
        if (total == 0L) return plan.districts.indices.sumOf(select) / plan.districts.size
        var acc = 0L
        for (d in plan.districts) acc += select(d.index) * d.residents
        return acc / total
    }

    fun score(plan: CityPlan, analysis: CityAnalysis, params: UrbanParams): LiveabilityScore {
        val housing = Housing.report(plan.districts, plan.inputs.population, plan.inputs.averageWage, params)
        val f = IntArray(LiveabilityFactor.values().size)
        // Housing: affordable first, and room for everyone.
        val afford = linear(housing.rentBurdenPpm, 200_000L, 600_000L)
        val lowAfford = linear(housing.lowIncomeBurdenPpm, 300_000L, 900_000L)
        val roomy = if (plan.inputs.population == 0L) 1000 else linear(housing.unhoused * Scale.PPM / plan.inputs.population, 0L, 150_000L)
        f[LiveabilityFactor.HOUSING.ordinal] = (afford * 4 + lowAfford * 3 + roomy * 3) / 10
        f[LiveabilityFactor.COMMUTE.ordinal] = if (plan.traffic.commuters == 0L) 700 else linear(plan.traffic.averageCommuteSeconds, 900L, 3_600L)
        f[LiveabilityFactor.SAFETY.ordinal] = (1000 - residentWeighted(plan) { analysis.factors[it].crime.toLong() }.toInt()).coerceIn(0, 1000)
        val dirt = residentWeighted(plan) { analysis.factors[it].pollution * 10L + analysis.factors[it].noise * 5L } / 15L
        f[LiveabilityFactor.ENVIRONMENT.ordinal] = (1000 - dirt * 1000L / 700L).coerceIn(0L, 1000L).toInt()
        val b = plan.budget
        f[LiveabilityFactor.SERVICES.ordinal] = (
            b.quality(Service.EDUCATION) * 25 + b.quality(Service.HEALTH) * 25 + b.quality(Service.POLICE) * 15 +
                b.quality(Service.FIRE) * 10 + b.quality(Service.SANITATION) * 15 + b.quality(Service.HOUSING_AID) * 10
            ) / 100
        val green = plan.districts.filter { it.zone == ZoneUse.GREEN }.sumOf { it.landSqm }
        val perResident = if (plan.population == 0L) 0L else green / plan.population
        f[LiveabilityFactor.GREEN.ordinal] = linear(perResident, 15L, 0L)
        f[LiveabilityFactor.AMENITY.ordinal] = residentWeighted(plan) { analysis.factors[it].amenity.toLong() }.coerceIn(0L, 1000L).toInt()
        val workers = mulDiv(plan.population, params.participationPpm, Scale.PPM)
        val balance = if (workers == 0L) Scale.PPM else mulDiv(plan.jobs, Scale.PPM, workers)
        val jobMatch = linear(balance, 1_000_000L, 400_000L)
        val employment = linear(plan.inputs.unemploymentPpm, 40_000L, 200_000L)
        f[LiveabilityFactor.JOBS.ordinal] = (jobMatch + employment) / 2
        var total = 0
        for (factor in LiveabilityFactor.values()) total += f[factor.ordinal] * factor.weightPermille
        return LiveabilityScore(total / 1000, f)
    }

    /** Ranks cities by index, best first; ties go to the lower city id. */
    fun rank(scores: Map<CityId, LiveabilityScore>): List<RankEntry> {
        val ordered = scores.entries.sortedWith(compareBy({ -it.value.index }, { it.key }))
        return ordered.mapIndexed { i, e -> RankEntry(e.key, i + 1, e.value.index, e.value.strongest(), e.value.weakest()) }
    }

    /** The city that leads on one factor, or null with no cities. */
    fun leader(scores: Map<CityId, LiveabilityScore>, factor: LiveabilityFactor): CityId? =
        scores.entries.sortedWith(compareBy({ -it.value.of(factor) }, { it.key })).firstOrNull()?.key
}
