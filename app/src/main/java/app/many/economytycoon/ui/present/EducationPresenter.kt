package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Money
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.talent.InstitutionKind
import app.many.economytycoon.talent.InstitutionLine
import app.many.economytycoon.talent.QualificationLine
import app.many.economytycoon.talent.TalentCommand
import app.many.economytycoon.talent.TalentView

data class InstitutionRow(
    val title: String, val owner: String, val where: String, val seats: String, val seatsTone: Tone, val quality: String, val qualityTone: Tone,
    val fees: String, val results: String, val fields: String, val building: String?, val close: TalentCommand?,
)

data class QualificationRow(val where: String, val school: String, val vocational: String, val university: String, val index: String, val tone: Tone)

data class EducationModel(val tiles: List<Tile>, val mine: List<InstitutionRow>, val others: List<InstitutionRow>, val qualifications: List<QualificationRow>, val note: String)

/** Schools, colleges and universities in the company's regions, and who is qualified there. */
object EducationPresenter {
    fun kindLabel(k: InstitutionKind): String = k.label

    fun row(i: InstitutionLine, view: TalentView, money: (Money) -> String): InstitutionRow {
        val occupancy = if (i.capacity == 0L) 0L else i.enrolled * 1_000_000L / i.capacity
        return InstitutionRow(
            title = i.name.ifBlank { kindLabel(i.kind) }, owner = if (i.publicOwned) "Public" else if (i.mine) "Yours" else "Private",
            where = "${kindLabel(i.kind)} in ${i.regionName}",
            seats = "${Formatting.grouped(i.enrolled)} of ${Formatting.grouped(i.capacity)} places",
            seatsTone = if (occupancy >= 950_000L) Tone.WARN else Tone.NEUTRAL,
            quality = Formatting.percentPpm(i.qualityPpm), qualityTone = Tones.health(i.qualityPpm),
            fees = if (i.tuition.raw <= 0L) "Free to attend" else "${money(i.tuition)} a year",
            results = "${Formatting.grouped(i.graduates)} graduated, ${Formatting.grouped(i.dropped)} left early",
            fields = if (i.kind == InstitutionKind.SCHOOL) "General schooling" else i.fields.joinToString(", ").ifBlank { "No subjects chosen" },
            building = if (i.underConstruction > 0L) "${Formatting.plural(i.underConstruction, "place")} being built" else null,
            close = if (i.mine) TalentCommand.CloseInstitution(view.company, i.id) else null,
        )
    }

    fun qualification(q: QualificationLine): QualificationRow = QualificationRow(
        where = q.regionName, school = Formatting.grouped(q.school), vocational = Formatting.grouped(q.vocational), university = Formatting.grouped(q.university),
        index = Formatting.percentPpm(q.indexPpm), tone = Tones.health(q.indexPpm),
    )

    fun build(v: TalentView, money: (Money) -> String = { Formatting.compactMoney(it) }): EducationModel {
        val rows = v.institutions.map { it to row(it, v, money) }
        val seats = v.institutions.sumOf { it.capacity }
        val enrolled = v.institutions.sumOf { it.enrolled }
        val tiles = listOf(
            Tile("Institutions", v.institutions.size.toString()),
            Tile("Places", Formatting.grouped(seats)),
            Tile("Students", Formatting.grouped(enrolled)),
            Tile("Yours", v.institutions.count { it.mine }.toString()),
        )
        val note = if (v.shortages.isNotEmpty()) "Colleges take years to turn out graduates; build before the shortage bites." else "Education supply matches what employers ask for."
        return EducationModel(
            tiles = tiles, mine = rows.filter { it.first.mine }.map { it.second }, others = rows.filter { !it.first.mine }.map { it.second },
            qualifications = v.qualifications.map { qualification(it) }, note = note,
        )
    }
}
