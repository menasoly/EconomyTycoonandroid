package app.many.economytycoon.simulation

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.RegulationId
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
import app.many.economytycoon.regulation.Fine
import app.many.economytycoon.regulation.Lever
import app.many.economytycoon.regulation.Law
import app.many.economytycoon.regulation.Mood
import app.many.economytycoon.regulation.RegulationEvent
import app.many.economytycoon.regulation.Stance
import app.many.economytycoon.world.Country

/**
 * Runs the political year: governments take office, read the mood of their country, propose and vote laws,
 * put passed laws into force, face elections and inspect factories for pollution.
 */
class RegulationRunner(private val e: Economy) {
    private var seq = 0L

    /** Makes sure every country has a government; harmless to call every day. */
    fun ensureGovernments(tick: Tick) {
        for (country in e.world.countries().sortedBy { it.id }) {
            if (e.regulation.government(country.id) != null) continue
            val random = e.random.stream("regulation.start", country.id.value)
            val stance = Stance.values()[random.nextInt(Stance.values().size)]
            val first = Tick(tick.value + e.regulation.params.electionEvery / 2 + random.nextLong((e.regulation.params.electionEvery / 2).toLong()))
            e.regulation.ensureGovernment(country, stance, first)
        }
    }

    /** How the country's people and industry look today. */
    fun mood(country: CountryId): Mood {
        val pops = e.world.citiesOf(country).map { e.world.population(it.id) }
        val labor = pops.sumOf { it.laborForce }
        val unemployed = pops.sumOf { it.unemployed }
        val people = pops.sumOf { it.total }
        val unemployment = if (labor == 0L) 0L else mulDiv(unemployed, 1_000_000L, labor)
        val happiness = if (people == 0L) 600_000L else pops.sumOf { it.happinessPpm * it.total } / people
        val regions = e.world.regionsOf(country).map { it.id }.toSet()
        val facilities = e.production.facilities().filter { it.region in regions && it.status == FacilityStatus.OPERATING }
        val pollution = if (facilities.isEmpty()) 0L else facilities.sumOf { it.pollutionPpm } / facilities.size
        return Mood(unemployment, happiness, e.world.country(country).stabilityPpm, pollution.coerceAtMost(1_000_000L))
    }

    fun run(tick: Tick): List<RegulationEvent> {
        ensureGovernments(tick)
        val out = ArrayList<RegulationEvent>()
        val p = e.regulation.params
        val countries = e.world.countries().sortedBy { it.id }
        val moods = HashMap<CountryId, Mood>()
        fun moodOf(c: CountryId) = moods.getOrPut(c) { mood(c) }

        for (c in countries) if ((tick.value + c.id.value) % 30L == 0L) e.regulation.updateApproval(c.id, moodOf(c.id))

        for (c in countries) {
            if ((tick.value + c.id.value) % p.proposeEvery != 0L) continue
            val random = e.random.stream("regulation", c.id.value)
            if (random.nextLong(1_000_000L) >= p.proposeChancePpm) continue
            val pick = e.regulation.choose(c, moodOf(c.id), random) ?: continue
            val made = e.regulation.propose(c, pick.first, pick.second, tick)
            if (made is Outcome.Ok) out += RegulationEvent.Proposed(made.value.id, c.id, tick)
        }

        out += e.regulation.vote(tick) { e.random.stream("regulation.vote", it.value) }

        for (law in e.regulation.due(tick)) {
            apply(law)
            out += e.regulation.markEnacted(law, tick)
        }

        val elected = e.regulation.elections(tick, { moodOf(it) }) { e.random.stream("regulation.election", it.value) }
        out += elected
        if (elected.isNotEmpty()) for (ev in elected) moods.remove(ev.country)

        for (c in countries) {
            if ((tick.value + c.id.value) % p.inspectEvery == 0L) out += inspect(c, tick)
        }

        e.regulation.prune(tick)
        return out
    }

    /** Writes the new value of a law's rule into the country, the tax policy and the labour markets. */
    fun apply(law: Law) {
        val country = e.world.country(law.country)
        val changed = law.lever.with(country, law.to)
        e.world.replaceCountry(changed)
        val gov = LedgerOwner.government(law.country)
        when (law.lever) {
            Lever.CORPORATE_TAX -> e.finance.setTaxPolicy(gov, e.finance.taxPolicy(gov).copy(corporateRate = app.many.economytycoon.core.Rate(law.to)))
            Lever.SALES_TAX -> e.finance.setTaxPolicy(gov, e.finance.taxPolicy(gov).copy(salesRate = app.many.economytycoon.core.Rate(law.to)))
            Lever.PAYROLL_TAX -> e.finance.setTaxPolicy(gov, e.finance.taxPolicy(gov).copy(payrollRate = app.many.economytycoon.core.Rate(law.to)))
            Lever.MINIMUM_WAGE -> for (r in e.world.regionsOf(law.country)) e.laborMarkets.setMinimumWage(r.id, Money(law.to * Money.SCALE))
            Lever.TARIFF, Lever.ENVIRONMENT -> Unit
        }
    }

    // ---- inspections ---------------------------------------------------------------------------

    /** The pollution level above which a facility is fined in this country. */
    fun limitFor(country: Country): Long = 1_000_000L - mulDiv(country.environmentalStrictnessPpm, 800_000L, 1_000_000L)

    fun fineFor(f: Facility, country: Country): Money {
        val excess = f.pollutionPpm - limitFor(country)
        if (excess <= 0L) return Money.ZERO
        val cost = e.content.facilityTypeOrNull(f.type)?.buildCost ?: return Money.ZERO
        val raw = mulDiv(mulDiv(cost.raw, excess, 1_000_000L), e.regulation.params.fineRatePpm, 1_000_000L)
        return Money.max(Money(raw), Money.units(10))
    }

    private fun inspect(country: Country, tick: Tick): List<RegulationEvent> {
        val out = ArrayList<RegulationEvent>()
        val regions = e.world.regionsOf(country.id).map { it.id }.toSet()
        val limit = limitFor(country)
        val gov = LedgerOwner.government(country.id)
        for (f in e.production.facilities().sortedBy { it.id }) {
            if (f.region !in regions || f.status != FacilityStatus.OPERATING || f.pollutionPpm <= limit) continue
            val owner = e.companies.company(f.owner) ?: continue
            if (!owner.isActive) continue
            val due = fineFor(f, country)
            val payer = LedgerOwner.company(f.owner)
            val pay = Money.min(due, e.finance.ledger.cash(payer).coerceAtLeast(Money.ZERO))
            if (pay.raw > 0L) {
                e.finance.ledger.post(
                    TransactionDraft(
                        "pollution:${f.id.value}:${seq++}", tick, ReasonCode.FEE,
                        listOf(
                            Entry(AccountRef(payer, Chart.OTHER_EXPENSE), pay), Entry(AccountRef(payer, Chart.CASH), -pay),
                            Entry(AccountRef(gov, Chart.CASH), pay), Entry(AccountRef(gov, Chart.REVENUE_OTHER), -pay),
                        ),
                        "pollution fine",
                    ),
                )
            }
            val earlier = e.regulation.recentFines(f.owner, f.id, tick)
            val close = earlier + 1 >= e.regulation.params.finesToClose
            val fine = Fine(f.owner, f.id, country.id, tick, f.pollutionPpm, limit, pay, close)
            e.regulation.recordFine(fine)
            if (close) e.production.shutdown(f.owner, f.id)
            out += RegulationEvent.Fined(fine)
        }
        return out
    }

    // ---- lobbying ------------------------------------------------------------------------------

    fun lobby(company: CompanyId, law: RegulationId, support: Boolean, amount: Money, tick: Tick): Outcome<Law> {
        val l = e.regulation.law(law) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown law")
        val c = e.companies.company(company) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown company")
        if (!c.isActive) return Outcome.reject(Rules.WRONG_STATE, "company is not active")
        if (!l.isOpen) return Outcome.reject(Rules.WRONG_STATE, "parliament has already voted on that law")
        if (amount < e.regulation.params.minLobby) return Outcome.reject(Rules.INVALID_ARGUMENT, "lobbying needs at least ${e.regulation.params.minLobby}")
        val payer = LedgerOwner.company(company)
        if (e.finance.ledger.cash(payer) < amount) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "not enough cash")
        val gov = LedgerOwner.government(l.country)
        val posted = e.finance.ledger.post(
            TransactionDraft(
                "lobby:${law.value}:${seq++}", tick, ReasonCode.FEE,
                listOf(
                    Entry(AccountRef(payer, Chart.OTHER_EXPENSE), amount), Entry(AccountRef(payer, Chart.CASH), -amount),
                    Entry(AccountRef(gov, Chart.CASH), amount), Entry(AccountRef(gov, Chart.REVENUE_OTHER), -amount),
                ),
                "lobbying",
            ),
        )
        if (posted !is Outcome.Ok) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "payment failed")
        return e.regulation.lobby(law, company, support, amount)
    }

    /** How the lobbying a company has done on open laws would be affected if it gave [amount] more. */
    fun swingWith(law: Law, support: Boolean, amount: Money): Long {
        val s = if (support) law.supportPool + amount else law.supportPool
        val o = if (support) law.opposePool else law.opposePool + amount
        val p = e.regulation.params
        return mulDiv(p.maxLobbySwingPpm, (s - o).raw, (s + o).raw + p.lobbyHalfPoint.raw)
    }
}
