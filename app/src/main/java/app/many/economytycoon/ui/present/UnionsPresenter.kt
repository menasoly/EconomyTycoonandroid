package app.many.economytycoon.ui.present

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.session.GameSession
import app.many.economytycoon.unions.Demand
import app.many.economytycoon.unions.DemandStatus
import app.many.economytycoon.unions.Union

data class DemandCard(
    val id: Long,
    val region: String,
    val raise: String,
    val monthlyCost: String,
    val deadline: String,
    val urgent: Boolean,
    val strength: String,
    val acceptableCounter: String,
)

data class UnionCard(
    val id: Long,
    val region: String,
    val members: String,
    val strength: Double,
    val strengthTone: Tone,
    val status: String,
    val statusTone: Tone,
    val record: String,
)

data class PastDemand(val region: String, val summary: String, val tone: Tone, val date: String)

data class UnionsModel(
    val tiles: List<Tile>,
    val demands: List<DemandCard>,
    val unions: List<UnionCard>,
    val history: List<PastDemand>,
    val note: String?,
)

object UnionsPresenter {
    fun statusLabel(s: DemandStatus): String = when (s) {
        DemandStatus.OPEN -> "Waiting for your answer"
        DemandStatus.ACCEPTED -> "Accepted"
        DemandStatus.SETTLED -> "Settled on your offer"
        DemandStatus.STRUCK -> "Strike"
        DemandStatus.BACKED_DOWN -> "Union backed down"
    }

    fun statusTone(s: DemandStatus): Tone = when (s) {
        DemandStatus.OPEN -> Tone.WARN
        DemandStatus.ACCEPTED, DemandStatus.SETTLED -> Tone.GOOD
        DemandStatus.STRUCK -> Tone.BAD
        DemandStatus.BACKED_DOWN -> Tone.INFO
    }

    private fun regionName(session: GameSession, u: Union) = session.economy.world.regions().firstOrNull { it.id == u.region }?.name ?: "Region ${u.region.value}"

    fun demandCard(session: GameSession, d: Demand, u: Union): DemandCard {
        val e = session.economy
        val left = (d.deadline.value - session.tick.value).coerceAtLeast(0L)
        val cost = e.unionRunner.monthlyCost(u, d.raisePpm)
        val minimum = d.raisePpm * e.unions.acceptableShare(u.strengthPpm) / 1_000_000L
        return DemandCard(
            d.id, regionName(session, u), Formatting.percentPpm(d.raisePpm), session.settings.money(cost) + " more a month",
            "Answer within ${Formatting.plural(left, "day")}", left <= 3L, "Union strength ${Formatting.percentPpm(u.strengthPpm)}",
            "They are unlikely to take less than ${Formatting.percentPpm(minimum)}",
        )
    }

    fun unionCard(session: GameSession, u: Union): UnionCard {
        val e = session.economy
        val now = session.tick
        val (status, tone) = when {
            u.onStrike(now) -> "On strike for ${Formatting.plural((u.strikeUntil!!.value - now.value).coerceAtLeast(0L), "more day")}" to Tone.BAD
            e.unions.openDemand(u.id) != null -> "Demand waiting" to Tone.WARN
            else -> "Next wage round in ${Formatting.plural((e.unions.params.roundEvery - (now.value - u.lastRound.value)).coerceAtLeast(0L), "day")}" to Tone.NEUTRAL
        }
        return UnionCard(
            u.id.value, regionName(session, u), "${Formatting.grouped(u.members)} members", u.strengthPpm / 1_000_000.0,
            if (u.strengthPpm >= 650_000L) Tone.BAD else if (u.strengthPpm >= 350_000L) Tone.WARN else Tone.GOOD, status, tone,
            "${Formatting.plural(u.raisesWon.toLong(), "raise")} won · ${Formatting.plural(u.strikes.toLong(), "strike")}",
        )
    }

    fun build(session: GameSession): UnionsModel? {
        val me: CompanyId = session.selectedCompany ?: return null
        val e = session.economy
        val mine = e.unions.ofCompany(me)
        val open = e.unions.openDemandsFor(me).sortedBy { it.deadline }
        val history = mine.flatMap { u -> e.unions.demandsOf(u.id).filter { !it.isOpen }.map { u to it } }
            .sortedByDescending { it.second.resolvedAt?.value ?: 0L }.take(12)
            .map { (u, d) ->
                val what = when (d.status) {
                    DemandStatus.ACCEPTED, DemandStatus.SETTLED -> "${Formatting.percentPpm(d.agreedPpm)} raise agreed (asked ${Formatting.percentPpm(d.raisePpm)})"
                    DemandStatus.STRUCK -> "Refused; ${Formatting.plural(d.strikeDays.toLong(), "day")} strike"
                    else -> "Refused; the union backed down"
                }
                PastDemand(regionName(session, u), what, statusTone(d.status), d.resolvedAt?.let { Formatting.date(it) } ?: "")
            }
        val striking = mine.count { it.onStrike(session.tick) }
        val tiles = listOf(
            Tile("Unions", mine.size.toString(), if (mine.isEmpty()) Tone.GOOD else Tone.NEUTRAL),
            Tile("Members", Formatting.grouped(mine.sumOf { it.members })),
            Tile("Demands", open.size.toString(), if (open.isNotEmpty()) Tone.WARN else Tone.NEUTRAL),
            Tile("On strike", striking.toString(), if (striking > 0) Tone.BAD else Tone.NEUTRAL),
        )
        val note = if (mine.isEmpty()) "No union yet. Workers organise when morale is low or pay lags behind the market." else null
        return UnionsModel(
            tiles, open.map { demandCard(session, it, e.unions.get(it.union)!!) }, mine.sortedBy { it.id }.map { unionCard(session, it) }, history, note,
        )
    }
}
