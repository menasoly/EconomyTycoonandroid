package app.many.economytycoon.talent

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.splitLargestRemainder

/** A place that can take students, as the allocator sees it. */
data class SeatOffer(val id: InstitutionId, val weight: Long, val free: Long)

/** Pure rules for who wants to study and where they end up. */
object EducationDemand {

    /** Share of an entry cohort that wants a place, given what study pays in the region ([premiumPpm], 1.0 is par). */
    fun participationPpm(kind: InstitutionKind, premiumPpm: Long, p: EducationParams): Long {
        val pull = Ppm.ONE + Ppm.mul(premiumPpm - Ppm.ONE, kind.premiumSensitivityPpm)
        val factor = if (kind.premiumSensitivityPpm == 0L) Ppm.ONE else pull.coerceIn(p.minPremiumPpm, p.maxPremiumPpm)
        return Ppm.clamp(Ppm.mul(kind.participationPpm, factor))
    }

    /** How affordable a fee is against yearly pay: 1.0 free, falling to the floor as the fee nears [EducationParams.affordabilityYears] of pay. */
    fun affordabilityPpm(tuition: Money, dailyWage: Money, p: EducationParams): Long {
        if (!tuition.isPositive) return Ppm.ONE
        val yearlyPay = dailyWage.raw * 360L
        if (yearlyPay <= 0L) return p.minAffordabilityPpm
        val burden = Ppm.ratio(tuition.raw, Ppm.of(yearlyPay, p.affordabilityYears))
        return (Ppm.ONE - burden).coerceIn(p.minAffordabilityPpm, Ppm.ONE)
    }

    /** What a student sees in an institution: good teaching, at a price they can pay. */
    fun attractionPpm(qualityPpm: Long, affordabilityPpm: Long): Long = Ppm.mul(qualityPpm + 200_000L, affordabilityPpm).coerceAtLeast(10_000L)

    /**
     * Shares out [seekers] over the offers in proportion to their weight, never beyond free seats; what a full
     * institution cannot take goes to the others. Returns the placed students per institution and the unplaced rest.
     */
    fun allocate(seekers: Long, offers: List<SeatOffer>): Pair<Map<InstitutionId, Long>, Long> {
        val placed = java.util.TreeMap<InstitutionId, Long>()
        var left = seekers.coerceAtLeast(0L)
        val free = java.util.TreeMap<InstitutionId, Long>()
        for (o in offers) { free[o.id] = o.free.coerceAtLeast(0L); placed[o.id] = 0L }
        repeat(offers.size.coerceAtLeast(1) + 1) {
            if (left <= 0L) return@repeat
            val open = offers.filter { (free[it.id] ?: 0L) > 0L }
            if (open.isEmpty()) return@repeat
            val capacityLeft = open.sumOf { free[it.id] ?: 0L }
            val round = minOf(left, capacityLeft)
            val shares = splitLargestRemainder(round, LongArray(open.size) { open[it].weight.coerceAtLeast(1L) })
            var used = 0L
            for ((i, o) in open.withIndex()) {
                val take = minOf(shares[i], free[o.id] ?: 0L)
                placed[o.id] = (placed[o.id] ?: 0L) + take
                free[o.id] = (free[o.id] ?: 0L) - take
                used += take
            }
            left -= used
            if (used == 0L) return@repeat
        }
        return placed to left
    }

    /** Splits an intake over the fields an institution teaches by how well each field pays. */
    fun splitByField(count: Long, fields: List<SkillId>, premiumOf: (SkillId) -> Long): Map<SkillId, Long> {
        if (count <= 0L || fields.isEmpty()) return emptyMap()
        val weights = LongArray(fields.size) { premiumOf(fields[it]).coerceAtLeast(300_000L) }
        val parts = splitLargestRemainder(count, weights)
        val out = java.util.TreeMap<SkillId, Long>()
        for ((i, f) in fields.withIndex()) if (parts[i] > 0L) out[f] = parts[i]
        return out
    }

    /** The level an institution's graduates reach: the kind's average, moved by teaching quality. */
    fun graduateLevelPpm(kind: InstitutionKind, qualityPpm: Long, p: EducationParams): Long =
        (kind.graduateLevelPpm + Ppm.mul(qualityPpm - 500_000L, p.levelSpreadPpm)).coerceIn(100_000L, 950_000L)

    /** Share of an intake that will finish: the kind's average, moved by quality and by how crowded the place is. */
    fun completionPpm(kind: InstitutionKind, qualityPpm: Long, occupancyPpm: Long, p: EducationParams): Long {
        val q = 800_000L + Ppm.mul(qualityPpm, 400_000L)
        val base = Ppm.mul(kind.completionPpm, q)
        val crowd = if (occupancyPpm > p.crowdingStartsPpm) Ppm.mul(occupancyPpm - p.crowdingStartsPpm, p.completionSpreadPpm) else 0L
        return (base - crowd).coerceIn(300_000L, p.maxCompletionPpm)
    }

    /** Quality an institution settles toward: spending at the benchmark gives one half, and more gives ever less extra. */
    fun qualityTargetPpm(spend: Money, benchmark: Money, occupancyPpm: Long, p: EducationParams): Long {
        val ratio = if (benchmark.raw <= 0L) Ppm.ONE else Ppm.ratio(spend.raw, benchmark.raw)
        val base = Ppm.ratio(ratio, ratio + Ppm.ONE)
        val crowd = if (occupancyPpm > p.crowdingStartsPpm) Ppm.mul(occupancyPpm - p.crowdingStartsPpm, p.crowdingPenaltyPpm) else 0L
        return Ppm.clamp(base - crowd)
    }

    /** The teaching spend a region's pay level calls for, per student-year. */
    fun benchmarkSpend(kind: InstitutionKind, dailyWage: Money, p: EducationParams): Money {
        val wageIndex = if (p.referenceWage.raw <= 0L) Ppm.ONE else Ppm.ratio(dailyWage.raw, p.referenceWage.raw)
        return Money.units(kind.benchmarkSpendUnits).times(app.many.economytycoon.core.Rate(wageIndex))
    }
}
