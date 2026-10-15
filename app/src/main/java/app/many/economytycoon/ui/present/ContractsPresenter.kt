package app.many.economytycoon.ui.present

import app.many.economytycoon.companies.CompanyStatus
import app.many.economytycoon.contracts.ContractSide
import app.many.economytycoon.contracts.ContractStatus
import app.many.economytycoon.contracts.SupplyContract
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.production.Facility
import app.many.economytycoon.production.FacilityStatus
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.session.GameSession

/** A facility the player could use for a contract, with how well it suits the deal. */
data class ContractFacility(val id: FacilityId, val name: String, val detail: String, val suitable: Boolean)

data class ContractCard(
    val id: Long,
    val title: String,
    val terms: String,
    val price: String,
    val versusMarket: String,
    val versusTone: Tone,
    val role: String,
    val status: String,
    val statusTone: Tone,
    val progress: Double,
    val progressText: String,
    val next: String,
    val paid: String,
    val penalties: String?,
    val cancelFee: String?,
    val good: String,
    val partner: CompanyId,
    val canAccept: Boolean,
    val canDecline: Boolean,
    val canWithdraw: Boolean,
    val canCancel: Boolean,
    val facilities: List<ContractFacility>,
)

data class ContractsModel(
    val tiles: List<Tile>,
    val incoming: List<ContractCard>,
    val outgoing: List<ContractCard>,
    val running: List<ContractCard>,
    val history: List<ContractCard>,
)

data class PartnerChoice(val id: CompanyId, val name: String)
data class GoodChoice(val id: GoodId, val name: String, val reference: String)

/** What the "new contract" form offers to choose from. */
data class ContractSetup(val partners: List<PartnerChoice>, val facilities: List<ContractFacility>, val goods: List<GoodChoice>, val limits: String)

object ContractsPresenter {
    fun statusLabel(s: ContractStatus): String = when (s) {
        ContractStatus.PROPOSED -> "Offer"
        ContractStatus.ACTIVE -> "Running"
        ContractStatus.FULFILLED -> "Completed"
        ContractStatus.CANCELLED -> "Cancelled"
        ContractStatus.DECLINED -> "Declined"
        ContractStatus.EXPIRED -> "Lapsed"
        ContractStatus.BREACHED -> "Broken"
    }

    fun statusTone(s: ContractStatus): Tone = when (s) {
        ContractStatus.PROPOSED -> Tone.INFO
        ContractStatus.ACTIVE -> Tone.GOOD
        ContractStatus.FULFILLED -> Tone.GOOD
        ContractStatus.BREACHED -> Tone.BAD
        ContractStatus.CANCELLED, ContractStatus.DECLINED, ContractStatus.EXPIRED -> Tone.MUTED
    }

    private fun facilityName(session: GameSession, f: Facility): String =
        f.name.ifBlank { session.economy.content.facilityTypeOrNull(f.type)?.name ?: "Facility ${f.id.value}" }

    private fun goodName(session: GameSession, good: GoodId): String = session.economy.content.goodOrNull(good)?.name ?: Formatting.title(good.key)

    /** Facilities of [company] that could take part in a deal for [good] on the given [side]. */
    fun facilitiesFor(session: GameSession, company: CompanyId, good: GoodId?, side: ContractSide, quantity: Quantity?): List<ContractFacility> {
        val e = session.economy
        return e.production.facilitiesOf(company).filter { it.status == FacilityStatus.OPERATING }.sortedBy { it.id }.map { f ->
            val name = facilityName(session, f)
            if (good == null) return@map ContractFacility(f.id, name, "", true)
            if (side == ContractSide.SELLER) {
                val have = f.inventory.available(good)
                ContractFacility(f.id, name, "${Formatting.quantity(have)} on hand", quantity == null || have >= quantity)
            } else {
                val room = f.inventory.room(good)
                ContractFacility(f.id, name, "room for ${Formatting.quantity(room)}", quantity == null || room >= quantity)
            }
        }
    }

    fun card(session: GameSession, c: SupplyContract, me: CompanyId): ContractCard {
        val e = session.economy
        val money = session.settings::money
        val iSell = c.seller == me
        val partner = c.other(me)
        val partnerName = e.companies.company(partner)?.name ?: "Unknown company"
        val good = goodName(session, c.terms.good)
        val ref = e.content.goodOrNull(c.terms.good)?.referencePrice?.perUnit
        val pct = if (ref == null || ref.raw <= 0L) 0L else (c.terms.unitPrice.perUnit.raw - ref.raw) * 1_000_000L / ref.raw
        val favourable = if (iSell) pct >= 0 else pct <= 0
        val incoming = c.isOpen && c.counterparty == me
        val total = c.terms.deliveries
        val tick = e.tick
        val next = when {
            c.isActive && c.nextDue != null -> {
                val due = c.nextDue!!.value - tick.value
                if (due <= 0) "next delivery today" else "next delivery in " + Formatting.duration(due)
            }
            c.isOpen -> "open for ${Formatting.duration((c.proposedAt.value + e.contracts.params.proposalLifetime - tick.value).coerceAtLeast(0L))}"
            else -> c.endedAt?.let { "ended " + Formatting.date(it) } ?: ""
        }
        val needQuantity = c.terms.quantity
        val myFacilities = if (incoming) facilitiesFor(session, me, c.terms.good, if (iSell) ContractSide.SELLER else ContractSide.BUYER, needQuantity) else emptyList()
        return ContractCard(
            id = c.id.value,
            title = (if (iSell) "Sell to " else "Buy from ") + partnerName,
            terms = "${Formatting.quantity(c.terms.quantity)} of $good every ${Formatting.plural(c.terms.intervalTicks.toLong(), "day")}, ${Formatting.plural(total.toLong(), "delivery")}",
            price = "${money(c.terms.unitPrice.perUnit)} each · ${money(c.terms.deliveryValue)} a delivery",
            versusMarket = if (ref == null) "" else Formatting.signedPercentPpm(pct) + " vs. usual price",
            versusTone = if (ref == null) Tone.MUTED else if (favourable) Tone.GOOD else Tone.WARN,
            role = if (iSell) "You sell" else "You buy",
            status = statusLabel(c.status), statusTone = statusTone(c.status),
            progress = if (total == 0) 0.0 else c.delivered.toDouble() / total,
            progressText = "${c.delivered} of $total delivered" + if (c.missed > 0) ", ${c.missed} missed" else "",
            next = next,
            paid = money(c.paid),
            penalties = if (c.penalties.raw > 0L) money(c.penalties) else null,
            cancelFee = if (c.isActive) money(e.contracts.cancelFee(c)) else null,
            good = c.terms.good.key,
            partner = partner,
            canAccept = incoming && myFacilities.any { it.suitable },
            canDecline = incoming,
            canWithdraw = c.isOpen && c.proposer == me,
            canCancel = c.isActive,
            facilities = myFacilities,
        )
    }

    fun build(session: GameSession): ContractsModel? {
        val me = session.selectedCompany ?: return null
        val e = session.economy
        val mine = e.contracts.of(me)
        fun cards(list: List<SupplyContract>) = list.sortedByDescending { it.id.value }.map { card(session, it, me) }
        val incoming = mine.filter { it.isOpen && it.counterparty == me }
        val outgoing = mine.filter { it.isOpen && it.proposer == me }
        val running = mine.filter { it.isActive }
        val history = mine.filter { !it.isOpen && !it.isActive }.sortedByDescending { it.endedAt?.value ?: 0L }.take(30)
        val perDelivery = running.sumOf { (if (it.seller == me) 1L else -1L) * it.terms.deliveryValue.raw / it.terms.intervalTicks.coerceAtLeast(1) }
        val money = session.settings::money
        val tiles = listOf(
            Tile("Running", running.size.toString()),
            Tile("Offers to answer", incoming.size.toString(), if (incoming.isEmpty()) Tone.NEUTRAL else Tone.INFO),
            Tile("Daily flow", money(Money(perDelivery)), Tones.bySign(perDelivery), "sales minus purchases under contract"),
            Tile("Broken", mine.count { it.status == ContractStatus.BREACHED }.toString(), if (mine.any { it.status == ContractStatus.BREACHED }) Tone.WARN else Tone.NEUTRAL),
        )
        return ContractsModel(tiles, cards(incoming), cards(outgoing), cards(running), history.map { card(session, it, me) })
    }

    fun setup(session: GameSession): ContractSetup? {
        val me = session.selectedCompany ?: return null
        val e = session.economy
        val partners = e.companies.all().filter { it.id != me && it.status == CompanyStatus.ACTIVE }.sortedBy { it.name }.map { PartnerChoice(it.id, it.name) }
        val goods = e.content.allGoods.sortedBy { it.name }.map { GoodChoice(it.id, it.name, session.settings.money(it.referencePrice.perUnit)) }
        val p = e.contracts.params
        val limits = "Deliveries ${p.minInterval} to ${p.maxInterval} days apart, up to ${p.maxDeliveries} in all. A missed delivery costs the seller ${Formatting.percentPpm(p.shortfallPenaltyPpm)} of its value; leaving a running contract costs ${Formatting.percentPpm(p.cancelFeePpm)} of one delivery."
        return ContractSetup(partners, facilitiesFor(session, me, null, ContractSide.SELLER, null), goods, limits)
    }
}
