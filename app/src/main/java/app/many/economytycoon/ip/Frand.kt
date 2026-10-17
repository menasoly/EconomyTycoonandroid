package app.many.economytycoon.ip

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.mulDiv

/** A way an offer fails the fair, reasonable and non-discriminatory test. */
data class FrandViolation(val code: String, val message: String)

/**
 * Holders of patents that every maker of a product must use have promised to license them fairly. A standard has a
 * ceiling on what all its essential patents together may charge; each holder's share of it follows its share of the
 * essential patents. Offers above that share are unfair, and offers worse than the best deal already given to a
 * comparable licensee are discriminatory.
 */
object Frand {
    const val UNFAIR = "unfair_rate"
    const val DISCRIMINATORY = "discriminatory"
    const val EXCLUSIVE = "exclusive_not_allowed"
    const val REFUSAL = "refusal_to_license"

    /** The most one holder may charge, as a share of sales, given its stake in the essential patents of the field. */
    fun fairCap(licensing: LicenseParams, holderEssential: Int, totalEssential: Int): Rate {
        if (totalEssential <= 0 || holderEssential <= 0) return Rate.ZERO
        val share = mulDiv(licensing.frandCapPpm, holderEssential.toLong(), totalEssential.toLong())
        return Rate(share.coerceAtLeast(1_000L))
    }

    /** The royalty an offer would ask on a sample of sales, as a share of those sales. */
    fun offeredRate(terms: RoyaltyTerms, sample: UsageReport): Rate {
        val running = when (terms.basis) {
            RoyaltyBasis.LUMP_SUM -> Money.ZERO
            else -> Royalty.running(terms, sample)
        }
        return if (sample.sales.raw <= 0L) Rate.ZERO else Rate(mulDiv(running.raw, Rate.ONE_PPM, sample.sales.raw))
    }

    fun check(
        licensing: LicenseParams,
        kind: LicenseKind,
        offered: Rate,
        cap: Rate,
        comparableRates: List<Rate>,
    ): List<FrandViolation> {
        val out = ArrayList<FrandViolation>()
        if (kind != LicenseKind.NON_EXCLUSIVE) out += FrandViolation(EXCLUSIVE, "Standard-essential patents must be offered to everyone, so they cannot be licensed exclusively.")
        if (cap.ppm > 0L && offered > cap) out += FrandViolation(UNFAIR, "The rate is above this holder's fair share of the standard's ceiling.")
        val best = comparableRates.minOrNull()
        if (best != null && best.ppm > 0L) {
            val limit = Rate(mulDiv(best.ppm, 1_000_000L + licensing.frandToleranceDiscPpm, 1_000_000L))
            if (offered > limit) out += FrandViolation(DISCRIMINATORY, "Another licensee already pays a clearly lower rate.")
        }
        return out
    }

    /** Whether an injunction may be sought: a holder cannot shut out a licensee who is willing to take FRAND terms. */
    fun injunctionAllowed(patentPledged: Boolean, defendantWilling: Boolean): Boolean = !(patentPledged && defendantWilling)
}
