package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.companies.CompanyStatus
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/**
 * Computer-run companies with cash to spare lend some of it to governments that are selling bonds, as long as
 * the coupon beats the central bank's rate by a margin. Each company gets one look at every issue.
 */
class AiBonds(private val e: Economy, private val executor: CommandExecutor) {
    class Report(var subscriptions: Int = 0)

    private val cashFloor = Money.units(80_000)
    private val shareOfSurplusPpm = 250_000L
    private val cap = Money.units(400_000)
    private val hurdlePpm = 8_000L

    fun run(tick: Tick): Report {
        val report = Report()
        val open = e.bonds.open()
        if (open.isEmpty()) return report
        val ai = e.companies.all().filter { it.kind == CompanyKind.AI && it.status == CompanyStatus.ACTIVE }.sortedBy { it.id }
        val days = e.bonds.params.subscriptionDays.toLong()
        for (b in open) {
            val hurdle = e.macro.policyRatePpm(b.country) + hurdlePpm
            if (b.couponPpm < hurdle) continue
            for (c in ai) {
                if ((tick.value - b.issuedAt.value) % days != c.id.value % days) continue
                if (b.unitsLeft <= 0L) break
                val cash = e.finance.ledger.cash(c.owner)
                val surplus = cash - Money.max(cashFloor, e.aiReserve.floor(c.id))
                if (surplus.raw <= 0L) continue
                val budget = Money.min(cap, Money(mulDiv(surplus.raw, shareOfSurplusPpm, 1_000_000L)))
                val units = minOf(b.unitsLeft, budget.raw / b.faceEach.raw)
                if (units <= 0L) continue
                val r = executor.execute(Actor.Ai(c.id), Command.SubscribeBond(c.id, b.id, units))
                if (r is Outcome.Ok) report.subscriptions++
            }
        }
        return report
    }
}
