package app.many.economytycoon.macro

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/** Everything the macro layer keeps for one country. */
class CountryMacro(val country: CountryId, params: MacroParams) {
    val bank = CentralBank(country, params)
    val cycle = BusinessCycle(country)
    val gdp = GdpLedger()
    val fiscal = FiscalBook(country)
    internal val snapshots = ArrayList<MacroSnapshot>()
    var trendGdp: Money = Money.ZERO
        internal set

    fun hashInto(h: CanonicalHasher) {
        bank.hashInto(h); cycle.hashInto(h); gdp.hashInto(h); fiscal.hashInto(h); h.putMoney(trendGdp)
        h.putInt(snapshots.size)
        for (s in snapshots) s.hashInto(h)
    }
}

/**
 * Monthly macroeconomic review of every country: closes the national accounts, measures the output gap,
 * lets central banks and the business cycle respond, and settles the public budget.
 */
class MacroService(val params: MacroParams = MacroParams(), private val view: MacroView) {
    private val countries = java.util.TreeMap<CountryId, CountryMacro>()

    private val periodsPerYear: Long get() = (params.ticksPerYear / params.periodTicks).coerceAtLeast(1L)

    fun of(country: CountryId): CountryMacro = countries.getOrPut(country) { CountryMacro(country, params) }
    fun peek(country: CountryId): CountryMacro? = countries[country]
    fun countries(): List<CountryId> = countries.keys.toList()

    fun record(country: CountryId, kind: GdpKind, amount: Money) = of(country).gdp.record(kind, amount)
    fun recordRevenue(country: CountryId, amount: Money) = of(country).fiscal.addRevenue(amount)
    fun recordSpending(country: CountryId, amount: Money) = of(country).fiscal.addSpending(amount)

    fun latest(country: CountryId): MacroSnapshot? = countries[country]?.snapshots?.lastOrNull()
    fun history(country: CountryId): List<MacroSnapshot> = countries[country]?.snapshots?.toList().orEmpty()
    fun policyRatePpm(country: CountryId): Long = countries[country]?.bank?.ratePpm ?: 40_000L
    fun demandShiftPpm(country: CountryId): Long = countries[country]?.cycle?.demandShiftPpm() ?: 1_000_000L

    /** Applies a confidence shock, for events such as crises or booms. */
    fun shock(country: CountryId, confidencePpm: Long) = of(country).cycle.shock(confidencePpm)

    fun isReviewTick(tick: Tick): Boolean = tick.value > 0 && tick.value % params.periodTicks == 0L

    /** Runs a review when [tick] closes a period. Returns the snapshots produced, in country order. */
    fun step(tick: Tick, random: RandomProvider): List<MacroSnapshot> {
        if (!isReviewTick(tick)) return emptyList()
        val out = ArrayList<MacroSnapshot>()
        for (id in view.countries().sorted()) {
            val c = of(id)
            val gdp = c.gdp.close()
            c.gdp.trim(params.historyPeriods)
            if (c.trendGdp.raw == 0L) c.trendGdp = gdp
            else c.trendGdp = Money(c.trendGdp.raw + mulDiv(gdp.raw - c.trendGdp.raw, params.trendSmoothingPpm, 1_000_000L))
            val gap = if (c.trendGdp.raw == 0L) 0L else mulDiv(gdp.raw - c.trendGdp.raw, 1_000_000L, c.trendGdp.raw)
            val yearAgo = c.gdp.ago(periodsPerYear.toInt() + 1)
            val growth = if (yearAgo == null || yearAgo.raw <= 0L) null else mulDiv(gdp.raw - yearAgo.raw, 1_000_000L, yearAgo.raw)

            val inflation = view.inflationPpm(id)
            val unemployment = view.unemploymentPpm(id)
            c.bank.review(PolicyInputs(inflation, gap, unemployment))
            c.cycle.step(random.stream("macro", id.value), growth ?: gap, c.bank.ratePpm, params.neutralRealRatePpm + params.inflationTargetPpm)

            val balance = c.fiscal.close(c.bank.ratePpm, periodsPerYear)
            val annualGdp = Money(gdp.raw * periodsPerYear)
            val snap = MacroSnapshot(
                tick, id, gdp, growth, gap, inflation, unemployment, c.bank.ratePpm, c.cycle.phase,
                c.cycle.confidencePpm, c.cycle.demandShiftPpm(), c.fiscal.debtToGdpPpm(annualGdp), balance,
            )
            c.snapshots += snap
            while (c.snapshots.size > params.historyPeriods) c.snapshots.removeAt(0)
            out += snap
        }
        return out
    }

    /** Headline tax rates an AI government should move to given its latest budget. */
    fun proposeTaxes(country: CountryId, corporatePpm: Long, salesPpm: Long): TaxProposal {
        val c = countries[country] ?: return TaxProposal(corporatePpm, salesPpm)
        val last = c.snapshots.lastOrNull() ?: return TaxProposal(corporatePpm, salesPpm)
        val annual = Money(last.gdp.raw * periodsPerYear)
        return FiscalRule.recommend(c.fiscal.lastBalance, annual, last.debtToGdpPpm, corporatePpm, salesPpm)
    }

    internal fun restore(c: CountryMacro) { countries[c.country] = c }
    internal fun clearAll() { countries.clear() }

    fun hashInto(h: CanonicalHasher) {
        h.section("macro")
        h.putInt(countries.size)
        for (c in countries.values) c.hashInto(h)
    }
}
