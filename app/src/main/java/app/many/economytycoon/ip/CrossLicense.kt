package app.many.economytycoon.ip

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules

/** What a cross-licence would look like. */
data class CrossLicenseQuote(val valueA: Money, val valueB: Money, val balancing: Money, val payer: CompanyId?)

/**
 * Two companies each license a set of patents to the other. The portfolios are valued, and the one holding the less
 * valuable set pays half the difference as a lump sum. Both licences start together or not at all.
 */
class CrossLicenseService(
    private val state: IpState,
    private val book: LicenseBook,
    private val valuer: PatentValuer,
) {
    /** The nominal consideration paid when the two sides are of equal worth. */
    val nominal: Money = Money.units(1)

    fun quote(run: IpRun, a: CompanyId, b: CompanyId, patentsA: List<PatentId>, patentsB: List<PatentId>): Outcome<CrossLicenseQuote> {
        val va = valueOf(run, a, patentsA) ?: return Outcome.reject(Rules.NOT_OWNER, "the first set is not wholly owned by the first company")
        val vb = valueOf(run, b, patentsB) ?: return Outcome.reject(Rules.NOT_OWNER, "the second set is not wholly owned by the second company")
        val diff = valuer.balancingPayment(va, vb)
        val payer = when { diff.raw > 0L -> b; diff.raw < 0L -> a; else -> null }
        return Outcome.ok(CrossLicenseQuote(va, vb, diff.abs(), payer))
    }

    private fun valueOf(run: IpRun, owner: CompanyId, ids: List<PatentId>): Money? {
        var sum = Money.ZERO
        for (id in ids) {
            val p = state.patentMap[id] ?: return null
            if (p.owner != IpOwner.company(owner)) return null
            sum += valuer.value(p, run).value
        }
        return sum
    }

    fun propose(
        run: IpRun, a: CompanyId, b: CompanyId, patentsA: List<PatentId>, patentsB: List<PatentId>,
        scope: LicenseScope = LicenseScope.EVERYWHERE, termDays: Int = 5 * 360,
    ): Outcome<Pair<License, License>> {
        if (a == b) return Outcome.reject(Rules.INVALID_ARGUMENT, "a company cannot cross-license with itself")
        if (patentsA.isEmpty() || patentsB.isEmpty()) return Outcome.reject(Rules.INVALID_ARGUMENT, "both sides must bring patents")
        val q = quote(run, a, b, patentsA, patentsB).let { if (it is Outcome.Rejected) return it else (it as Outcome.Ok).value }
        // The payer's own licence-out carries the lump sum: A licenses to B, so B's payment sits on A's licence.
        val lumpOnA = if (q.payer == b) q.balancing else Money.ZERO
        val lumpOnB = if (q.payer == a) q.balancing else Money.ZERO
        val first = book.offer(run, OfferRequest(IpOwner.company(a), b, patentsA, RoyaltyTerms(RoyaltyBasis.LUMP_SUM, lumpSum = lumpOnA + nominal), LicenseKind.NON_EXCLUSIVE, scope, termDays))
        if (first is Outcome.Rejected) return first
        val one = (first as Outcome.Ok).value
        val second = book.offer(run, OfferRequest(IpOwner.company(b), a, patentsB, RoyaltyTerms(RoyaltyBasis.LUMP_SUM, lumpSum = lumpOnB + nominal), LicenseKind.NON_EXCLUSIVE, scope, termDays))
        if (second is Outcome.Rejected) {
            one.status = LicenseStatus.WITHDRAWN
            one.endedAt = run.tick
            return second
        }
        val two = (second as Outcome.Ok).value
        book.link(one, two)
        return Outcome.ok(one to two)
    }
}
