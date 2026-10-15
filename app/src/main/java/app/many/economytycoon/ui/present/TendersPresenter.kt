package app.many.economytycoon.ui.present

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.TenderId
import app.many.economytycoon.production.Facility
import app.many.economytycoon.production.FacilityStatus
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.session.GameSession
import app.many.economytycoon.tenders.Tender
import app.many.economytycoon.tenders.TenderStatus

data class BidFacility(val id: FacilityId, val name: String, val stock: String, val enough: Boolean, val cost: String)

data class TenderCard(
    val id: TenderId,
    val title: String,
    val buyer: String,
    val quantity: String,
    val ceiling: String,
    val value: String,
    val timing: String,
    val status: String,
    val statusTone: Tone,
    val yourBid: String?,
    val leadingBid: String?,
    val bids: Int,
    val winning: Boolean,
    val progress: Double,
    val progressText: String?,
    val canBid: Boolean,
    val canWithdraw: Boolean,
    val reference: String,
)

data class TendersModel(
    val tiles: List<Tile>,
    val open: List<TenderCard>,
    val won: List<TenderCard>,
    val history: List<TenderCard>,
    val standing: String?,
)

object TendersPresenter {
    fun statusLabel(s: TenderStatus): String = when (s) {
        TenderStatus.OPEN -> "Open"
        TenderStatus.AWARDED -> "Awarded"
        TenderStatus.DELIVERED -> "Delivered"
        TenderStatus.FAILED -> "Failed"
        TenderStatus.NO_BIDS -> "No bids"
    }

    fun statusTone(s: TenderStatus): Tone = when (s) {
        TenderStatus.OPEN -> Tone.INFO
        TenderStatus.AWARDED -> Tone.WARN
        TenderStatus.DELIVERED -> Tone.GOOD
        TenderStatus.FAILED -> Tone.BAD
        TenderStatus.NO_BIDS -> Tone.MUTED
    }

    private fun facilityName(session: GameSession, f: Facility) = f.name.ifBlank { session.economy.content.facilityTypeOrNull(f.type)?.name ?: "Facility ${f.id.value}" }

    fun card(session: GameSession, t: Tender, me: CompanyId): TenderCard {
        val e = session.economy
        val money = session.settings::money
        val now = session.tick.value
        val good = e.content.goodOrNull(t.good)?.name ?: Formatting.title(t.good.key)
        val country = e.world.countries().firstOrNull { it.id == t.country }?.name ?: "Government"
        val mine = t.bidOf(me)
        val lead = t.leadingBid()
        val winner = t.winner
        val timing = when (t.status) {
            TenderStatus.OPEN -> "Bidding closes in ${Formatting.plural((t.closesAt.value - now).coerceAtLeast(0L), "day")}"
            TenderStatus.AWARDED -> "Deliver within ${Formatting.plural((t.deliverBy.value - now).coerceAtLeast(0L), "day")}"
            else -> t.endedAt?.let { "Ended ${Formatting.date(it)}" } ?: ""
        }
        val facility = t.winnerFacility?.let { e.production.facility(it) }
        val have = if (t.isAwarded && winner == me && facility != null) facility.inventory.available(t.good) else null
        val progress = if (have != null && t.quantity.raw > 0L) (have.raw.toDouble() / t.quantity.raw).coerceIn(0.0, 1.0) else 0.0
        val ref = e.content.goodOrNull(t.good)?.referencePrice?.perUnit
        return TenderCard(
            id = t.id, title = "$good for $country", buyer = country, quantity = Formatting.quantity(t.quantity), ceiling = money(t.ceiling) + " max a unit",
            value = if (t.isOpen) "up to " + money(t.ceilingValue) else money(t.awardedValue), timing = timing,
            status = statusLabel(t.status), statusTone = statusTone(t.status),
            yourBid = mine?.let { money(it.unitPrice) + " a unit" }, leadingBid = lead?.let { money(it.unitPrice) + " a unit" }, bids = t.bids.size,
            winning = t.isOpen && mine != null && lead?.company == me,
            progress = progress, progressText = have?.let { "${Formatting.quantity(it)} of ${Formatting.quantity(t.quantity)} in stock" },
            canBid = t.isOpen && !e.tenders.isBarred(me, session.tick), canWithdraw = t.isOpen && mine != null,
            reference = ref?.let { "Market price ${money(it)}" } ?: "",
        )
    }

    fun build(session: GameSession): TendersModel? {
        val me = session.selectedCompany ?: return null
        val e = session.economy
        val money = session.settings::money
        val open = e.tenders.open().sortedBy { it.closesAt }
        val won = e.tenders.awardedTo(me).sortedBy { it.deliverBy }
        val history = e.tenders.history().filter { it.winner == me || it.bidOf(me) != null }.sortedByDescending { it.endedAt?.value ?: 0L }.take(30)
        val record = e.tenders.wonBy(me)
        val delivered = record.filter { it.status == TenderStatus.DELIVERED }
        val earned = delivered.fold(Money.ZERO) { a, t -> a + t.awardedValue }
        val failures = e.tenders.failuresOf(me, session.tick)
        val tiles = listOf(
            Tile("Open tenders", open.size.toString(), note = "inviting bids"),
            Tile("Your bids", e.tenders.biddingBy(me).size.toString(), note = "waiting"),
            Tile("To deliver", won.size.toString(), if (won.isNotEmpty()) Tone.WARN else Tone.NEUTRAL),
            Tile("Earned", money(earned), Tones.bySign(earned.raw), "from ${Formatting.plural(delivered.size.toLong(), "tender")}"),
        )
        val standing = when {
            e.tenders.isBarred(me, session.tick) -> "You are barred from bidding after $failures failed deliveries."
            failures > 0 -> "$failures missed ${if (failures == 1) "delivery" else "deliveries"} on record; ${e.tenders.params.barAfterFailures} bars you from bidding."
            else -> null
        }
        return TendersModel(
            tiles, open.map { card(session, it, me) }, won.map { card(session, it, me) },
            history.map { card(session, it, me) }, standing,
        )
    }

    /** Facilities that could deliver this tender, best stocked first. */
    fun facilitiesFor(session: GameSession, tender: TenderId): List<BidFacility> {
        val me = session.selectedCompany ?: return emptyList()
        val e = session.economy
        val t = e.tenders.get(tender) ?: return emptyList()
        val money = session.settings::money
        return e.production.facilitiesOf(me).filter { it.status != FacilityStatus.DEMOLISHED }.sortedWith(
            compareByDescending<Facility> { it.inventory.available(t.good).raw }.thenBy { it.id },
        ).map {
            val have = it.inventory.available(t.good)
            val cost = it.inventory.unitCost(t.good)
            BidFacility(it.id, facilityName(session, it), "${Formatting.quantity(have)} in stock", have >= t.quantity, if (cost.raw > 0L) "cost ${money(cost)} a unit" else "no stock yet")
        }
    }

    /** The default bid offered in the form: just under the market price, inside the band. */
    fun suggestedPrice(session: GameSession, tender: TenderId): Money? {
        val e = session.economy
        val t = e.tenders.get(tender) ?: return null
        val ref = e.tenderRunner.reference(t)
        return Money.min(Money.max(Money(ref.raw * 95 / 100), e.tenderRunner.floorFor(t)), t.ceiling)
    }
}
