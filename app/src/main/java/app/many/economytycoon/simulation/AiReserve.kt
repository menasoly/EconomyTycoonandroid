package app.many.economytycoon.simulation

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money

/**
 * How much cash a computer-run company must keep back for running what it already owns. Every AI module that
 * puts money to work (shares, bonds, futures, land) leaves this much untouched, so a company that has
 * invested its spare cash can still pay its staff and buy its inputs.
 */
class AiReserve(private val e: Economy) {
    /** Wages and upkeep for one day, from the staff on hand and the facilities standing. */
    fun dailyCost(id: CompanyId): Money {
        val payroll = e.workforce.stats(id, e.tick).dailyPayroll
        val upkeep = e.production.facilitiesOf(id).fold(Money.ZERO) { acc, f -> acc + (e.content.facilityTypeOrNull(f.type)?.upkeepPerTick ?: Money.ZERO) }
        return payroll + upkeep
    }

    /** Cash to hold back: [days] of running costs, never less than [minimum]. */
    fun floor(id: CompanyId, days: Int = DAYS, minimum: Money = MINIMUM): Money = Money.max(minimum, dailyCost(id) * days.toLong())

    companion object {
        const val DAYS = 120
        val MINIMUM: Money = Money.units(60_000)
    }
}
