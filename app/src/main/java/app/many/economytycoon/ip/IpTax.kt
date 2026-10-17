package app.many.economytycoon.ip

import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/** Tax treatment of research and of income from patents. Pure arithmetic over the country's regime. */
object IpTaxRules {
    /** Credit on a year's qualifying research spending. */
    fun researchCredit(regime: LegalRegime, spend: Money): Money = Money(mulDiv(spend.raw.coerceAtLeast(0L), regime.rdCreditPpm, 1_000_000L))

    /** Tax saved on [income] from owned patents where a patent box applies; zero when the box rate is no better than the ordinary rate. */
    fun patentBoxRelief(regime: LegalRegime, ordinary: Rate, income: Money): Money {
        val box = regime.patentBoxRatePpm ?: return Money.ZERO
        val saving = ordinary.ppm - box
        if (saving <= 0L || income.raw <= 0L) return Money.ZERO
        return Money(mulDiv(income.raw, saving, 1_000_000L))
    }
}

/**
 * The yearly research credit. At the start of each year it looks back over the year just ended, works out the credit
 * from what the host says qualified, and returns a payment from the government. The year it paid for is remembered so
 * a replayed tick cannot pay twice.
 */
class IpTax(private val state: IpState, private val office: PatentOffice, val params: IpParams = IpParams()) {
    private fun yearOf(tick: Tick): Long = tick.value / Calendar.DAYS_PER_YEAR

    /** Companies the module knows about: those that hold or lean on patents, secrets or have set a policy. */
    fun knownCompanies(): List<CompanyId> {
        val set = java.util.TreeSet<CompanyId>()
        for (p in state.patentMap.values) { p.owner.companyOrNull()?.let { set += it }; set += p.inventor }
        for (s in state.secretMap.values) set += s.owner
        for (c in state.policyMap.keys) set += c
        for (l in state.licenseMap.values) set += l.licensee
        return set.toList()
    }

    fun creditFor(run: IpRun, company: CompanyId, year: Long): Money {
        val country = run.ctx.homeCountry(company) ?: return Money.ZERO
        val from = Tick(year * Calendar.DAYS_PER_YEAR)
        val to = Tick((year + 1) * Calendar.DAYS_PER_YEAR)
        return IpTaxRules.researchCredit(office.regimeOf(country), run.ctx.qualifyingResearchSpend(company, from, to))
    }

    fun step(run: IpRun) {
        if (run.tick.value % Calendar.DAYS_PER_YEAR != 0L || run.tick.value == 0L) return
        val finished = yearOf(run.tick) - 1
        for (c in knownCompanies()) {
            if (!run.ctx.isActive(c)) continue
            val paid = state.creditYearMap[c]
            if (paid != null && paid >= finished) continue
            val country: CountryId = run.ctx.homeCountry(c) ?: continue
            val credit = creditFor(run, c, finished)
            state.creditYearMap[c] = finished
            if (credit.raw <= 0L) continue
            run.out.charge(run.tick, ChargeKind.TAX_CREDIT, IpLedger.government(country), IpLedger.of(c), credit, "research credit for year ${finished + 1}", c.value.toString())
            run.out.event(IpEvent.TaxCreditPaid(c, credit, run.tick))
        }
    }

    /** Tax saved by a company on what it earned from its patents between two ticks. */
    fun patentBoxSaving(run: IpRun, company: CompanyId, income: Money): Money {
        val country = run.ctx.homeCountry(company) ?: return Money.ZERO
        return IpTaxRules.patentBoxRelief(office.regimeOf(country), run.ctx.corporateTaxRate(country), income)
    }
}
