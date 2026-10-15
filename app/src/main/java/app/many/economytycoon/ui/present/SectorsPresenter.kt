package app.many.economytycoon.ui.present

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.intel.Balance
import app.many.economytycoon.intel.Concentration
import app.many.economytycoon.intel.GoodSector
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.session.GameSession

enum class SectorFilter(val label: String) { MINE("Mine"), OPENINGS("Openings"), ALL("All") }

data class ProducerLine(val name: String, val share: String, val facilities: Int, val mine: Boolean)

data class SectorRow(
    val good: GoodId,
    val name: String,
    val concentration: String,
    val concentrationTone: Tone,
    val balance: String,
    val balanceTone: Tone,
    val price: String,
    val priceTone: Tone,
    val leader: String,
    val yours: String?,
    val producers: List<ProducerLine>,
    val opening: Boolean,
)

data class SectorsModel(val filter: SectorFilter, val rows: List<SectorRow>, val summary: String, val empty: String)

/** Who makes what, and where there is room to make it too. */
object SectorsPresenter {
    fun concentrationTone(c: Concentration): Tone = when (c) {
        Concentration.NONE -> Tone.WARN
        Concentration.MONOPOLY -> Tone.WARN
        Concentration.CONCENTRATED -> Tone.WARN
        Concentration.MODERATE -> Tone.INFO
        Concentration.COMPETITIVE -> Tone.GOOD
    }

    fun balanceTone(b: Balance): Tone = when (b) {
        Balance.SURPLUS -> Tone.INFO
        Balance.BALANCED -> Tone.GOOD
        Balance.SHORT -> Tone.WARN
        Balance.UNMADE -> Tone.BAD
        Balance.UNUSED -> Tone.MUTED
    }

    /** Dearer than usual is good news for a seller, cheaper is bad. */
    fun priceTone(ratioPpm: Long): Tone = when {
        ratioPpm >= 1_100_000L -> Tone.GOOD
        ratioPpm <= 900_000L -> Tone.BAD
        else -> Tone.NEUTRAL
    }

    fun priceText(ratioPpm: Long): String {
        val diff = ratioPpm - 1_000_000L
        return when {
            kotlin.math.abs(diff) < 10_000L -> "At the usual price"
            diff > 0L -> "${Formatting.percentPpm(diff)} above usual"
            else -> "${Formatting.percentPpm(-diff)} below usual"
        }
    }

    fun row(s: GoodSector, name: String, viewer: CompanyId?): SectorRow {
        val rank = viewer?.let { s.rankOf(it) }
        return SectorRow(
            good = s.good, name = name, concentration = s.concentration.label, concentrationTone = concentrationTone(s.concentration),
            balance = s.balance.label, balanceTone = balanceTone(s.balance), price = priceText(s.priceRatioPpm), priceTone = priceTone(s.priceRatioPpm),
            leader = s.leader?.let { "${it.name} (${Formatting.percentPpm(it.sharePpm)})" } ?: "Nobody",
            yours = if (viewer == null || rank == null) null else "You are #$rank of ${s.producers.size} with ${Formatting.percentPpm(s.shareOf(viewer))}",
            producers = s.producers.map { ProducerLine(it.name, Formatting.percentPpm(it.sharePpm), it.facilities, it.company == viewer) },
            opening = s.isOpening() || s.producers.isEmpty(),
        )
    }

    fun select(all: List<GoodSector>, filter: SectorFilter, viewer: CompanyId?): List<GoodSector> = when (filter) {
        SectorFilter.MINE -> all.filter { s -> viewer != null && s.rankOf(viewer) != null }
        SectorFilter.OPENINGS -> all.filter { (it.producers.isEmpty() && it.industryUsePerDay > 0L) || it.isOpening() }
            .sortedByDescending { it.priceRatioPpm }
        SectorFilter.ALL -> all
    }

    fun emptyText(filter: SectorFilter): String = when (filter) {
        SectorFilter.MINE -> "You do not make anything yet. Give a production line a recipe to take a place here."
        SectorFilter.OPENINGS -> "No obvious gaps right now. Every needed good has sellers at ordinary prices."
        SectorFilter.ALL -> "Nobody is producing anything yet."
    }

    fun model(all: List<GoodSector>, filter: SectorFilter, viewer: CompanyId?, nameOf: (GoodId) -> String): SectorsModel {
        val chosen = select(all, filter, viewer)
        val gaps = all.count { it.producers.isEmpty() && it.industryUsePerDay > 0L }
        return SectorsModel(
            filter = filter,
            rows = chosen.map { row(it, nameOf(it.good), viewer) }.let { if (filter == SectorFilter.ALL) it.sortedBy { r -> r.name } else it },
            summary = "${all.size} goods in production or in use" + if (gaps > 0) ", $gaps with no maker" else "",
            empty = emptyText(filter),
        )
    }

    fun build(session: GameSession, filter: SectorFilter): SectorsModel {
        val e = session.economy
        val all = session.sectors().sectors()
        return model(all, filter, session.selectedCompany) { e.content.good(it).name }
    }
}
