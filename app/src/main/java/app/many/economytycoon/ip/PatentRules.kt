package app.many.economytycoon.ip

import app.many.economytycoon.content.TechDef
import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.isqrt
import app.many.economytycoon.core.mulDiv

/** The result of examining one national application. */
sealed class ExamResult {
    data class Granted(val breadthPpm: Long) : ExamResult()
    data class Narrowed(val breadthPpm: Long) : ExamResult()
    object Refused : ExamResult()
}

/** The arithmetic of getting and keeping a patent, kept free of state so it can be tested and used for quotes. */
object PatentRules {
    fun claimsFeeUnits(office: OfficeParams, claims: Int): Long =
        office.extraClaimFeeUnits * (claims - office.includedClaims).coerceAtLeast(0)

    /** Everything due when an application goes in: filing, search, examination and extra claims. */
    fun applicationFee(office: OfficeParams, regime: LegalRegime, claims: Int): Money {
        val base = Money.units(office.filingFeeUnits + office.searchFeeUnits + office.examinationFeeUnits + claimsFeeUnits(office, claims))
        return base.times(app.many.economytycoon.core.Rate(regime.feeMultiplierPpm))
    }

    fun grantFee(office: OfficeParams, regime: LegalRegime): Money =
        Money.units(office.grantFeeUnits).times(app.many.economytycoon.core.Rate(regime.feeMultiplierPpm))

    /** Fee for the [year]th year after grant (1-based): it climbs by a fixed step of the base every year. */
    fun maintenanceFee(office: OfficeParams, regime: LegalRegime, year: Int): Money {
        val growth = 1_000_000L + office.maintenanceGrowthPpm * (year - 1).coerceAtLeast(0)
        val base = Money.units(office.maintenanceBaseUnits).times(app.many.economytycoon.core.Rate(growth))
        return base.times(app.many.economytycoon.core.Rate(regime.feeMultiplierPpm))
    }

    fun termYears(regime: LegalRegime, pharma: Boolean): Int = regime.termYears + if (pharma) regime.pharmaExtensionYears else 0

    fun termTicks(regime: LegalRegime, pharma: Boolean): Long = termYears(regime, pharma).toLong() * Calendar.DAYS_PER_YEAR

    /** Total of the maintenance fees from the first year to the last year before expiry. */
    fun lifetimeMaintenance(office: OfficeParams, regime: LegalRegime, pharma: Boolean, yearsHeld: Int): Money {
        var sum = Money.ZERO
        val last = minOf(yearsHeld, termYears(regime, pharma) - 1)
        for (y in 1..last) sum += maintenanceFee(office, regime, y)
        return sum
    }

    /**
     * How new the invention is, ppm. Higher tiers and costlier research are more novel; a crowded field, rivals who
     * already know the technology and a published patent on the same technology all pull it down.
     */
    fun novelty(
        office: OfficeParams, def: TechDef, priorArtAffinityPpm: Long, samePublishedPatent: Boolean, othersKnowing: Int, jitterPpm: Long,
    ): Long {
        val costBonus = (isqrt(def.researchCost.raw / Money.SCALE) * office.costNoveltyPerRootPpm).coerceAtMost(office.costNoveltyCapPpm)
        val crowd = mulDiv(priorArtAffinityPpm, office.crowdStepPpm, 1_000_000L).coerceAtMost(office.maxCrowdPpm)
        val holders = if (othersKnowing <= 0) -office.firstMoverPpm else (office.perHolderPenaltyPpm * othersKnowing).coerceAtMost(office.maxHolderPenaltyPpm)
        val same = if (samePublishedPatent) office.sameTechPenaltyPpm else 0L
        val raw = office.baseNoveltyPpm + office.tierNoveltyStepPpm * def.tier + costBonus - crowd - holders - same + jitterPpm
        return raw.coerceIn(30_000L, 980_000L)
    }

    /** Chance an application is granted as asked, ppm. Wide claims are harder to get than narrow ones. */
    fun grantProbability(office: OfficeParams, regime: LegalRegime, noveltyPpm: Long, breadthPpm: Long): Long {
        val p = office.baseGrantPpm + mulDiv(noveltyPpm, office.noveltyGrantWeightPpm, 1_000_000L) -
            mulDiv(breadthPpm, office.breadthGrantPenaltyPpm, 1_000_000L) - regime.strictnessPpm
        return p.coerceIn(office.minGrantPpm, office.maxGrantPpm)
    }

    /** Days an application waits for examination in a country with [pendingThere] applications ahead of it. */
    fun examinationDays(office: OfficeParams, regime: LegalRegime, pendingThere: Int, jitterPpm: Long): Int {
        val base = mulDiv(office.baseExaminationDays.toLong(), regime.examDelayMultiplierPpm, 1_000_000L)
        val backlog = (office.backlogDaysPerPending.toLong() * pendingThere).coerceAtMost(office.maxBacklogDays.toLong())
        val jitter = mulDiv(base, jitterPpm, 1_000_000L)
        return (base + backlog + jitter).coerceAtLeast(30L).toInt()
    }

    /** Decides an application from a roll in [0, 1) ppm. A roll just above the grant probability still earns a narrower patent. */
    fun examine(office: OfficeParams, regime: LegalRegime, noveltyPpm: Long, breadthPpm: Long, rollPpm: Long): ExamResult {
        val p = grantProbability(office, regime, noveltyPpm, breadthPpm)
        return when {
            rollPpm < p -> ExamResult.Granted(breadthPpm)
            rollPpm < p + office.amendBandPpm -> ExamResult.Narrowed(mulDiv(breadthPpm, office.narrowFactorPpm, 1_000_000L).coerceAtLeast(100_000L))
            else -> ExamResult.Refused
        }
    }
}
