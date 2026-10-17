package app.many.economytycoon.urban

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LobbyingTest {
    private fun seat(integrity: Int = 600, ties: Int = 400) = CouncilSeat(0, "Test", 0, 0, ties, integrity)

    @Test fun `points grow with the square root of the money`() {
        assertEquals(0, Lobbying.points(Money.ZERO))
        assertEquals(0, Lobbying.points(Money.units(-500)))
        val a = Lobbying.points(Money.units(10_000))
        val b = Lobbying.points(Money.units(40_000))
        assertEquals(a * 2, b)
        assertTrue(Lobbying.points(Money.units(100_000)) < 10 * Lobbying.points(Money.units(10_000)))
    }

    @Test fun `a targeted effort reaches one member and a general one a fifth of everyone`() {
        val targeted = LobbyEffort(PetitionId(1), CompanyId(1), 3, Money.units(1), 100, Tick(0))
        val general = LobbyEffort(PetitionId(1), CompanyId(1), null, Money.units(1), 100, Tick(0))
        assertEquals(100, Lobbying.pointsFor(targeted, 3))
        assertEquals(0, Lobbying.pointsFor(targeted, 2))
        assertEquals(20, Lobbying.pointsFor(general, 0))
        assertEquals(20, Lobbying.pointsFor(general, 8))
    }

    @Test fun `honest members are harder to move`() {
        assertTrue(Lobbying.effective(100, seat(integrity = 900)) < Lobbying.effective(100, seat(integrity = 300)))
        assertTrue(Lobbying.effective(100, seat(ties = 800)) > Lobbying.effective(100, seat(ties = 100)))
        assertEquals(0, Lobbying.effective(0, seat()))
    }

    @Test fun `a very honest member with no ties cannot be bought`() {
        assertEquals(0, Lobbying.effective(500, seat(integrity = 1000, ties = 0)))
    }

    @Test fun `modest spending carries no scandal risk`() {
        val cap = Money.units(150_000)
        assertEquals(0L, Lobbying.scandalRiskPpm(Money.units(10_000), cap, 700))
        assertEquals(0L, Lobbying.scandalRiskPpm(Money.units(59_000), cap, 700))
        assertEquals(0L, Lobbying.scandalRiskPpm(Money.ZERO, cap, 700))
        assertEquals(0L, Lobbying.scandalRiskPpm(Money.units(10_000), Money.ZERO, 700))
    }

    @Test fun `heavy spending raises the risk and honest councils raise it more`() {
        val cap = Money.units(150_000)
        val mid = Lobbying.scandalRiskPpm(Money.units(100_000), cap, 700)
        val all = Lobbying.scandalRiskPpm(cap, cap, 700)
        assertTrue(mid > 0L && all > mid)
        assertTrue(Lobbying.scandalRiskPpm(cap, cap, 900) > all)
        assertTrue(Lobbying.scandalRiskPpm(Money.units(9_000_000), cap, 1000) <= 900_000L)
    }

    @Test fun `the default hook has no opinion`() {
        val p = Petition(PetitionId(1), 0, PetitionKind.REZONE, null, ZoneUse.RESIDENTIAL, ZoneUse.MIXED, 3000, 10, Tick(0), Money.ZERO)
        assertEquals(0, NoLobbying.influence(p, seat(), Tick(0)))
        val hook = LobbyingHook { pet, s, _ -> if (pet.toZone == ZoneUse.MIXED) 30 + s.index else 0 }
        assertEquals(30, hook.influence(p, seat(), Tick(0)))
    }
}
