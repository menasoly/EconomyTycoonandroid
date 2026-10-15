package app.many.economytycoon.simulation

import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.GrantId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.AccountRef
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.Entry
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.finance.ReasonCode
import app.many.economytycoon.finance.TransactionDraft
import app.many.economytycoon.grants.Application
import app.many.economytycoon.grants.GrantEvent
import app.many.economytycoon.grants.GrantKind
import app.many.economytycoon.grants.GrantProgram
import app.many.economytycoon.production.FacilityStatus

/**
 * Runs the grant cycle: governments announce programmes for jobs or investment, companies apply, the growth is
 * counted when the programme ends and the treasury pays for it, as far as its cash goes.
 */
class GrantRunner(private val e: Economy) {
    private var seq = 0L

    /** Industries a facilities grant can favour: those with at least one facility anyone can build from the start. */
    fun favourable(): List<IndustryFamily> =
        e.content.allFacilityTypes.filter { it.requiredTech == null && it.extracts == null && it.maxLines > 0 }.map { it.family }.distinct().sortedBy { it.ordinal }

    fun budgetFor(country: CountryId): Money = e.tenderRunner.dailyPublicSpending(country) * e.grants.params.budgetDays.toLong()

    // ---- counting ------------------------------------------------------------------------------

    /** People the company employs in the country. */
    fun jobsIn(company: CompanyId, country: CountryId): Long {
        val regions = e.world.regionsOf(country).map { it.id }.toSet()
        return e.workforce.cohortsOf(company).filter { it.key.region in regions }.sumOf { it.headcount }
    }

    /** Facilities of the favoured industry the company has in the country, built or being built. */
    fun facilitiesIn(company: CompanyId, country: CountryId, family: IndustryFamily): Long {
        val regions = e.world.regionsOf(country).map { it.id }.toSet()
        return e.production.facilitiesOf(company).count { f ->
            f.status != FacilityStatus.DEMOLISHED && f.region in regions && e.content.facilityTypeOrNull(f.type)?.family == family
        }.toLong()
    }

    fun count(kind: GrantKind, family: IndustryFamily?, company: CompanyId, country: CountryId): Long = when (kind) {
        GrantKind.JOBS -> jobsIn(company, country)
        GrantKind.FACILITIES -> if (family == null) 0L else facilitiesIn(company, country, family)
    }

    fun countFor(g: GrantProgram, company: CompanyId): Long = count(g.kind, g.family, company, g.country)

    // ---- applying ------------------------------------------------------------------------------

    fun apply(company: CompanyId, program: GrantId, tick: Tick): Outcome<Application> {
        val g = e.grants.get(program) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown grant programme")
        val c = e.companies.company(company)
        if (c == null || !c.isActive) return Outcome.reject(Rules.WRONG_STATE, "only a trading company can apply")
        return e.grants.apply(program, company, countFor(g, company), tick)
    }

    fun withdraw(company: CompanyId, program: GrantId): Outcome<Unit> = e.grants.withdraw(program, company)

    // ---- the day -------------------------------------------------------------------------------

    fun run(tick: Tick): List<GrantEvent> {
        val events = ArrayList<GrantEvent>()
        events += announce(tick)
        events += e.grants.closeApplications(tick)
        events += e.grants.settle(tick, { g, a -> measureForSettlement(g, a) }, { g, a, owed -> payOut(g, a, owed, tick) })
        e.grants.prune(tick)
        return events
    }

    private fun measureForSettlement(g: GrantProgram, a: Application): Long {
        val c = e.companies.company(a.company)
        return if (c == null || !c.isActive) a.baseline else countFor(g, a.company)
    }

    private fun announce(tick: Tick): List<GrantEvent> {
        val p = e.grants.params
        val out = ArrayList<GrantEvent>()
        val families = favourable()
        for (country in e.world.countries().sortedBy { it.id }) {
            if ((tick.value + country.id.value) % p.issueEvery != 0L) continue
            val random = e.random.stream("grants", country.id.value)
            if (random.nextLong(1_000_000L) >= p.issueChancePpm) continue
            val kind = if (families.isEmpty() || random.nextLong(2L) == 0L) GrantKind.JOBS else GrantKind.FACILITIES
            val family = if (kind == GrantKind.FACILITIES) families[random.nextInt(families.size)] else null
            val budget = Money.units(p.minBudgetUnits + random.nextLong(p.maxBudgetUnits - p.minBudgetUnits + 1))
            if (e.grants.committed(country.id) + budget > budgetFor(country.id)) continue
            val rate = Money.units(if (kind == GrantKind.JOBS) p.jobRateUnits else p.facilityRateUnits)
            val issued = e.grants.announce(country.id, kind, family, rate, Money.units(p.capUnits), budget, tick)
            if (issued is Outcome.Ok) out += GrantEvent.Announced(issued.value.id, country.id, kind, tick)
        }
        return out
    }

    /** Pays from the treasury as far as its cash goes. The grant is income to the company and spending to the state. */
    private fun payOut(g: GrantProgram, a: Application, owed: Money, tick: Tick): Money {
        val c = e.companies.company(a.company)
        if (c == null || !c.isActive) return Money.ZERO
        val gov = LedgerOwner.government(g.country)
        val pay = Money.min(owed, e.finance.ledger.cash(gov).coerceAtLeast(Money.ZERO))
        if (pay.raw <= 0L) return Money.ZERO
        val company = LedgerOwner.company(a.company)
        val posted = e.finance.ledger.post(
            TransactionDraft(
                "grant:${g.id.value}:${a.company.value}:${seq++}", tick, ReasonCode.SUBSIDY,
                listOf(
                    Entry(AccountRef(gov, Chart.OTHER_EXPENSE), pay), Entry(AccountRef(gov, Chart.CASH), -pay),
                    Entry(AccountRef(company, Chart.CASH), pay), Entry(AccountRef(company, Chart.REVENUE_OTHER), -pay),
                ),
                "grant payment",
            ),
        )
        return if (posted is Outcome.Ok) pay else Money.ZERO
    }

    fun forget(company: CompanyId) { e.grants.endAllOf(company) }
}
