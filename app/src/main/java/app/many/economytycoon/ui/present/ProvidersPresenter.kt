package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Money
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.talent.Accreditation
import app.many.economytycoon.talent.CourseLine
import app.many.economytycoon.talent.ProviderLine
import app.many.economytycoon.talent.TalentCommand
import app.many.economytycoon.talent.TalentView

data class CourseRowModel(val title: String, val detail: String, val tone: Tone, val close: TalentCommand?)

data class ProviderRowModel(
    val rank: String, val title: String, val owner: String, val accreditation: String, val tone: Tone, val quality: String, val results: String,
    val business: String, val courses: List<CourseRowModel>, val accredit: TalentCommand?, val moreTeaching: TalentCommand?, val lessTeaching: TalentCommand?,
)

data class ProvidersModel(val tiles: List<Tile>, val mine: List<ProviderRowModel>, val market: List<ProviderRowModel>, val note: String)

/** The training-provider market: who teaches, how well their students do, and the player's own education businesses. */
object ProvidersPresenter {
    fun nextLevel(a: Accreditation): Accreditation? = Accreditation.values().getOrNull(a.ordinal + 1)

    fun course(c: CourseLine, mine: Boolean, company: app.many.economytycoon.core.CompanyId, money: (Money) -> String): CourseRowModel = CourseRowModel(
        title = c.name, detail = "${c.kind.label} in ${c.skill.lowercase()}: ${Formatting.grouped(c.students)} of ${Formatting.grouped(c.seats)} places, ${money(c.tuition)}, ${Formatting.grouped(c.completed)} completed",
        tone = if (c.open) Tone.NEUTRAL else Tone.MUTED, close = if (mine && c.open) TalentCommand.CloseCourse(company, c.id) else null,
    )

    fun provider(p: ProviderLine, company: app.many.economytycoon.core.CompanyId, money: (Money) -> String): ProviderRowModel {
        val profit = Money(p.revenue.raw - p.costs.raw)
        val next = nextLevel(p.accreditation)
        return ProviderRowModel(
            rank = if (p.rank > 0) "#${p.rank}" else "-", title = p.name, owner = if (p.mine) "You" else p.ownerName,
            accreditation = p.accreditation.label, tone = when (p.accreditation) { Accreditation.NONE -> Tone.MUTED; Accreditation.BASIC -> Tone.INFO; else -> Tone.GOOD },
            quality = "Quality ${Formatting.percentPpm(p.qualityPpm)}, reputation ${Formatting.percentPpm(p.reputationPpm)}",
            results = "${Formatting.grouped(p.graduates)} graduates, ${Formatting.percentPpm(p.completionPpm)} finish, average gain ${Formatting.percentPpm(p.meanGainPpm)}",
            business = "Revenue ${money(p.revenue)}, costs ${money(p.costs)}, " + (if (profit.raw >= 0L) "profit ${money(profit)}" else "loss ${money(Money(-profit.raw))}"),
            courses = p.courses.map { course(it, p.mine, company, money) },
            accredit = if (p.mine && next != null) TalentCommand.AccreditProvider(company, p.id, next) else null,
            moreTeaching = if (p.mine) TalentCommand.SetTeaching(company, p.id, (p.teachingLevelPpm + 200_000L).coerceAtMost(2_000_000L)) else null,
            lessTeaching = if (p.mine) TalentCommand.SetTeaching(company, p.id, (p.teachingLevelPpm - 200_000L).coerceAtLeast(100_000L)) else null,
        )
    }

    fun build(v: TalentView, money: (Money) -> String = { Formatting.compactMoney(it) }): ProvidersModel {
        val sorted = v.providers.sortedWith(compareBy<ProviderLine> { if (it.rank == 0) Int.MAX_VALUE else it.rank }.thenBy { it.id })
        val mine = sorted.filter { it.mine }; val others = sorted.filter { !it.mine }
        val tiles = listOf(
            Tile("Providers", v.providers.size.toString()),
            Tile("Yours", mine.size.toString()),
            Tile("Students", Formatting.grouped(v.providers.sumOf { p -> p.courses.sumOf { it.students } })),
            Tile("Profit", money(Money(mine.sumOf { it.revenue.raw - it.costs.raw })), Tones.bySign(mine.sumOf { it.revenue.raw - it.costs.raw })),
        )
        val note = if (mine.isEmpty()) "Open a provider where skills are scarce: students pay tuition and employers pay for places." else "Quality and results build reputation; reputation and accreditation bring students."
        return ProvidersModel(tiles, mine.map { provider(it, v.company, money) }, others.map { provider(it, v.company, money) }, note)
    }
}
