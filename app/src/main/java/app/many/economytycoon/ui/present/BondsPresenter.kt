package app.many.economytycoon.ui.present

import app.many.economytycoon.bonds.Bond
import app.many.economytycoon.bonds.DEALER
import app.many.economytycoon.core.BondId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.session.GameSession

data class BondOffer(
    val id: BondId,
    val title: String,
    val coupon: String,
    val unitPrice: String,
    val left: String,
    val closes: String,
    val yours: String?,
    val canSubscribe: Boolean,
    val maxUnits: Long,
)

data class BondHolding(
    val id: BondId,
    val title: String,
    val units: Long,
    val cost: String,
    val value: String,
    val gain: String,
    val gainTone: Tone,
    val income: String,
    val matures: String,
    val canSell: Boolean,
)

data class BondForSale(
    val id: BondId,
    val title: String,
    val units: Long,
    val price: String,
    val yieldText: String,
    val matures: String,
    val canBuy: Boolean,
)

data class BondsModel(
    val tiles: List<Tile>,
    val offers: List<BondOffer>,
    val holdings: List<BondHolding>,
    val forSale: List<BondForSale>,
    val rate: String,
)

object BondsPresenter {
    fun title(session: GameSession, b: Bond): String {
        val country = session.economy.world.countries().firstOrNull { it.id == b.country }?.name ?: "Government"
        return "$country ${b.tenorDays}-day bond"
    }

    fun offer(session: GameSession, b: Bond, me: CompanyId): BondOffer {
        val e = session.economy
        val now = session.tick.value
        val cash = e.finance.ledger.cash(e.companies.require(me).owner)
        val affordable = if (b.faceEach.raw <= 0L) 0L else (cash.raw / b.faceEach.raw).coerceAtLeast(0L)
        val max = minOf(b.unitsLeft, affordable)
        val held = b.heldBy(me)
        return BondOffer(
            b.id, title(session, b), Formatting.percentPpm(b.couponPpm) + " a year", session.settings.money(b.faceEach) + " a unit",
            "${Formatting.grouped(b.unitsLeft)} of ${Formatting.grouped(b.unitsOffered)} units left",
            "Closes in ${Formatting.plural((b.subscriptionEnds.value - now).coerceAtLeast(0L), "day")}",
            if (held > 0L) "You hold ${Formatting.grouped(held)} units" else null, max > 0L, max,
        )
    }

    fun holding(session: GameSession, b: Bond, me: CompanyId): BondHolding {
        val e = session.economy
        val money = session.settings::money
        val units = b.heldBy(me)
        val cost = b.costOf(me)
        val bid = e.bonds.unitPrice(b, e.bondRunner.pricePpm(b, session.tick) - e.bonds.params.dealerSpreadPpm)
        val value = Money(bid.raw * units)
        val gain = value - cost
        val dealerRoom = e.bonds.params.dealerMaxUnits - b.heldBy(DEALER)
        return BondHolding(
            b.id, title(session, b), units, money(cost), money(value), (if (gain.raw > 0L) "+" else "") + money(gain), Tones.bySign(gain.raw),
            money(Money(e.bonds.couponPerUnit(b).raw * units)) + " every " + e.bonds.params.couponEvery + " days",
            "Repaid in " + Formatting.duration((b.maturesAt.value - session.tick.value).coerceAtLeast(0L)), dealerRoom > 0L,
        )
    }

    fun forSale(session: GameSession, b: Bond, me: CompanyId): BondForSale {
        val e = session.economy
        val ask = e.bonds.unitPrice(b, e.bondRunner.pricePpm(b, session.tick) + e.bonds.params.dealerSpreadPpm)
        val cash = e.finance.ledger.cash(e.companies.require(me).owner)
        val units = b.heldBy(DEALER)
        return BondForSale(
            b.id, title(session, b), units, session.settings.money(ask) + " a unit", "Yields " + Formatting.percentPpm(e.bondRunner.currentYield(b, session.tick)),
            "Repaid in " + Formatting.duration((b.maturesAt.value - session.tick.value).coerceAtLeast(0L)), units > 0L && cash >= ask,
        )
    }

    fun build(session: GameSession): BondsModel? {
        val me = session.selectedCompany ?: return null
        val e = session.economy
        val money = session.settings::money
        val offers = e.bonds.open().sortedBy { it.subscriptionEnds }.map { offer(session, it, me) }
        val held = e.bonds.heldBy(me).sortedBy { it.maturesAt }
        val holdings = held.map { holding(session, it, me) }
        val forSale = e.bonds.outstanding().filter { it.heldBy(DEALER) > 0L }.sortedBy { it.maturesAt }.map { forSale(session, it, me) }
        val invested = held.fold(Money.ZERO) { a, b -> a + b.costOf(me) }
        val worth = e.bondRunner.worth(me, session.tick)
        val country = e.companies.company(me)?.country
        val rate = country?.let { Formatting.percentPpm(e.macro.policyRatePpm(it)) } ?: "-"
        val tiles = listOf(
            Tile("Open issues", offers.size.toString(), if (offers.isNotEmpty()) Tone.INFO else Tone.NEUTRAL),
            Tile("Bonds held", held.size.toString()),
            Tile("Invested", money(invested)),
            Tile("Worth now", money(worth), Tones.bySign((worth - invested).raw), note = "at the dealer's bid"),
        )
        return BondsModel(tiles, offers, holdings, forSale, rate)
    }
}
