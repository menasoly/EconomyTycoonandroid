package app.many.economytycoon.simulation

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.PolicyId
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.AccountRef
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.Entry
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.ReasonCode
import app.many.economytycoon.finance.TransactionDraft
import app.many.economytycoon.production.Facility
import app.many.economytycoon.production.FacilityStatus
import app.many.economytycoon.risk.Claim
import app.many.economytycoon.risk.CoverLevel
import app.many.economytycoon.risk.Deductible
import app.many.economytycoon.risk.Disaster
import app.many.economytycoon.risk.Peril
import app.many.economytycoon.risk.Policy
import app.many.economytycoon.risk.RiskEvent

/** What a policy would cost before the player commits to it. */
data class PolicyQuote(val insuredValue: Money, val annualRatePpm: Long, val premium: Money, val perils: Set<Peril>)

/**
 * Rolls the dice on disasters, applies the damage to buildings and stock, bills premiums and pays claims.
 * Damage lands whether or not a facility is insured; insurance only decides who bears the cost.
 */
class RiskRunner(private val e: Economy) {
    private var seq = 0L

    // ---- cover ---------------------------------------------------------------------------------

    fun insuredValueOf(f: Facility): Money {
        val def = e.content.facilityTypeOrNull(f.type) ?: return f.inventory.totalValue()
        val building = def.buildCost.scale(e.risk.params.buildingShareOfValuePpm, 1_000_000L)
        return building + f.inventory.totalValue()
    }

    private fun buildingValue(f: Facility): Money = e.content.facilityTypeOrNull(f.type)?.buildCost ?: Money.ZERO

    fun quote(f: Facility, level: CoverLevel, coveragePpm: Long, deductible: Deductible, surchargePpm: Long = 0L): PolicyQuote {
        val region = e.world.region(f.region)
        val rate = e.risk.annualRatePpm(region, level, coveragePpm, deductible, f.conditionPpm, f.pollutionPpm)
        val insured = insuredValueOf(f)
        return PolicyQuote(insured, rate, e.risk.premiumFor(insured, rate, surchargePpm), level.perils)
    }

    private fun seedReserve(tick: Tick) {
        if (e.risk.reserveSeeded) return
        e.risk.reserveSeeded = true
        e.finance.mint(tick, e.risk.insurer, e.risk.params.insurerReserve, "insurer:reserve")
    }

    fun problemWithCover(holder: CompanyId, f: Facility?): String? = when {
        f == null -> "unknown facility"
        f.owner != holder -> "that facility belongs to another company"
        f.status == FacilityStatus.DEMOLISHED -> "that facility has been demolished"
        e.risk.activeFor(f.id) != null -> "that facility is already insured"
        else -> null
    }

    fun buy(holder: CompanyId, facility: FacilityId, level: CoverLevel, coveragePpm: Long, deductible: Deductible, tick: Tick): Outcome<Policy> {
        val f = e.production.facility(facility)
        problemWithCover(holder, f)?.let {
            return Outcome.reject(if (f == null) Rules.UNKNOWN_ENTITY else if (f.owner != holder) Rules.NOT_OWNER else Rules.WRONG_STATE, it)
        }
        f!!
        e.risk.checkChoice(coveragePpm)?.let { return Outcome.Rejected(it) }
        val q = quote(f, level, coveragePpm, deductible)
        if (e.finance.ledger.cash(LedgerOwner.company(holder)) < q.premium) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "the first premium is ${q.premium}")
        val sold = e.risk.sell(holder, facility, f.region, level, coveragePpm, deductible, q.insuredValue, q.premium, tick)
        if (sold is Outcome.Rejected) return sold
        seedReserve(tick)
        if (!payPremium(holder, q.premium, tick, "premium:${(sold as Outcome.Ok).value.id.value}")) {
            e.risk.cancel(holder, sold.value.id, tick)
            return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "the first premium could not be paid")
        }
        return sold
    }

    fun cancel(holder: CompanyId, id: PolicyId, tick: Tick): Outcome<Policy> = e.risk.cancel(holder, id, tick)

    private fun payPremium(holder: CompanyId, amount: Money, tick: Tick, memo: String): Boolean {
        if (amount.raw <= 0L) return true
        val payer = LedgerOwner.company(holder)
        if (e.finance.ledger.cash(payer) < amount) return false
        val insurer = e.risk.insurer
        return e.finance.ledger.post(
            TransactionDraft(
                "insurance:${tick.value}:${seq++}", tick, ReasonCode.INSURANCE,
                listOf(
                    Entry(AccountRef(payer, Chart.OTHER_EXPENSE), amount), Entry(AccountRef(payer, Chart.CASH), -amount),
                    Entry(AccountRef(insurer, Chart.CASH), amount), Entry(AccountRef(insurer, Chart.REVENUE_OTHER), -amount),
                ),
                memo,
            ),
        ).isOk
    }

    private fun payClaim(holder: CompanyId, amount: Money, tick: Tick, memo: String): Boolean {
        if (amount.raw <= 0L) return true
        val payee = LedgerOwner.company(holder)
        val insurer = e.risk.insurer
        return e.finance.ledger.post(
            TransactionDraft(
                "claim:${tick.value}:${seq++}", tick, ReasonCode.INSURANCE,
                listOf(
                    Entry(AccountRef(insurer, Chart.OTHER_EXPENSE), amount), Entry(AccountRef(insurer, Chart.CASH), -amount),
                    Entry(AccountRef(payee, Chart.CASH), amount), Entry(AccountRef(payee, Chart.REVENUE_OTHER), -amount),
                ),
                memo,
            ),
        ).isOk
    }

    // ---- the day -------------------------------------------------------------------------------

    fun run(tick: Tick): List<RiskEvent> {
        val events = ArrayList<RiskEvent>()
        bill(tick, events)
        val random = e.random.stream("risk")
        val params = e.risk.params
        val facilities = e.production.facilities().filter { it.status != FacilityStatus.DEMOLISHED }.sortedBy { it.id }

        // Regional events: one roll per region and peril.
        for (region in e.world.regions().sortedBy { it.id }) {
            for (peril in Peril.values()) {
                if (!peril.regional) continue
                if (random.nextLong(1_000_000L) >= e.risk.chancePerTickPpm(region, peril)) continue
                val severity = severity(random)
                val hit = facilities.filter { it.region == region.id }
                events += strike(peril, region.id, severity, hit, tick, random, scatter = true)
            }
        }
        // Fires: one roll per facility.
        for (f in facilities) {
            val region = e.world.region(f.region)
            val chance = mulDiv(e.risk.chancePerTickPpm(region, Peril.FIRE), e.risk.fireMultiplePpm(f.conditionPpm, f.pollutionPpm), 1_000_000L)
            if (random.nextLong(1_000_000L) >= chance) continue
            events += strike(Peril.FIRE, f.region, severity(random), listOf(f), tick, random, scatter = false)
        }
        return events
    }

    private fun severity(random: app.many.economytycoon.core.DeterministicRandom): Long {
        val p = e.risk.params
        val u = random.nextLong(1_000_000L)
        val skew = u * u / 1_000_000L
        return p.minSeverityPpm + mulDiv(p.maxSeverityPpm - p.minSeverityPpm, skew, 1_000_000L)
    }

    /** Applies one event to the facilities in its path and settles whatever is insured. */
    fun strike(peril: Peril, region: app.many.economytycoon.core.RegionId, severityPpm: Long, hit: List<Facility>, tick: Tick, random: app.many.economytycoon.core.DeterministicRandom?, scatter: Boolean): List<RiskEvent> {
        val id = e.ids.event()
        val events = ArrayList<RiskEvent>()
        val claims = ArrayList<Claim>()
        for (f in hit) {
            // Not every building in a region suffers the same; a few are spared the worst.
            val s = if (scatter && random != null) mulDiv(severityPpm, 600_000L + random.nextLong(400_001L), 1_000_000L) else severityPpm
            val claim = damage(id, peril, f, s, tick) ?: continue
            claims += claim
        }
        val total = claims.fold(Money.ZERO) { a, c -> a + c.loss }
        val insured = claims.fold(Money.ZERO) { a, c -> a + c.payout }
        if (peril.regional || claims.isNotEmpty()) {
            val d = Disaster(id, peril, region, tick, severityPpm, claims.size, total, insured)
            e.risk.record(d)
            events += RiskEvent.Struck(d)
        }
        for (c in claims) {
            e.risk.record(c)
            events += if (c.insured) RiskEvent.Paid(c) else RiskEvent.Uncovered(c)
        }
        return events
    }

    private fun damage(disaster: app.many.economytycoon.core.EventId, peril: Peril, f: Facility, severityPpm: Long, tick: Tick): Claim? {
        val params = e.risk.params
        val bDrop = (mulDiv(severityPpm, peril.buildingFactorPpm, 1_000_000L)).coerceAtMost((f.conditionPpm - params.conditionFloorPpm).coerceAtLeast(0L))
        f.conditionPpm = f.conditionPpm - bDrop
        val buildingLoss = Money(mulDiv(buildingValue(f).raw, bDrop, 1_000_000L))
        var stockLoss = Money.ZERO
        val share = mulDiv(severityPpm, peril.stockFactorPpm, 1_000_000L)
        for ((good, slot) in f.inventory.entries()) {
            val lost = Quantity(mulDiv(slot.available.raw, share, 1_000_000L))
            if (lost.raw <= 0L) continue
            val cost = (f.inventory.remove(good, lost) as? Outcome.Ok)?.value ?: continue
            stockLoss += cost
        }
        val loss = buildingLoss + stockLoss
        if (loss.raw <= 0L) return null
        val policy = e.risk.activeFor(f.id)?.takeIf { it.covers(peril) }
        if (policy == null) return Claim(disaster, peril, null, f.owner, f.id, tick, buildingLoss, stockLoss, Money.ZERO, Money.ZERO)
        val eligible = Money.max(loss - policy.deductibleAmount, Money.ZERO)
        val owed = Money.min(eligible.scale(policy.coveragePpm, 1_000_000L), policy.insuredValue.scale(policy.coveragePpm, 1_000_000L))
        val available = Money.max(e.finance.ledger.cash(e.risk.insurer), Money.ZERO)
        val pay = Money.min(owed, available)
        if (pay.raw > 0L && !payClaim(f.owner, pay, tick, "claim:${policy.id.value}")) {
            return Claim(disaster, peril, policy.id, f.owner, f.id, tick, buildingLoss, stockLoss, Money.ZERO, owed)
        }
        e.risk.paidOut(policy, pay, tick)
        return Claim(disaster, peril, policy.id, f.owner, f.id, tick, buildingLoss, stockLoss, pay, owed - pay)
    }

    // ---- premiums ------------------------------------------------------------------------------

    private fun bill(tick: Tick, events: MutableList<RiskEvent>) {
        for (p in e.risk.activePolicies()) {
            if (p.paidThrough > tick) continue
            val f = e.production.facility(p.facility)
            if (f == null || f.owner != p.holder || f.status == FacilityStatus.DEMOLISHED) {
                e.risk.lapse(p, tick)
                events += RiskEvent.Lapsed(p.id, p.holder, p.facility, tick)
                continue
            }
            val q = quote(f, p.level, p.coveragePpm, p.deductible, p.surchargePpm)
            if (payPremium(p.holder, q.premium, tick, "premium:${p.id.value}")) {
                e.risk.billed(p, q.insuredValue, q.premium, tick)
            } else {
                e.risk.lapse(p, tick)
                events += RiskEvent.Lapsed(p.id, p.holder, p.facility, tick)
            }
        }
    }

    fun forget(company: CompanyId, tick: Tick) = e.risk.endAllOf(company, tick)
}
