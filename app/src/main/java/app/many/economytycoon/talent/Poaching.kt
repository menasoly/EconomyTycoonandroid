package app.many.economytycoon.talent

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.workforce.CohortKey

/** Who a headhunter is after. */
sealed class PoachTarget {
    /** [count] of the best people in a cohort, at least [minLevelPpm] in the main skill. */
    data class Group(val cohort: CohortKey, val count: Long, val minLevelPpm: Long = 0L) : PoachTarget()
    data class Individual(val person: PersonId) : PoachTarget()
}

/** What a hunter does about a non-compete covering the people it is after. */
enum class CovenantStance { RESPECT, BUY_OUT, IGNORE }

enum class OfferStatus { PENDING, SUCCEEDED, COUNTERED, REJECTED, BLOCKED }

data class PoachParams(
    /** Days a search firm takes to make the approach and get an answer. */
    val resolveDays: Int = 10,
    /** Fee as a share of the first year's pay of whoever moves; a quarter is paid at the start. */
    val feePpm: Long = 150_000L,
    val retainerShareOfFeePpm: Long = 250_000L,
    val maxShareOfCohortPpm: Long = 300_000L,
    val maxPendingPerHunter: Int = 4,
    /** Days before the same hunter may approach the same cohort again. */
    val cooldownDays: Int = 60,
    /** Weights of the acceptance odds. */
    val basePpm: Long = 120_000L,
    val payPremiumWeightPpm: Long = 1_800_000L,
    val moraleWeightPpm: Long = 500_000L,
    val tenureLoyaltyPpm: Long = 150_000L,
    val tenureFullYears: Long = 5L,
    val bondWeightPpm: Long = 400_000L,
    val bonusWeightPpm: Long = 1_000_000L,
    val brandWeightPpm: Long = 300_000L,
    val certifiedWeightPpm: Long = 100_000L,
    val retentionWeightPpm: Long = 1_500_000L,
    val minOddsPpm: Long = 20_000L,
    val maxOddsPpm: Long = 900_000L,
    /** A counter-offer wins people back with this probability before loyalty and morale are counted. */
    val counterBasePpm: Long = 400_000L,
    /** Share of covenant penalty owed as damages when a hunter ignores it and is taken to court. */
    val damagesMultiplierPpm: Long = 1_500_000L,
    val courtWinsPpm: Long = 700_000L,
    /** A covenant costs the employer this share of the covered wages while it runs. */
    val covenantPayPpm: Long = 30_000L,
    val covenantMoralePpm: Long = 12_000L,
    val maxCovenantMonths: Int = 36,
    val maxPenaltyYears: Long = 2L,
    val maxCovenantsPerCompany: Int = 12,
    val poachedMoralePpm: Long = 250_000L,
    val referenceCounterRaisePpm: Long = 150_000L,
    val keepDays: Int = 360,
)

/** A company's answer to a rival's approach to its people. */
class CounterPolicy(val company: CompanyId) {
    var enabled: Boolean = false
        internal set
    /** Highest raise over the current wage the company will match, ppm. */
    var maxMatchPpm: Long = 150_000L
        internal set

    internal fun restore(enabled: Boolean, maxMatch: Long) { this.enabled = enabled; maxMatchPpm = maxMatch }
    fun hashInto(h: CanonicalHasher) { h.putLong(company.value).putBoolean(enabled).putLong(maxMatchPpm) }
}

/** One approach by a hunter to the people of another company. */
class HeadhuntOffer(
    val id: OfferId,
    val hunter: CompanyId,
    val victim: CompanyId,
    val target: PoachTarget,
    /** Where the people would work if they came. */
    val destination: CohortKey,
    val offeredWage: Money,
    val signingBonus: Money,
    val stance: CovenantStance,
    val submittedAt: Tick,
    val resolveAt: Tick,
    /** People the approach is about, fixed when it was made. */
    val heads: Long,
) {
    var status: OfferStatus = OfferStatus.PENDING
        internal set
    var movedHeads: Long = 0L
        internal set
    var keptHeads: Long = 0L
        internal set
    var counterWage: Money? = null
        internal set
    var oddsPpm: Long = 0L
        internal set
    var feePaid: Money = Money.ZERO
        internal set
    var resolvedAt: Tick? = null
        internal set

    val isPending: Boolean get() = status == OfferStatus.PENDING

    internal fun restore(status: OfferStatus, moved: Long, kept: Long, counter: Money?, odds: Long, fee: Money, resolved: Tick?) {
        this.status = status; movedHeads = moved; keptHeads = kept; counterWage = counter; oddsPpm = odds; feePaid = fee; resolvedAt = resolved
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(hunter.value).putLong(victim.value)
        when (val t = target) {
            is PoachTarget.Group -> h.putInt(1).putLong(t.cohort.company.value).putLong(t.cohort.facility.value).putString(t.cohort.role.key).putLong(t.cohort.region.value).putLong(t.count).putLong(t.minLevelPpm)
            is PoachTarget.Individual -> h.putInt(2).putLong(t.person.value)
        }
        h.putLong(destination.company.value).putLong(destination.facility.value).putString(destination.role.key).putLong(destination.region.value)
        h.putMoney(offeredWage).putMoney(signingBonus).putInt(stance.ordinal).putLong(submittedAt.value).putLong(resolveAt.value).putLong(heads)
        h.putInt(status.ordinal).putLong(movedHeads).putLong(keptHeads).putLong(counterWage?.raw ?: -1L).putLong(oddsPpm).putMoney(feePaid).putLong(resolvedAt?.value ?: -1L)
    }
}

/** A non-compete: covered people may not join a rival until [until]. */
class NonCompete(
    val id: CovenantId,
    val company: CompanyId,
    val cohort: CohortKey?,
    val person: PersonId?,
    val region: RegionId?,
    val headsCovered: Long,
    val signedAt: Tick,
    val until: Tick,
    /** Owed per head if a rival takes them anyway. */
    val penaltyPerHead: Money,
) {
    var breaches: Int = 0
        internal set
    var lapsed: Boolean = false
        internal set

    fun isActive(tick: Tick): Boolean = !lapsed && tick < until
    fun covers(key: CohortKey?, who: PersonId?): Boolean = (key != null && key == cohort) || (who != null && who == person)

    internal fun restore(breaches: Int, lapsed: Boolean) { this.breaches = breaches; this.lapsed = lapsed }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(company.value)
        h.putBoolean(cohort != null)
        if (cohort != null) h.putLong(cohort.company.value).putLong(cohort.facility.value).putString(cohort.role.key).putLong(cohort.region.value)
        h.putLong(person?.value ?: -1L).putLong(region?.value ?: -1L).putLong(headsCovered).putLong(signedAt.value).putLong(until.value).putMoney(penaltyPerHead)
        h.putInt(breaches).putBoolean(lapsed)
    }
}

/** A bonus paid to keep a cohort, which also makes its people harder to hire away. */
class RetentionGrant(val cohort: CohortKey, val until: Tick, val strengthPpm: Long) {
    fun hashInto(h: CanonicalHasher) {
        h.putLong(cohort.company.value).putLong(cohort.facility.value).putString(cohort.role.key).putLong(cohort.region.value).putLong(until.value).putLong(strengthPpm)
    }
}

data class CooldownKey(val hunter: CompanyId, val cohort: CohortKey) : Comparable<CooldownKey> {
    override fun compareTo(other: CooldownKey): Int {
        val c = hunter.compareTo(other.hunter)
        return if (c != 0) c else cohort.compareTo(other.cohort)
    }
}

/** Everything that bears on whether people say yes to a rival, as plain numbers. */
data class OddsInputs(
    val currentWage: Money,
    val offeredWage: Money,
    val signingBonus: Money,
    val moralePpm: Long,
    val tenureTicks: Long,
    val bondedSharePpm: Long,
    val hunterBrandPpm: Long,
    val certifiedSharePpm: Long,
    val retentionPpm: Long,
    val loyaltyPpm: Long = 0L,
)

object PoachOdds {
    /** Chance that a person approached by a rival says yes, ppm. */
    fun acceptance(i: OddsInputs, p: PoachParams): Long {
        val wage = i.currentWage.raw.coerceAtLeast(1L)
        val premium = Ppm.ratio(i.offeredWage.raw - wage, wage)
        val bonus = Ppm.ratio(i.signingBonus.raw, wage * 360L)
        var odds = p.basePpm
        odds += Ppm.mul(premium, p.payPremiumWeightPpm)
        odds += Ppm.mul(600_000L - i.moralePpm, p.moraleWeightPpm)
        odds -= Ppm.mul(Ppm.clamp(Ppm.ratio(i.tenureTicks, p.tenureFullYears * 360L)), p.tenureLoyaltyPpm)
        odds -= Ppm.mul(i.bondedSharePpm, p.bondWeightPpm)
        odds += Ppm.mul(bonus, p.bonusWeightPpm)
        odds += Ppm.mul(i.hunterBrandPpm - 500_000L, p.brandWeightPpm)
        odds += Ppm.mul(i.certifiedSharePpm, p.certifiedWeightPpm)
        odds -= Ppm.mul(i.retentionPpm, p.retentionWeightPpm)
        odds -= Ppm.mul(i.loyaltyPpm - 500_000L, 400_000L)
        return odds.coerceIn(p.minOddsPpm, p.maxOddsPpm)
    }

    /** Chance that a counter-offer at [counterWage] keeps someone who was ready to leave for [offeredWage]. */
    fun counterSuccess(counterWage: Money, offeredWage: Money, moralePpm: Long, loyaltyPpm: Long, p: PoachParams): Long {
        val gap = if (offeredWage.raw <= 0L) 0L else Ppm.ratio(counterWage.raw - offeredWage.raw, offeredWage.raw)
        var odds = p.counterBasePpm + Ppm.mul(gap, 2_000_000L) + Ppm.mul(moralePpm - 500_000L, 300_000L) + Ppm.mul(loyaltyPpm - 500_000L, 300_000L)
        return odds.coerceIn(50_000L, 950_000L)
    }

    /** The agency's fee for [heads] people on [wage] a day. */
    fun fee(wage: Money, heads: Long, p: PoachParams): Money = Money(Ppm.of(wage.raw * 360L, p.feePpm)) * heads
}
