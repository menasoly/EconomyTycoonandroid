package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.companies.CompanyStatus
import app.many.economytycoon.contracts.ContractSide
import app.many.economytycoon.contracts.SupplyContract
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.production.Facility
import app.many.economytycoon.production.FacilityStatus

/**
 * How computer-run companies deal with supply contracts: they answer offers made to them and, now and then,
 * offer surplus stock to companies that are trying to buy it. Every choice is a pure function of the state,
 * so the same game always plays out the same way.
 */
class AiContracts(private val e: Economy, private val executor: CommandExecutor) {
    class Report(var accepted: Int = 0, var declined: Int = 0, var proposed: Int = 0)

    /** How often, in ticks, a company looks for a customer. */
    private val proposalEvery = 20L
    private val acceptBelowReferencePpm = 1_050_000L
    private val sellAboveReferencePpm = 900_000L

    fun run(tick: Tick): Report {
        val report = Report()
        val ai = e.companies.all().filter { it.kind == CompanyKind.AI && it.status == CompanyStatus.ACTIVE }.map { it.id }.sorted()
        for (id in ai) for (offer in e.contracts.proposalsFor(id)) respond(id, offer, tick, report)
        for (id in ai) if ((tick.value + id.value) % proposalEvery == 0L) offer(id, tick, report)
        return report
    }

    private fun operating(company: CompanyId): List<Facility> =
        e.production.facilitiesOf(company).filter { it.status == FacilityStatus.OPERATING }.sortedBy { it.id }

    private fun reference(good: GoodId): Money = e.content.goodOrNull(good)?.referencePrice?.perUnit ?: Money.ZERO

    private fun respond(id: CompanyId, offer: SupplyContract, tick: Tick, report: Report) {
        val good = offer.terms.good
        val ref = reference(good)
        val facilities = operating(id)
        val choice: Facility? = if (offer.proposerSide == ContractSide.BUYER) {
            // The AI would be the seller: it needs the stock on hand and a fair price.
            val fair = ref.raw <= 0L || offer.terms.unitPrice.perUnit.raw >= mulDiv(ref.raw, sellAboveReferencePpm, 1_000_000L)
            if (!fair) null else facilities.filter { it.inventory.available(good) >= offer.terms.quantity }.maxByOrNull { it.inventory.available(good).raw }
        } else {
            // The AI would be the buyer: it needs room for a couple of deliveries and a fair price.
            val fair = ref.raw <= 0L || offer.terms.unitPrice.perUnit.raw <= mulDiv(ref.raw, acceptBelowReferencePpm, 1_000_000L)
            if (!fair) null else facilities.filter { it.inventory.room(good).raw >= offer.terms.quantity.raw * 2 }.maxByOrNull { it.inventory.room(good).raw }
        }
        val actor = Actor.Ai(id)
        if (choice != null && executor.execute(actor, Command.AcceptContract(id, offer.id, choice.id)) is Outcome.Ok) {
            report.accepted++
        } else {
            executor.execute(actor, Command.DeclineContract(id, offer.id))
            report.declined++
        }
    }

    private fun offer(id: CompanyId, tick: Tick, report: Report) {
        if (e.contracts.proposalsBy(id).isNotEmpty()) return
        if (e.contracts.activeOf(id).size >= e.contracts.params.maxActivePerCompany / 2) return
        val stocked = operating(id).flatMap { f -> f.inventory.goods().map { g -> Triple(f, g, f.inventory.available(g)) } }
            .filter { (_, g, q) -> q.raw >= Quantity.units(100).raw && reference(g).raw > 0L }
            .sortedWith(compareByDescending<Triple<Facility, GoodId, Quantity>> { it.third.raw }.thenBy { it.second })
        val (facility, good, available) = stocked.firstOrNull() ?: return
        val customers = e.companies.all()
            .filter { it.id != id && it.status == CompanyStatus.ACTIVE }
            .filter { c -> e.companies.policies.buyRules(c.id).any { it.good == good && it.enabled } }
            .sortedBy { it.id }
        if (customers.isEmpty()) return
        val customer = customers[((tick.value / proposalEvery) % customers.size).toInt()]
        if (e.contracts.proposalsFor(customer.id).any { it.proposer == id && it.terms.good == good }) return
        val rule = e.companies.policies.buyRules(customer.id).first { it.good == good && it.enabled }
        val qty = Quantity(minOf(available.raw / 4, rule.target.raw / 2).coerceAtLeast(Quantity.units(1).raw))
        val price = Money(mulDiv(reference(good).raw, 1_000_000L, 1_000_000L))
        val r = executor.execute(Actor.Ai(id), Command.ProposeContract(id, customer.id, ContractSide.SELLER, facility.id, good, qty, price, 5, 12))
        if (r is Outcome.Ok) report.proposed++
    }
}
