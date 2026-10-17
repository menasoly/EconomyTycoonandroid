package app.many.economytycoon.watch

import app.many.economytycoon.agenda.AgendaBuilder
import app.many.economytycoon.agenda.AgendaKind
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.markets.MarketKey
import app.many.economytycoon.simulation.Economy

/** What rules are checked against. */
interface WatchFacts {
    val day: Long
    val cash: Money
    val monthlyProfit: Money
    val idleFacilities: Int

    /** The price in a region, or null when the good has no market there. */
    fun price(region: RegionId, good: GoodId): Money?

    /** Today's change in a listed company's share price, in ppm; null when it is not listed. */
    fun stockMovePpm(issuer: CompanyId): Long?

    /** Days until the next loan payment, or null when none is coming. */
    fun daysToLoanPayment(): Long?

    /** Days until the next contract delivery the company has to make or expects. */
    fun daysToDelivery(): Long?
}

/** Names for messages. */
interface WatchNames {
    fun good(id: GoodId): String
    fun region(id: RegionId): String
    fun company(id: CompanyId): String
}

class EconomyWatchFacts(private val e: Economy, private val company: CompanyId) : WatchFacts {
    private val c = e.companies.company(company)
    private val agenda by lazy { AgendaBuilder(e).build(company, e.tick.value, HORIZON) }

    override val day: Long get() = e.tick.value
    override val cash: Money get() = c?.let { e.finance.statements.balance(it.owner, e.tick).cash } ?: Money.ZERO
    override val monthlyProfit: Money get() = c?.let { e.finance.statements.income(it.owner, Tick((e.tick.value - 30L).coerceAtLeast(0L)), e.tick).netIncome } ?: Money.ZERO
    override val idleFacilities: Int get() = e.production.facilitiesOf(company).count { f -> f.lines.none { it.batch != null } }

    override fun price(region: RegionId, good: GoodId): Money? =
        if (e.markets.state(MarketKey(region, good)) == null) null else e.markets.price(MarketKey(region, good)).perUnit

    override fun stockMovePpm(issuer: CompanyId): Long? {
        val l = e.exchange.listing(issuer) ?: return null
        val prev = l.previousClose.raw
        return if (prev <= 0L) 0L else (l.price.raw - prev) * 1_000_000L / prev
    }

    override fun daysToLoanPayment(): Long? = agenda.of(AgendaKind.LOAN_PAYMENT).minOfOrNull { it.day }?.minus(day)

    override fun daysToDelivery(): Long? =
        (agenda.of(AgendaKind.DELIVERY_OUT) + agenda.of(AgendaKind.DELIVERY_IN)).minOfOrNull { it.day }?.minus(day)

    private companion object { const val HORIZON = 90 }
}

class EconomyWatchNames(private val e: Economy) : WatchNames {
    override fun good(id: GoodId) = e.content.goodOrNull(id)?.name ?: id.key.replace('_', ' ')
    override fun region(id: RegionId) = e.world.regions().firstOrNull { it.id == id }?.name ?: "region ${id.value}"
    override fun company(id: CompanyId) = e.companies.company(id)?.name ?: "company ${id.value}"
}
