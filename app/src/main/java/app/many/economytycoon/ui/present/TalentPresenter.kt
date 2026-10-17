package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Money
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.talent.CohortLine
import app.many.economytycoon.talent.MigrationLine
import app.many.economytycoon.talent.ShortageKind
import app.many.economytycoon.talent.ShortageRow
import app.many.economytycoon.talent.TalentView
import app.many.economytycoon.workforce.CohortKey

data class TalentAdviceRow(val code: String, val title: String, val detail: String, val tone: Tone)

data class TalentCohortRow(
    val key: CohortKey, val role: String, val people: String, val output: String, val outputTone: Tone, val weakness: String?,
    val pay: String, val payTone: Tone, val morale: String, val moraleTone: Tone, val openings: String?, val training: String?,
)

data class TalentShortageRow(val title: String, val where: String, val kind: String, val tone: Tone, val detail: String)

data class TalentMigrationRow(val country: String, val doors: String, val flows: String, val brainDrain: String, val tone: Tone)

data class TalentModel(
    val tiles: List<Tile>,
    val advice: List<TalentAdviceRow>,
    val cohorts: List<TalentCohortRow>,
    val shortages: List<TalentShortageRow>,
    val migration: List<TalentMigrationRow>,
    val note: String,
)

/** Small wording helpers shared by the people-side presenters. */
object TalentWords {
    fun people(n: Long): String = if (n == 1L) "1 person" else "${Formatting.grouped(n)} people"
}

/** Overview of skills, shortages and the people side of the company. */
object TalentPresenter {
    fun kindLabel(k: ShortageKind): String = when (k) {
        ShortageKind.NONE -> "No shortage"
        ShortageKind.QUANTITY -> "Too few people"
        ShortageKind.QUALITY -> "Not skilled enough"
        ShortageKind.BOTH -> "Too few, and under-skilled"
    }

    fun kindTone(k: ShortageKind): Tone = when (k) {
        ShortageKind.NONE -> Tone.GOOD
        ShortageKind.QUANTITY, ShortageKind.QUALITY -> Tone.WARN
        ShortageKind.BOTH -> Tone.BAD
    }

    /** Output of a team against what its role asks: under 85% is poor, over 100% good. */
    fun outputTone(multiplierPpm: Long): Tone = when {
        multiplierPpm < 850_000L -> Tone.BAD
        multiplierPpm < 980_000L -> Tone.WARN
        else -> Tone.GOOD
    }

    fun payTone(ratioPpm: Long): Tone = when {
        ratioPpm < 900_000L -> Tone.BAD
        ratioPpm < 1_000_000L -> Tone.WARN
        else -> Tone.GOOD
    }

    fun cohort(c: CohortLine): TalentCohortRow = TalentCohortRow(
        key = c.key, role = Formatting.title(c.role), people = TalentWords.people(c.headcount),
        output = Formatting.percentPpm(c.multiplierPpm) + " of the role's need", outputTone = outputTone(c.multiplierPpm),
        weakness = c.weakestSkill?.let { "Weakest in ${it.lowercase()}" },
        pay = Formatting.percentPpm(c.wageRatioPpm) + " of market pay", payTone = payTone(c.wageRatioPpm),
        morale = Formatting.percentPpm(c.moralePpm), moraleTone = Tones.health(c.moralePpm),
        openings = if (c.openings > 0L) "${Formatting.plural(c.openings, "post")} open" else null,
        training = if (c.trainingShareOfCohortPpm > 0L) "${Formatting.percentPpm(c.trainingShareOfCohortPpm)} of time in training" else null,
    )

    fun shortage(r: ShortageRow): TalentShortageRow = TalentShortageRow(
        title = Formatting.title(r.skill.key), where = "Region ${r.region.value}", kind = kindLabel(r.kind), tone = kindTone(r.kind),
        detail = "Wages ${Formatting.signedPercentPpm(r.premiumPpm - 1_000_000L)} over normal; ${TalentWords.people(r.supply)} ready against ${Formatting.plural(r.demandFlow, "opening")} a year",
    )

    fun migration(m: MigrationLine): TalentMigrationRow = TalentMigrationRow(
        country = m.countryName,
        doors = (if (m.skilledOnly) "Skilled only, " else "") + "open to ${Formatting.percentPpm(m.opennessPpm)}, quota ${Formatting.percentPpm(m.quotaPpm)} of workers a year",
        flows = "${TalentWords.people(m.out)} left, ${TalentWords.people(m.arrived)} arrived, ${TalentWords.people(m.returned)} came home".replace("persons", "people"),
        brainDrain = if (m.brainDrainPpm <= 0L) "No skilled people lost" else "${Formatting.percentPpm(m.brainDrainPpm)} of workers were skilled people who left",
        tone = when { m.brainDrainPpm >= 10_000L -> Tone.BAD; m.brainDrainPpm >= 3_000L -> Tone.WARN; else -> Tone.NEUTRAL },
    )

    fun build(v: TalentView, money: (Money) -> String = { Formatting.compactMoney(it) }): TalentModel {
        val heads = v.cohorts.sumOf { it.headcount }
        val openings = v.cohorts.sumOf { it.openings }
        val output = if (heads == 0L) 1_000_000L else v.cohorts.sumOf { it.multiplierPpm * it.headcount } / heads
        val appeal = if (v.brands.isEmpty()) 500_000L else v.brands.sumOf { it.brandPpm } / v.brands.size
        val tiles = listOf(
            Tile("People", Formatting.grouped(heads)),
            Tile("Open posts", Formatting.grouped(openings), if (openings > 0L) Tone.WARN else Tone.NEUTRAL),
            Tile("Skill match", Formatting.percentPpm(output), outputTone(output)),
            Tile("Shortages", v.shortages.size.toString(), if (v.shortages.isEmpty()) Tone.GOOD else Tone.WARN),
            Tile("Key posts covered", Formatting.percentPpm(v.coveragePpm), Tones.health(v.coveragePpm)),
            Tile("Employer appeal", Formatting.percentPpm(appeal), Tones.health(appeal)),
        )
        val note = when {
            v.cohorts.isEmpty() -> "You have no staff yet."
            v.shortages.isNotEmpty() -> "Skills you rely on are scarce in your regions. Training your own people is slower than hiring but cheaper than a wage race."
            else -> "People and skills look healthy."
        }
        return TalentModel(
            tiles = tiles,
            advice = v.advice.map { TalentAdviceRow(it.code, it.title, it.detail, AdvisorPresenter.tone(it.priority)) },
            cohorts = v.cohorts.map { cohort(it) },
            shortages = v.shortages.map { shortage(it) },
            migration = v.migration.map { migration(it) },
            note = note,
        )
    }
}
