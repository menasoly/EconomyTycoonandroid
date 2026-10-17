package app.many.economytycoon.ip

import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.TechId
import app.many.economytycoon.core.Tick

/** What a licensee sold using a technology over some stretch of days. */
data class UsageReport(val units: Quantity, val sales: Money) {
    operator fun plus(o: UsageReport) = UsageReport(Quantity(units.raw + o.units.raw), sales + o.sales)

    val isEmpty: Boolean get() = units.raw <= 0L && sales.raw <= 0L

    /** The larger of two reports by sales; used so a product covered by several patents is counted once. */
    fun max(o: UsageReport): UsageReport = if (o.sales > sales || (o.sales == sales && o.units.raw > units.raw)) o else this

    companion object {
        val ZERO = UsageReport(Quantity.ZERO, Money.ZERO)
    }
}

/** A company found selling something that uses a technology in a country. */
data class Practice(val company: CompanyId, val usage: UsageReport)

/**
 * The module's window onto the rest of the game. The host implements it from the economy; tests implement it from
 * plain numbers. Every member has a neutral default so a host can supply only what it has.
 */
interface IpContext {
    /** Cash a party could pay out right now. */
    fun cash(owner: IpOwner): Money = Money.ZERO

    fun isActive(company: CompanyId): Boolean = true

    /** Companies that count as computer-controlled and get the planner's attention. */
    fun aiCompanies(): List<CompanyId> = emptyList()

    /** The country a company is based in, where its default filings go. */
    fun homeCountry(company: CompanyId): CountryId? = null

    /** Companies other than [exclude] that know [tech] from their own research. */
    fun independentHolders(tech: TechId, exclude: CompanyId): List<CompanyId> = emptyList()

    /** Technologies the company has researched, in id order. */
    fun knownTechs(company: CompanyId): List<TechId> = emptyList()

    /** People inside [company] who know the [tech] secret. */
    fun knowers(company: CompanyId, tech: TechId): Int = 0

    /** How much of the technology a company's products give away, ppm. */
    fun exposure(company: CompanyId, tech: TechId): Rate = Rate.ZERO

    /** What [company] sold with products using [tech] in the given industries and countries, from [from] up to [to]. */
    fun usage(company: CompanyId, tech: TechId, industries: List<IndustryFamily>, countries: List<CountryId>, from: Tick, to: Tick): UsageReport = UsageReport.ZERO

    /** Every company selling products that use [tech] in [country]. */
    fun practicing(tech: TechId, country: CountryId, from: Tick, to: Tick): List<Practice> = emptyList()

    /** The share of what a company reports honestly to licensors, 1.0 being all of it. */
    fun reportingHonesty(company: CompanyId): Rate = Rate.ONE

    fun publicHealthEmergency(country: CountryId): Boolean = false

    /** Whether the patent's technology is in use by its owner inside the country. */
    fun isWorkedLocally(owner: CompanyId, tech: TechId, country: CountryId): Boolean = false

    /** The share of a field's market a company holds. */
    fun marketShare(company: CompanyId, field: TechField): Rate = Rate.ZERO

    /** What the company spent on research that qualifies for a credit between two ticks. */
    fun qualifyingResearchSpend(company: CompanyId, from: Tick, to: Tick): Money = Money.ZERO

    fun corporateTaxRate(country: CountryId): Rate = Rate.percent(20)

    /** Annual sales the company makes in the industry or field, for valuing what a patent protects. */
    fun annualSalesInField(company: CompanyId, field: TechField): Money = Money.ZERO

    /** Whether the company is going under and selling what it can. */
    fun isDistressed(company: CompanyId): Boolean = false

    /** The profit margin the company earns on what it sells, ppm. */
    fun profitMargin(company: CompanyId): Rate = Rate.percent(12)
}

/** A context that knows nothing and has no money; the neutral choice for tests and offline use. */
object EmptyIpContext : IpContext

/** Cash that has been promised during the current tick, so one pool of money is never spent twice. */
class Wallet(private val ctx: IpContext) {
    private val spent = java.util.TreeMap<IpOwner, Long>()

    fun available(owner: IpOwner): Money = Money(ctx.cash(owner).raw - (spent[owner] ?: 0L)).coerceAtLeast(Money.ZERO)
    fun canPay(owner: IpOwner, amount: Money): Boolean = available(owner) >= amount
    fun reserve(owner: IpOwner, amount: Money) { spent[owner] = (spent[owner] ?: 0L) + amount.raw }

    /** Reserves [amount] if there is enough; false (and no change) otherwise. */
    fun tryReserve(owner: IpOwner, amount: Money): Boolean {
        if (!canPay(owner, amount)) return false
        reserve(owner, amount)
        return true
    }
}

/** The per-call bundle every service method takes: the clock, the host's facts, the cash promised so far and the output. */
class IpRun(val tick: Tick, val ctx: IpContext, val wallet: Wallet, val out: IpOutput)
