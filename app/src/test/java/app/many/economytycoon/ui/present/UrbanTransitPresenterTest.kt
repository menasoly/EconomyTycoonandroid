package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.urban.LevelOfService
import app.many.economytycoon.urban.LineStatus
import app.many.economytycoon.urban.TransitNetwork
import app.many.economytycoon.urban.UrbanCommand
import app.many.economytycoon.urban.UrbanReader
import app.many.economytycoon.urban.UrbanTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrbanTransitPresenterTest {
    private val T = UrbanTestSupport
    private val money: MoneyText = { "$" + it.wholeUnits() }

    private fun city(days: Int = 90) = T.withCity().let { T.run(it, 0, days); UrbanReader.cityView(it, T.city, Tick(days.toLong()))!! }

    @Test fun `headways read as minutes`() {
        assertEquals("every 5 min", UrbanTransitPresenter.headwayText(300))
        assertEquals("every 2 min 30 s", UrbanTransitPresenter.headwayText(150))
    }

    @Test fun `statuses and service levels have tones`() {
        assertEquals(Tone.GOOD, UrbanTransitPresenter.statusTone(LineStatus.ACTIVE))
        assertEquals(Tone.WARN, UrbanTransitPresenter.statusTone(LineStatus.SUSPENDED))
        for (s in LineStatus.values()) assertTrue(UrbanTransitPresenter.statusLabel(s).isNotBlank())
        assertEquals(Tone.GOOD, UrbanTransitPresenter.serviceTone(LevelOfService.A))
        assertEquals(Tone.BAD, UrbanTransitPresenter.serviceTone(LevelOfService.F))
    }

    @Test fun `the page shows commute tiles, lines and the busiest roads`() {
        val m = UrbanTransitPresenter.build(city(), money)
        assertEquals(5, m.tiles.size)
        assertEquals(1, m.lines.size)
        assertTrue(m.roads.size in 1..8)
        assertTrue(m.note.isNotBlank())
        assertEquals("Running", m.lines[0].status)
        assertTrue(m.lines[0].route.contains(" - "))
    }

    @Test fun `roads are listed worst first`() {
        val c = city()
        val m = UrbanTransitPresenter.build(c, money)
        val order = c.roads.sortedByDescending { it.volumeCapacityPpm }.take(m.roads.size).map { it.fromName + " - " + it.toName }
        assertEquals(order.toSet(), m.roads.map { it.name }.toSet())
    }

    @Test fun `running lines can be sped up, slowed and suspended`() {
        val c = city()
        val row = UrbanTransitPresenter.build(c, money).lines[0]
        assertTrue(row.canSpeedUp && row.canSlowDown && row.canToggle)
        assertEquals("Suspend", row.toggleLabel)
        val line = c.lines[0]
        val fast = UrbanTransitPresenter.speedUp(T.city, line) as UrbanCommand.SetLineHeadway
        val slow = UrbanTransitPresenter.slowDown(T.city, line) as UrbanCommand.SetLineHeadway
        assertTrue(fast.headwaySeconds < line.headwaySeconds && slow.headwaySeconds > line.headwaySeconds)
        assertTrue(UrbanTransitPresenter.toggle(T.city, line) is UrbanCommand.ToggleLine)
    }

    @Test fun `headway commands stay inside the limits`() {
        val c = city()
        val line = c.lines[0]
        val tight = (UrbanTransitPresenter.speedUp(T.city, line.copy(headwaySeconds = 61)) as UrbanCommand.SetLineHeadway).headwaySeconds
        val loose = (UrbanTransitPresenter.slowDown(T.city, line.copy(headwaySeconds = 3_500)) as UrbanCommand.SetLineHeadway).headwaySeconds
        assertTrue(tight >= TransitNetwork.MIN_HEADWAY)
        assertTrue(loose <= TransitNetwork.MAX_HEADWAY)
        val row = UrbanTransitPresenter.lineRow(line.copy(headwaySeconds = TransitNetwork.MIN_HEADWAY), money)
        assertFalse(row.canSpeedUp)
        assertFalse(UrbanTransitPresenter.lineRow(line.copy(headwaySeconds = TransitNetwork.MAX_HEADWAY), money).canSlowDown)
    }

    @Test fun `a suspended line offers resume and no tuning`() {
        val line = city().lines[0].copy(status = LineStatus.SUSPENDED)
        val row = UrbanTransitPresenter.lineRow(line, money)
        assertEquals("Resume", row.toggleLabel)
        assertTrue(row.canToggle)
        assertFalse(row.canSpeedUp)
    }

    @Test fun `planned lines cannot be toggled`() {
        val row = UrbanTransitPresenter.lineRow(city().lines[0].copy(status = LineStatus.PLANNED), money)
        assertFalse(row.canToggle)
    }

    @Test fun `fares can be changed through a command`() {
        val c = city()
        val cmd = UrbanTransitPresenter.fare(T.city, c.lines[0], Money.cents(75)) as UrbanCommand.SetLineFare
        assertEquals(Money.cents(75), cmd.fare)
        assertEquals(c.lines[0].id, cmd.line)
    }

    @Test fun `tolls show only where charged`() {
        val c = city()
        val tolled = c.roads[0].copy(toll = Money.units(3))
        assertEquals("$3", UrbanTransitPresenter.roadRow(tolled, money).toll)
        assertEquals(null, UrbanTransitPresenter.roadRow(c.roads[0].copy(toll = Money.ZERO), money).toll)
    }
}
