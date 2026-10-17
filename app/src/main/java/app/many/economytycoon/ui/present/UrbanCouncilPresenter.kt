package app.many.economytycoon.ui.present

import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.urban.CityView
import app.many.economytycoon.urban.PetitionKind
import app.many.economytycoon.urban.PetitionStatus
import app.many.economytycoon.urban.PetitionView
import app.many.economytycoon.urban.SeatView
import app.many.economytycoon.urban.UrbanCommand
import app.many.economytycoon.urban.ZoneUse

data class SeatRow(val name: String, val ward: String, val stance: String, val tone: Tone, val ties: String)

data class PetitionRow(
    val id: Long,
    val title: String,
    val district: String,
    val status: String,
    val tone: Tone,
    val timeline: String,
    val vote: String,
    val facts: List<Fact>,
    val canWithdraw: Boolean,
    val canLobby: Boolean,
)

data class UrbanCouncilModel(val tiles: List<Tile>, val seats: List<SeatRow>, val open: List<PetitionRow>, val closed: List<PetitionRow>, val note: String)

/** The city council: who sits on it, what is before it and how it is likely to vote. */
object UrbanCouncilPresenter {
    fun stance(ideology: Int): String = when {
        ideology <= -500 -> "Strongly pro-growth"
        ideology < -150 -> "Leans pro-growth"
        ideology <= 150 -> "Undecided"
        ideology < 500 -> "Leans against growth"
        else -> "Strongly against growth"
    }

    fun stanceTone(ideology: Int): Tone = if (ideology < -150) Tone.GOOD else if (ideology > 150) Tone.BAD else Tone.NEUTRAL

    fun seatRow(s: SeatView) = SeatRow(
        s.name, s.wardName, stance(s.ideology), stanceTone(s.ideology),
        if (s.developerTies >= 50) "Close to developers" else if (s.integrity < 30) "Open to persuasion" else "Independent",
    )

    fun petitionTitle(p: PetitionView): String = when (p.kind) {
        PetitionKind.REZONE -> "Rezone ${p.fromZone.label.lowercase()} to ${p.toZone.label.lowercase()}"
        PetitionKind.VARIANCE -> "Allow floor-area ratio ${UrbanPresenter.ratio(p.farMilli)}"
        PetitionKind.DOWNZONE -> "Downzone to ${p.toZone.label.lowercase()}"
    }

    fun statusTone(s: PetitionStatus): Tone = when (s) {
        PetitionStatus.FILED, PetitionStatus.HEARING -> Tone.INFO
        PetitionStatus.APPROVED, PetitionStatus.ENACTED -> Tone.GOOD
        PetitionStatus.DENIED -> Tone.BAD
        PetitionStatus.WITHDRAWN, PetitionStatus.VOID -> Tone.MUTED
    }

    fun petitionRow(p: PetitionView, now: Tick, money: MoneyText): PetitionRow {
        val timeline = when (p.status) {
            PetitionStatus.FILED -> "Hearing ${Formatting.date(p.hearingAt)}"
            PetitionStatus.HEARING -> "Vote ${Formatting.date(p.voteAt)}"
            else -> "Filed ${Formatting.date(p.filedAt)}"
        }
        val vote = if (p.status == PetitionStatus.FILED || p.status == PetitionStatus.HEARING) "Needs ${p.needed} of the seats; likely ${p.yes} for, ${p.no} against"
        else "${p.yes} for, ${p.no} against"
        val facts = listOf(
            Fact("Staff assessment", "${p.staffScore / 10} of 100", Tones.health(p.staffScore * 1000L)),
            Fact("Residents in favour", p.support.toString()),
            Fact("Residents opposed", p.opposition.toString(), if (p.protest) Tone.BAD else Tone.NEUTRAL, if (p.protest) "Organised protest" else null),
            Fact("Spent on lobbying", money(p.lobbied)),
        )
        val open = p.status.open
        return PetitionRow(
            p.id.value, petitionTitle(p), p.districtName, p.status.label, statusTone(p.status), timeline, vote, facts,
            canWithdraw = p.status == PetitionStatus.FILED || p.status == PetitionStatus.HEARING,
            canLobby = p.status == PetitionStatus.FILED || (p.status == PetitionStatus.HEARING && p.voteAt > now),
        )
    }

    fun build(c: CityView, now: Tick, money: MoneyText = { Formatting.compactMoney(it) }): UrbanCouncilModel {
        val council = c.council
        val tiles = listOf(
            Tile("Seats", council.seats.size.toString(), Tone.NEUTRAL, "Majority ${council.majority}, supermajority ${council.supermajority}"),
            Tile("Pro-growth seats", council.growthSeats.toString(), if (council.growthSeats >= council.majority) Tone.GOOD else Tone.WARN),
            Tile("Public mood", if (council.moodAgainstGrowth > 100) "Against growth" else if (council.moodAgainstGrowth < -100) "Welcomes growth" else "Mixed",
                if (council.moodAgainstGrowth > 100) Tone.BAD else Tone.NEUTRAL),
            Tile("Next election", "${council.nextElectionIn / 30} months"),
        )
        val (open, closed) = c.petitions.partition { it.status.open }
        return UrbanCouncilModel(
            tiles, council.seats.map { seatRow(it) }, open.map { petitionRow(it, now, money) },
            closed.sortedByDescending { it.filedAt.value }.take(10).map { petitionRow(it, now, money) },
            "Petitions that are denied cannot be filed again for six months.",
        )
    }

    fun rezone(city: CityId, district: Int, to: ZoneUse, farMilli: Int, floors: Int, applicant: CompanyId? = null): UrbanCommand =
        UrbanCommand.FilePetition(city, district, PetitionKind.REZONE, to, farMilli, floors, applicant)

    fun variance(city: CityId, district: Int, zone: ZoneUse, farMilli: Int, floors: Int, applicant: CompanyId? = null): UrbanCommand =
        UrbanCommand.FilePetition(city, district, PetitionKind.VARIANCE, zone, farMilli, floors, applicant)

    fun withdraw(city: CityId, p: PetitionRow, applicant: CompanyId? = null): UrbanCommand =
        UrbanCommand.WithdrawPetition(city, app.many.economytycoon.urban.PetitionId(p.id), applicant)

    fun lobby(city: CityId, p: PetitionRow, company: CompanyId, amount: Money, seat: Int? = null): UrbanCommand =
        UrbanCommand.LobbyPetition(city, app.many.economytycoon.urban.PetitionId(p.id), company, seat, amount)
}
