package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Money
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.talent.DevelopmentKind
import app.many.economytycoon.talent.LadderLine
import app.many.economytycoon.talent.PersonLine
import app.many.economytycoon.talent.PositionLine
import app.many.economytycoon.talent.PromotionPreview
import app.many.economytycoon.talent.SuccessorLine
import app.many.economytycoon.talent.TalentCommand
import app.many.economytycoon.talent.TalentView

data class StepRow(val text: String, val tone: Tone)

data class LadderRow(val title: String, val path: String, val state: String, val tone: Tone, val steps: List<StepRow>, val toggle: TalentCommand, val remove: TalentCommand)

data class SuccessorRow(val name: String, val readiness: String, val tone: Tone, val development: String, val upgrade: TalentCommand?)

data class PositionRow(val title: String, val holder: String, val tone: Tone, val effect: String, val successors: List<SuccessorRow>, val warning: String?)

data class PeopleRow(val name: String, val detail: String, val loyalty: String, val tone: Tone)

data class CareersModel(val tiles: List<Tile>, val ladders: List<LadderRow>, val positions: List<PositionRow>, val people: List<PeopleRow>, val note: String)

/** Internal career ladders, key positions and who is being groomed to fill them. */
object CareersPresenter {
    fun stepRow(s: PromotionPreview, rungs: List<String>, money: (Money) -> String): StepRow {
        val from = rungs.getOrElse(s.rung - 1) { "?" }; val to = rungs.getOrElse(s.rung) { "?" }
        return if (s.blockedBy != null) StepRow("$from to $to: ${s.blockedBy}", Tone.MUTED)
        else StepRow("$from to $to: ${Formatting.plural(s.planned, "promotion")} next review (${TalentWords.people(s.eligible)} qualify), ${money(s.extraWagePerDay)} more a day", Tone.GOOD)
    }

    fun ladder(l: LadderLine, company: app.many.economytycoon.core.CompanyId, money: (Money) -> String): LadderRow = LadderRow(
        title = l.name, path = l.rungs.joinToString(" > "),
        state = (if (l.enabled) "Active" else "Paused") + ", ${Formatting.plural(l.promotions, "promotion")} made", tone = if (l.enabled) Tone.GOOD else Tone.MUTED,
        steps = l.steps.map { stepRow(it, l.rungs, money) },
        toggle = TalentCommand.SetLadderEnabled(company, l.id, !l.enabled), remove = TalentCommand.RemoveLadder(company, l.id),
    )

    fun nextDevelopment(d: DevelopmentKind): DevelopmentKind? = when (d) {
        DevelopmentKind.NONE -> DevelopmentKind.MENTORING
        DevelopmentKind.MENTORING -> DevelopmentKind.STRETCH
        DevelopmentKind.STRETCH -> DevelopmentKind.EXECUTIVE_COURSE
        DevelopmentKind.EXECUTIVE_COURSE -> null
    }

    fun successor(s: SuccessorLine, pos: PositionLine, company: app.many.economytycoon.core.CompanyId): SuccessorRow = SuccessorRow(
        name = s.name, readiness = Formatting.percentPpm(s.readinessPpm) + " ready", tone = Tones.health(s.readinessPpm), development = s.development.label,
        upgrade = nextDevelopment(s.development)?.let { TalentCommand.SetDevelopment(company, pos.id, s.person, it) },
    )

    fun position(p: PositionLine, company: app.many.economytycoon.core.CompanyId): PositionRow {
        val best = p.successors.maxOfOrNull { it.readinessPpm } ?: 0L
        return PositionRow(
            title = "${p.title} (${p.role.lowercase()})",
            holder = when { p.vacant && p.searching -> "Vacant ${Formatting.plural(p.vacantDays, "day")}, searching"; p.vacant -> "Vacant"; else -> "${p.holder}, ${p.holderAge}" },
            tone = if (p.vacant) Tone.BAD else Tones.health(p.effectivenessPpm),
            effect = "Running at ${Formatting.percentPpm(p.effectivenessPpm)} of full effect",
            successors = p.successors.map { successor(it, p, company) },
            warning = when { p.vacant -> null; p.successors.isEmpty() -> "No successor named"; best < 550_000L -> "No successor is ready yet"; else -> null },
        )
    }

    fun person(p: PersonLine, money: (Money) -> String): PeopleRow = PeopleRow(
        name = p.name, detail = "${p.role}, ${p.ageYears}, ${money(p.wage)} a day" + (p.position?.let { ", holds $it" } ?: ""),
        loyalty = Formatting.percentPpm(p.loyaltyPpm) + " loyal", tone = Tones.health(p.loyaltyPpm),
    )

    fun build(v: TalentView, money: (Money) -> String = { Formatting.compactMoney(it) }): CareersModel {
        val tiles = listOf(
            Tile("Ladders", v.ladders.size.toString()),
            Tile("Key posts", v.positions.size.toString()),
            Tile("Succession cover", Formatting.percentPpm(v.coveragePpm), Tones.health(v.coveragePpm)),
            Tile("Key post effect", Formatting.percentPpm(v.keyRoleIndexPpm), Tones.health(v.keyRoleIndexPpm)),
        )
        val note = when {
            v.positions.any { it.vacant } -> "A key post is empty; output there is reduced until it is filled."
            v.positions.any { it.successors.isEmpty() } -> "Name successors so a departure does not leave a hole."
            else -> "Promoting from within keeps people and costs less than hiring in."
        }
        return CareersModel(tiles, v.ladders.map { ladder(it, v.company, money) }, v.positions.map { position(it, v.company) }, v.people.map { person(it, money) }, note)
    }
}
