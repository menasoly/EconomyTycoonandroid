package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Money
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.talent.ProgrammeKind
import app.many.economytycoon.talent.ProgrammeLine
import app.many.economytycoon.talent.ProgrammeStatus
import app.many.economytycoon.talent.TalentCommand
import app.many.economytycoon.talent.TalentView
import app.many.economytycoon.core.Tick

data class ProgrammeRow(val title: String, val detail: String, val status: String, val tone: Tone, val timing: String, val bond: String?, val cancel: TalentCommand?)

data class TrainingOption(val title: String, val detail: String, val cost: String, val start: TalentCommand)

data class TrainingModel(val tiles: List<Tile>, val running: List<ProgrammeRow>, val past: List<ProgrammeRow>, val options: List<TrainingOption>, val note: String)

/** Employer training: what is running, what it cost and what could be started. */
object TrainingPresenter {
    fun statusLabel(s: ProgrammeStatus): String = when (s) {
        ProgrammeStatus.ACTIVE -> "Running"
        ProgrammeStatus.COMPLETED -> "Finished"
        ProgrammeStatus.CANCELLED -> "Cancelled"
    }

    fun statusTone(s: ProgrammeStatus): Tone = when (s) {
        ProgrammeStatus.ACTIVE -> Tone.INFO
        ProgrammeStatus.COMPLETED -> Tone.GOOD
        ProgrammeStatus.CANCELLED -> Tone.MUTED
    }

    fun row(p: ProgrammeLine, company: app.many.economytycoon.core.CompanyId, now: Tick, money: (Money) -> String): ProgrammeRow {
        val left = (p.end.value - now.value).coerceAtLeast(0L)
        return ProgrammeRow(
            title = "${p.kind.label}: ${p.skill.lowercase()} for ${p.role.lowercase()}",
            detail = "${Formatting.plural(p.seats, "trainee")}, ${money(p.costPerHead)} each" + (p.provider?.let { ", taught by $it" } ?: ""),
            status = statusLabel(p.status), tone = statusTone(p.status),
            timing = if (p.status == ProgrammeStatus.ACTIVE) "Ends in ${Formatting.plural(left, "day")}" else "Ended ${Formatting.date(p.end)}",
            bond = p.bondDays?.let { d ->
                "Bound for ${Formatting.plural(d.toLong(), "day")} after finishing" + (if (p.bondedHeads > 0L) ", ${TalentWords.people(p.bondedHeads)} bound" else "") +
                    (if (p.clawedBack.raw > 0L) ", ${money(p.clawedBack)} repaid" else "")
            },
            cancel = if (p.status == ProgrammeStatus.ACTIVE) TalentCommand.CancelTraining(company, p.id) else null,
        )
    }

    fun build(v: TalentView, money: (Money) -> String = { Formatting.compactMoney(it) }): TrainingModel {
        val running = v.programmes.filter { it.status == ProgrammeStatus.ACTIVE }.map { row(it, v.company, v.tick, money) }
        val past = v.programmes.filter { it.status != ProgrammeStatus.ACTIVE }.take(8).map { row(it, v.company, v.tick, money) }
        val options = ArrayList<TrainingOption>()
        for (c in v.cohorts) {
            val skill = c.weakestSkillId ?: continue
            if (v.programmes.any { it.status == ProgrammeStatus.ACTIVE && it.cohort == c.key }) continue
            val seats = (c.headcount / 10L).coerceAtLeast(1L)
            for (kind in listOf(ProgrammeKind.ON_THE_JOB, ProgrammeKind.CERTIFICATION)) {
                val per = c.quotes[kind] ?: continue
                options.add(TrainingOption(
                    title = "${kind.label} for ${Formatting.title(c.role).lowercase()}", detail = "${Formatting.plural(seats, "trainee")} in ${c.weakestSkill?.lowercase() ?: "the weakest skill"}, ${Formatting.plural(kind.defaultDays.toLong(), "day")}",
                    cost = money(per * seats), start = TalentCommand.StartTraining(v.company, c.key, skill, kind, seats, kind.defaultDays),
                ))
            }
        }
        val tiles = listOf(
            Tile("Running", running.size.toString()),
            Tile("Trainees", Formatting.grouped(v.programmes.filter { it.status == ProgrammeStatus.ACTIVE }.sumOf { it.seats })),
            Tile("Bound staff", Formatting.grouped(v.programmes.sumOf { it.bondedHeads })),
        )
        val note = if (options.isEmpty() && running.isEmpty()) "Nobody falls short of their role right now." else "Trained people are more productive, and more attractive to rivals. A retention clause makes them repay if they leave early."
        return TrainingModel(tiles, running, past, options, note)
    }
}
