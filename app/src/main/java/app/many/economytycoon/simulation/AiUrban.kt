package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.companies.CompanyStatus
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.urban.CityPlan
import app.many.economytycoon.urban.PetitionKind
import app.many.economytycoon.urban.PetitionStatus
import app.many.economytycoon.urban.UrbanCommand
import app.many.economytycoon.urban.ZoneUse
import app.many.economytycoon.urban.Zoning

/**
 * Computer-run companies deal with the city councils where they do most of their business. Once a month a company that
 * can afford it looks at the city: if it has a petition waiting it puts a little money behind it, and now and then it asks
 * for a district of old housing or industry to be rezoned for mixed use. Each company acts in at most one city and files
 * at most one petition at a time, on its own day of the month.
 */
class AiUrban(private val e: Economy, private val executor: CommandExecutor) {
    class Report(var petitions: Int = 0, var lobbying: Int = 0)

    private val lobbyShareBps = 40L
    private val lobbyCap = Money.units(20_000)
    private val lobbyFloor = Money.units(500)
    private val cashCover = 15L
    private val filingCover = 4L
    private val filingEveryMonths = 6L

    fun run(tick: Tick): Report {
        val report = Report()
        if (!e.urbanRunner.active) return report
        val ai = e.companies.all().filter { it.kind == CompanyKind.AI && it.status == CompanyStatus.ACTIVE }.map { it.id }.sorted()
        for (id in ai) {
            if ((tick.value + id.value) % 30L != 5L) continue
            val city = homeCity(id) ?: continue
            val plan = e.urban.plan(city) ?: continue
            if (!lobby(id, plan, tick, report)) file(id, plan, tick, report)
        }
        return report
    }

    /** The city where the company has most working facilities (the lowest id on a tie), else the city of its headquarters. */
    private fun homeCity(id: CompanyId): CityId? {
        val counts = java.util.TreeMap<CityId, Int>()
        for (f in e.production.facilitiesOf(id)) {
            val city = f.city ?: continue
            if (f.isOperating()) counts[city] = (counts[city] ?: 0) + 1
        }
        return counts.entries.maxWithOrNull(compareBy({ it.value }, { -it.key.value }))?.key ?: e.companies.company(id)?.hq
    }

    private fun cash(id: CompanyId): Money = e.finance.ledger.cash(LedgerOwner.company(id))

    private fun lobby(id: CompanyId, plan: CityPlan, tick: Tick, report: Report): Boolean {
        val waiting = plan.petitions.open().firstOrNull { it.applicant == id && (it.status == PetitionStatus.FILED || it.status == PetitionStatus.HEARING) } ?: return false
        if (waiting.lobbied.raw > 0L) return true
        val cash = cash(id)
        val amount = Money.min(lobbyCap, Money(mulDiv(cash.raw.coerceAtLeast(0L), lobbyShareBps, 10_000L)))
        if (amount < lobbyFloor || cash < amount * cashCover) return true
        val r = executor.execute(Actor.Ai(id), Command.Urban(id, UrbanCommand.LobbyPetition(plan.city, waiting.id, id, null, amount)))
        if (r is Outcome.Ok) report.lobbying++
        return true
    }

    private fun file(id: CompanyId, plan: CityPlan, tick: Tick, report: Report) {
        if ((tick.value / 30L + id.value) % filingEveryMonths != 0L) return
        if (plan.petitions.open().size >= e.urban.params.maxOpenPetitionsPerCity) return
        // The cheapest district to rezone, the lowest number on a tie.
        val params = e.urban.params
        val district = plan.districts.filter { d ->
            !d.heritage && plan.petitions.openFor(d.index) == null &&
                (d.zone == ZoneUse.RESIDENTIAL || d.zone == ZoneUse.INDUSTRIAL) && Zoning.allowed(d.zone, ZoneUse.MIXED)
        }.minWithOrNull(compareBy({ Zoning.rezoneCost(it.landSqm, it.zone, ZoneUse.MIXED, params).raw }, { it.index })) ?: return
        val fee = Zoning.rezoneCost(district.landSqm, district.zone, ZoneUse.MIXED, params)
        if (cash(id) < fee * filingCover) return
        val cmd = UrbanCommand.FilePetition(plan.city, district.index, PetitionKind.REZONE, ZoneUse.MIXED, 0, 0, id)
        val r = executor.execute(Actor.Ai(id), Command.Urban(id, cmd))
        if (r is Outcome.Ok) report.petitions++
    }
}
