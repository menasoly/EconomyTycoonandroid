package app.many.economytycoon.ui.present

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.urban.PetitionKind
import app.many.economytycoon.urban.PetitionStatus
import app.many.economytycoon.urban.UrbanCommand
import app.many.economytycoon.urban.UrbanReader
import app.many.economytycoon.urban.UrbanTestSupport
import app.many.economytycoon.urban.ZoneUse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrbanCouncilPresenterTest {
    private val T = UrbanTestSupport
    private val money: MoneyText = { "$" + it.wholeUnits() }
    private val firm = CompanyId(8)

    private fun withPetition(days: Int): app.many.economytycoon.urban.CityView {
        val svc = T.withCity()
        val d = T.plan(svc).districts.first { it.zone == ZoneUse.RESIDENTIAL }
        svc.execute(UrbanCommand.FilePetition(T.city, d.index, PetitionKind.REZONE, ZoneUse.MIXED, 0, 0, firm), Tick(0)).getOrThrow()
        T.run(svc, 0, days)
        return UrbanReader.cityView(svc, T.city, Tick(days.toLong()))!!
    }

    @Test fun `stance names cover the whole range`() {
        assertEquals("Strongly pro-growth", UrbanCouncilPresenter.stance(-500))
        assertEquals("Leans pro-growth", UrbanCouncilPresenter.stance(-200))
        assertEquals("Undecided", UrbanCouncilPresenter.stance(0))
        assertEquals("Leans against growth", UrbanCouncilPresenter.stance(300))
        assertEquals("Strongly against growth", UrbanCouncilPresenter.stance(500))
        assertEquals(Tone.GOOD, UrbanCouncilPresenter.stanceTone(-300))
        assertEquals(Tone.BAD, UrbanCouncilPresenter.stanceTone(300))
        assertEquals(Tone.NEUTRAL, UrbanCouncilPresenter.stanceTone(0))
    }

    @Test fun `the page has tiles and one row per member`() {
        val m = UrbanCouncilPresenter.build(withPetition(1), Tick(1), money)
        assertEquals(4, m.tiles.size)
        assertEquals(9, m.seats.size)
        assertTrue(m.seats.all { it.name.isNotBlank() && it.ward.isNotBlank() })
        assertTrue(m.note.isNotBlank())
    }

    @Test fun `a fresh petition is open and can be withdrawn or lobbied`() {
        val m = UrbanCouncilPresenter.build(withPetition(1), Tick(1), money)
        val row = m.open.single()
        assertTrue(row.title.startsWith("Rezone residential to mixed use"))
        assertEquals("Filed", row.status)
        assertTrue(row.canWithdraw && row.canLobby)
        assertTrue(row.timeline.startsWith("Hearing"))
        assertTrue(m.closed.isEmpty())
        assertEquals(4, row.facts.size)
    }

    @Test fun `a heard petition shows its vote date`() {
        val m = UrbanCouncilPresenter.build(withPetition(16), Tick(16), money)
        val row = m.open.firstOrNull() ?: m.closed.first()
        if (row.status == PetitionStatus.HEARING.label) assertTrue(row.timeline.startsWith("Vote"))
        assertTrue(row.vote.isNotBlank())
    }

    @Test fun `a decided petition moves to the closed list`() {
        val m = UrbanCouncilPresenter.build(withPetition(120), Tick(120), money)
        assertTrue(m.open.isEmpty())
        val row = m.closed.single()
        assertFalse(row.canWithdraw)
        assertFalse(row.canLobby)
        assertTrue(row.status == "In force" || row.status == "Denied")
        assertTrue(row.vote.contains("for"))
    }

    @Test fun `tones follow the status`() {
        assertEquals(Tone.INFO, UrbanCouncilPresenter.statusTone(PetitionStatus.FILED))
        assertEquals(Tone.GOOD, UrbanCouncilPresenter.statusTone(PetitionStatus.ENACTED))
        assertEquals(Tone.BAD, UrbanCouncilPresenter.statusTone(PetitionStatus.DENIED))
        assertEquals(Tone.MUTED, UrbanCouncilPresenter.statusTone(PetitionStatus.WITHDRAWN))
    }

    @Test fun `command builders carry the choices`() {
        val rezone = UrbanCouncilPresenter.rezone(T.city, 3, ZoneUse.COMMERCIAL, 4_000, 20, firm) as UrbanCommand.FilePetition
        assertEquals(PetitionKind.REZONE, rezone.kind)
        assertEquals(3, rezone.district)
        assertEquals(firm, rezone.applicant)
        val variance = UrbanCouncilPresenter.variance(T.city, 3, ZoneUse.RESIDENTIAL, 3_500, 12) as UrbanCommand.FilePetition
        assertEquals(PetitionKind.VARIANCE, variance.kind)
        assertEquals(null, variance.applicant)
    }

    @Test fun `withdraw and lobby commands point at the petition`() {
        val row = UrbanCouncilPresenter.build(withPetition(1), Tick(1), money).open.single()
        val w = UrbanCouncilPresenter.withdraw(T.city, row, firm) as UrbanCommand.WithdrawPetition
        assertEquals(row.id, w.petition.value)
        val l = UrbanCouncilPresenter.lobby(T.city, row, firm, Money.units(5_000), 2) as UrbanCommand.LobbyPetition
        assertEquals(Money.units(5_000), l.amount)
        assertEquals(2, l.seat)
    }

    @Test fun `the closed list is capped`() {
        val svc = T.withCity()
        val plan = T.plan(svc)
        repeat(14) { i ->
            val d = plan.districts[i % 9]
            val p = plan.petitions.file(app.many.economytycoon.urban.PetitionId(100L + i), d, PetitionKind.REZONE, null, ZoneUse.MIXED, 0, 0, Money.ZERO, Tick(i * 400L), T.params)
            p.getOrNull()?.let { it.status = PetitionStatus.DENIED }
        }
        val m = UrbanCouncilPresenter.build(UrbanReader.cityView(svc, T.city, Tick(9_000))!!, Tick(9_000), money)
        assertTrue(m.closed.size <= 10)
    }
}
