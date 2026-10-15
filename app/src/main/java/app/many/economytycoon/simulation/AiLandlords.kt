package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.companies.CompanyStatus
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.world.ZoneType

/**
 * Some computer-run companies speculate in land. They buy cheap unowned plots, sit on them for a while and then
 * list them a little over their appraisal, and they snap up listings that are priced under it.
 */
class AiLandlords(private val e: Economy, private val executor: CommandExecutor) {
    class Report(var bought: Int = 0, var listed: Int = 0, var snapped: Int = 0)

    private val lookEvery = 20L
    private val cashFloor = Money.units(250_000)
    private val maxParcels = 4
    private val holdDays = 120L
    private val markupPpm = 1_100_000L
    private val bargainPpm = 970_000L
    private val maxSpendPpm = 300_000L

    private fun speculators() = e.companies.all().filter { it.kind == CompanyKind.AI && it.status == CompanyStatus.ACTIVE && it.id.value % 3L == 1L }.sortedBy { it.id }

    fun run(tick: Tick): Report {
        val report = Report()
        for (c in speculators()) {
            if ((tick.value + c.id.value) % lookEvery != 0L) continue
            val cash = e.finance.ledger.cash(c.owner)
            val budget = mulDiv((cash - Money.max(cashFloor, e.aiReserve.floor(c.id))).raw, maxSpendPpm, 1_000_000L)
            val held = e.world.land.ownedBy(c.id)
            for (p in held.filter { !it.developed && !e.property.encumbered(it.id) && e.property.heldDays(it.id, tick) >= holdDays }) {
                val ask = Money(mulDiv(e.propertyRunner.appraise(p).raw, markupPpm, 1_000_000L))
                if (executor.execute(Actor.Ai(c.id), Command.ListParcel(c.id, p.id, ask)) is Outcome.Ok) report.listed++
            }
            if (budget <= 0L) continue
            val deal = e.property.listings().filter { it.seller != c.id }
                .filter { l -> val p = e.world.land.get(l.parcel); p != null && l.ask.raw <= mulDiv(e.propertyRunner.appraise(p).raw, bargainPpm, 1_000_000L) && l.ask.raw <= budget }
                .minByOrNull { it.ask.raw }
            if (deal != null && held.size < maxParcels) {
                if (executor.execute(Actor.Ai(c.id), Command.BuyListed(c.id, deal.parcel)) is Outcome.Ok) { report.snapped++; continue }
            }
            if (held.size >= maxParcels) continue
            val city = e.world.citiesOf(e.companies.company(c.id)!!.country).sortedBy { it.id }.firstOrNull() ?: continue
            val pick = e.world.land.available(city.id).filter { it.effectiveZone != ZoneType.PROTECTED && e.propertyRunner.appraise(it).raw in 1..budget }
                .sortedWith(compareBy({ e.propertyRunner.appraise(it).raw / it.area.coerceAtLeast(1) }, { it.id })).firstOrNull() ?: continue
            if (executor.execute(Actor.Ai(c.id), Command.BuyParcel(c.id, pick.id)) is Outcome.Ok) report.bought++
        }
        return report
    }
}
