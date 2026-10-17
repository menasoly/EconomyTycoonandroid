package app.many.economytycoon.ip

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.mulDiv

/** A fair reading of a case at one moment, used by both sides' decisions and by the screens. */
data class CaseEstimate(
    /** Chance the claimant wins, ppm. */
    val claimantWinPpm: Long,
    /** Claimant strength minus defence strength, ppm. */
    val marginPpm: Long,
    /** What the claimant would be awarded on a win, damages and costs shifted. */
    val awardIfWon: Money,
    val claimantCostToGo: Money,
    val defendantCostToGo: Money,
    /** The least the claimant should take, and the most the defendant should pay. */
    val claimantReserve: Money,
    val defendantReserve: Money,
) {
    val zoneOfAgreement: Boolean get() = claimantReserve <= defendantReserve
}

/** The arithmetic of lawsuits. Free of state so every number can be checked on its own. */
object Litigation {
    /** The claimant's side of an infringement case, ppm: how broad and novel the patent is and how good the evidence. */
    fun infringementStrength(lp: LitigationParams, regime: LegalRegime, breadthPpm: Long, noveltyPpm: Long, evidencePpm: Long): Long =
        mulDiv(breadthPpm, lp.breadthWeightPpm, 1_000_000L) + mulDiv(noveltyPpm, lp.noveltyWeightPpm, 1_000_000L) +
            mulDiv(evidencePpm, lp.evidenceWeightPpm, 1_000_000L) + regime.patenteeBiasPpm

    /** The defence: prior art shows in a patent's lack of novelty, and design differences show in the defendant's evidence. */
    fun defenceStrength(noveltyPpm: Long, evidencePpm: Long): Long = (1_000_000L - noveltyPpm) / 2 + evidencePpm / 2

    /** A challenger attacking validity wins on lack of novelty and good evidence; the owner defends on the same two things. */
    fun challengeStrength(noveltyPpm: Long, evidencePpm: Long): Long = mulDiv(1_000_000L - noveltyPpm, 600_000L, 1_000_000L) + mulDiv(evidencePpm, 400_000L, 1_000_000L)
    fun validityDefence(regime: LegalRegime, noveltyPpm: Long, evidencePpm: Long): Long =
        mulDiv(noveltyPpm, 500_000L, 1_000_000L) + mulDiv(evidencePpm, 300_000L, 1_000_000L) + regime.patenteeBiasPpm / 2

    /** Theft of a secret turns almost entirely on proof; documented secrecy measures help the claimant. */
    fun misappropriationStrength(evidencePpm: Long, protectionPpm: Long): Long = mulDiv(evidencePpm, 800_000L, 1_000_000L) + mulDiv(protectionPpm, 200_000L, 1_000_000L)

    /**
     * Chance the claimant wins at a given margin: certain beyond the decisive margin either way and a straight line
     * through even odds in between.
     */
    fun winProbabilityPpm(lp: LitigationParams, marginPpm: Long): Long {
        val d = lp.decisiveMarginPpm
        return when {
            marginPpm >= d -> 1_000_000L
            marginPpm <= -d -> 0L
            else -> 500_000L + mulDiv(marginPpm, 500_000L, d)
        }
    }

    /**
     * The court's decision. Clear margins decide the case on the evidence alone; inside the grey band the draw
     * [rollPpm] settles it, so close cases are the only ones luck can turn.
     */
    fun decide(lp: LitigationParams, marginPpm: Long, rollPpm: Long): Boolean = rollPpm < winProbabilityPpm(lp, marginPpm)

    /** Chance an appeal reverses the judgment: tiny when the margin was clear, up to the maximum when it was a coin toss. */
    fun reversalPpm(lp: LitigationParams, marginPpm: Long): Long {
        val closeness = (lp.decisiveMarginPpm - Math.abs(marginPpm)).coerceAtLeast(0L)
        return mulDiv(lp.appealReversalMaxPpm, closeness, lp.decisiveMarginPpm)
    }

    /** Damages for infringement over [infringingSales]: the greater of a reasonable royalty and the claimant's lost profit, enhanced if wilful. */
    fun damages(
        lp: LitigationParams, regime: LegalRegime, infringingSales: Money, royaltyPpm: Long, willful: Boolean, claimantShare: Rate, claimantMargin: Rate,
    ): Money {
        val royalty = infringingSales.times(Rate(royaltyPpm))
        val lost = infringingSales.times(claimantShare).times(claimantMargin)
        var base = Money.max(royalty, lost)
        base = base.times(Rate(regime.damagesMultiplierPpm))
        if (willful && regime.enhancedDamages) base = base.times(Rate(lp.enhancedMultiplePpm))
        return Money.max(base, Money.units(lp.minDamagesUnits))
    }

    fun damagesForTheft(lp: LitigationParams, regime: LegalRegime, annualAdvantage: Money, yearsLost: Int): Money =
        Money.max(Money(mulDiv(annualAdvantage.raw * yearsLost.coerceAtLeast(1), lp.misappropriationMultiplePpm, 1_000_000L)).times(Rate(regime.damagesMultiplierPpm)), Money.units(lp.minDamagesUnits))

    /** Daily cost of running a case in a stage, before the country's cost level. */
    fun dailyCost(lp: LitigationParams, stage: DisputeStage): Money = Money.units(
        when (stage) {
            DisputeStage.NOTICE -> lp.noticeCostUnitsPerDay
            DisputeStage.FILED -> lp.noticeCostUnitsPerDay * 2
            DisputeStage.DISCOVERY -> lp.discoveryCostUnitsPerDay
            DisputeStage.TRIAL -> lp.trialCostUnitsPerDay
            DisputeStage.APPEAL -> lp.appealCostUnitsPerDay
            else -> 0L
        },
    )

    /** Days a stage lasts; zero for stages that wait for someone's decision. */
    fun stageDays(lp: LitigationParams, stage: DisputeStage): Int = when (stage) {
        DisputeStage.NOTICE -> lp.noticeResponseDays
        DisputeStage.FILED -> lp.pleadingDays
        DisputeStage.DISCOVERY -> lp.discoveryDays
        DisputeStage.TRIAL -> lp.trialDays
        DisputeStage.JUDGMENT -> lp.appealWindowDays
        DisputeStage.APPEAL -> lp.appealDays
        else -> 0
    }

    private val ORDER = listOf(DisputeStage.FILED, DisputeStage.DISCOVERY, DisputeStage.TRIAL)

    /** What each side still expects to spend if the case is fought to judgment from [stage], [daysIn] days into it. */
    fun costToGo(lp: LitigationParams, regime: LegalRegime, stage: DisputeStage, daysIn: Int): Money {
        var sum = 0L
        val level = regime.legalCostMultiplierPpm
        fun add(s: DisputeStage, days: Int) { sum += mulDiv(dailyCost(lp, s).raw * days.coerceAtLeast(0), level, 1_000_000L) }
        when (stage) {
            DisputeStage.DETECTED, DisputeStage.NOTICE -> {
                if (stage == DisputeStage.NOTICE) add(DisputeStage.NOTICE, lp.noticeResponseDays - daysIn)
                sum += mulDiv(Money.units(lp.filingCostUnits).raw, level, 1_000_000L)
                for (s in ORDER) add(s, stageDays(lp, s))
            }
            DisputeStage.FILED, DisputeStage.DISCOVERY, DisputeStage.TRIAL -> {
                val idx = ORDER.indexOf(stage)
                add(stage, stageDays(lp, stage) - daysIn)
                for (s in ORDER.drop(idx + 1)) add(s, stageDays(lp, s))
            }
            DisputeStage.APPEAL -> add(stage, stageDays(lp, stage) - daysIn)
            else -> {}
        }
        return Money(sum)
    }

    /**
     * Reserves for settling. The claimant needs at least its expected net from going on; the defendant should pay up to
     * its expected loss. Costs saved by settling are what open the zone.
     */
    fun estimate(
        lp: LitigationParams, marginPpm: Long, award: Money, costShifted: Money, claimantCostToGo: Money, defendantCostToGo: Money, injunctionHarm: Money,
    ): CaseEstimate {
        val p = winProbabilityPpm(lp, marginPpm)
        val winAward = award + costShifted
        val claimantNet = Money(mulDiv(winAward.raw, p, 1_000_000L)) - claimantCostToGo
        val defendantLoss = Money(mulDiv((winAward + injunctionHarm).raw, p, 1_000_000L)) + defendantCostToGo
        return CaseEstimate(p, marginPpm, winAward, claimantCostToGo, defendantCostToGo, claimantNet.coerceAtLeast(Money.ZERO), defendantLoss.coerceAtLeast(Money.ZERO))
    }

    /** Where a deal would land if both sides split the gap between their reserves. */
    fun midpoint(e: CaseEstimate): Money = Money((e.claimantReserve.raw + e.defendantReserve.raw) / 2)

}
