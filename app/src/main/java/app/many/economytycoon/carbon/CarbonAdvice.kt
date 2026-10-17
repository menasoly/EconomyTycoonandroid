package app.many.economytycoon.carbon

import app.many.economytycoon.advisor.Insight
import app.many.economytycoon.advisor.Topic
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RoundingMode
import app.many.economytycoon.core.mulDiv

/** Numbers the carbon advice compares against. */
data class CarbonAdviceTuning(
    /** A deadline this close with a projected shortfall is urgent. */
    val urgentDays: Int = 30,
    /** Projected bill above this share of the company's yearly revenue is worth flagging, in ppm. */
    val heavyBillPpm: Long = 60_000L,
    /** Permits beyond this share of the year's need are surplus. */
    val surplusPpm: Long = 1_300_000L,
    /** Grades at or below this are flagged. */
    val weakGrade: CarbonGrade = CarbonGrade.BB,
    val minTonnes: Tonnes = Tonnes.of(50),
)

/**
 * Advice on a company's carbon position, in the advisor's form. The rules read the same view the screens do, and each
 * returns at most one insight, so the list stays short.
 */
object CarbonAdvice {

    fun insights(view: CompanyCarbonView, revenue: Money, tuning: CarbonAdviceTuning = CarbonAdviceTuning()): List<Insight> {
        val out = ArrayList<Insight>()
        for (a in view.accounts) {
            if (a.kind == RegimeKind.NONE) continue
            shortfall(a, tuning)?.let { out += it }
            if (a.kind == RegimeKind.CAP_AND_TRADE) {
                surplus(a, tuning)?.let { out += it }
                lapsing(a)?.let { out += it }
            }
            heavyBill(a, revenue, tuning)?.let { out += it }
        }
        abatement(view)?.let { out += it }
        credits(view)?.let { out += it }
        rating(view, tuning)?.let { out += it }
        return out.sortedWith(compareByDescending<Insight> { it.priority }.thenBy { it.code })
    }

    private fun shortfall(a: AccountView, t: CarbonAdviceTuning): Insight? {
        val p = a.pending ?: return null
        val short = p.projectedShortfall
        if (short < t.minTonnes) return null
        val urgent = p.daysLeft <= t.urgentDays
        val priority = if (urgent) 95 else 75
        return Insight(
            "carbon.shortfall", Topic.FINANCE, priority, "Short of permits for ${p.year}",
            "You are ${short.format()} t short with ${p.daysLeft} days left. Each tonne missed is fined; buy permits or credits before the deadline.",
        )
    }

    private fun surplus(a: AccountView, t: CarbonAdviceTuning): Insight? {
        if (a.pending != null) return null
        val need = a.projectedCovered
        val limit = need.times(Rate(t.surplusPpm))
        if (a.permitsHeld < t.minTonnes || a.permitsHeld <= limit) return null
        val excess = a.permitsHeld - limit
        if (excess < t.minTonnes) return null
        return Insight(
            "carbon.surplus", Topic.FINANCE, 40, "Spare permits",
            "You hold ${excess.format()} t more than this year needs. Selling them now turns a cost into cash; permits also expire after a few years.",
        )
    }

    private fun lapsing(a: AccountView): Insight? {
        if (!a.expiringNext.isPositive) return null
        return Insight(
            "carbon.lapsing", Topic.FINANCE, 50, "Permits about to lapse",
            "${a.expiringNext.format()} t of your oldest permits expire at the start of next year. Use or sell them first.",
        )
    }

    private fun heavyBill(a: AccountView, revenue: Money, t: CarbonAdviceTuning): Insight? {
        if (revenue.raw <= 0L || a.projectedBill.raw <= 0L) return null
        val share = mulDiv(a.projectedBill.raw, Rate.ONE_PPM, revenue.raw, RoundingMode.HALF_EVEN)
        if (share < t.heavyBillPpm) return null
        return Insight(
            "carbon.exposure", Topic.FINANCE, 60 + (share / 50_000L).toInt().coerceAtMost(25), "Carbon is a large cost",
            "Pricing your emissions this year comes to ${a.projectedBill}, ${share / 10_000L}% of revenue. Cutting emissions is cheaper than paying for them once a measure costs less than ${a.price} a tonne.",
        )
    }

    private fun abatement(view: CompanyCarbonView): Insight? {
        val priced = view.accounts.filter { it.kind != RegimeKind.NONE && it.projectedCovered.isPositive }
        if (priced.isEmpty()) return null
        val best = view.options.filter { opt -> priced.any { a -> opt.costPerTonne < a.price } }.minByOrNull { it.costPerTonne } ?: return null
        val live = view.abatement.any { it.facility == best.facility && it.kind == best.kind && (it.status == AbatementStatus.BUILDING || it.status == AbatementStatus.OPERATING) }
        if (live) return null
        return Insight(
            "carbon.abate", Topic.PRODUCTION, 55, "A cheaper way to cut emissions",
            "${best.kind.label} at one of your facilities would cut ${best.annualAbatement.format()} t a year for ${best.costPerTonne} a tonne, less than you pay for permits.",
        )
    }

    private fun credits(view: CompanyCarbonView): Insight? {
        val pending = view.accounts.mapNotNull { it.pending }.firstOrNull { it.offsetAllowance.isPositive && it.outstanding.isPositive } ?: return null
        if (view.offsetsTotal.isPositive) return null
        return Insight(
            "carbon.credits", Topic.FINANCE, 35, "Credits can cover part of the bill",
            "Up to ${pending.offsetAllowance.format()} t of ${pending.year}'s obligation may be met with verified credits, often cheaper than permits.",
        )
    }

    private fun rating(view: CompanyCarbonView, t: CarbonAdviceTuning): Insight? {
        val r = view.rating ?: return null
        if (r.grade.ordinal < t.weakGrade.ordinal) return null
        val weakest = r.factors.minByOrNull { it.points * 1000L / it.maximum.coerceAtLeast(1) } ?: return null
        return Insight(
            "carbon.rating", Topic.FINANCE, 45, "Weak carbon rating: ${r.grade.label}",
            "A low rating widens your borrowing spread by ${r.grade.spreadBp} basis points. Weakest area: ${weakest.label.lowercase()} (${weakest.note}).",
        )
    }
}
