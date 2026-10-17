package app.many.economytycoon.net

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.companies.Department
import app.many.economytycoon.core.Price
import app.many.economytycoon.markets.Side
import app.many.economytycoon.simulation.Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandPayloadTest {
    private val co = CompanyId(3)

    private fun parseOk(c: Command): Command = (CommandPayload.parse(CommandPayload.encode(c)) as ParsedCommand.Ok).command

    @Test fun commandsRoundTrip() {
        val samples = listOf(
            Command.Rename(co, "New Name"),
            Command.SetBudget(co, Department.MARKETING, Money.units(12)),
            Command.PlaceOrder(co, FacilityId(4), Side.BUY, GoodId("wheat"), Quantity(5_000), Price.of(3), 7),
            Command.Borrow(co, Money.units(1000), 90),
        )
        for (c in samples) assertEquals(c, parseOk(c))
    }

    @Test fun emptyAndTrailingBytesAreRefused() {
        assertTrue(CommandPayload.parse(ByteArray(0)) is ParsedCommand.Bad)
        val bytes = CommandPayload.encode(Command.Rename(co, "Abc")) + byteArrayOf(0)
        assertTrue(CommandPayload.parse(bytes) is ParsedCommand.Bad)
    }

    @Test fun oversizedPayloadIsRefused() {
        assertTrue(CommandPayload.parse(ByteArray(NetLimits.MAX_COMMAND_BYTES + 1)) is ParsedCommand.Bad)
    }

    @Test fun truncationsAreRefused() {
        val bytes = CommandPayload.encode(Command.PlaceOrder(co, FacilityId(4), Side.SELL, GoodId("steel"), Quantity(1000), null))
        for (n in 0 until bytes.size) assertTrue("cut at $n", CommandPayload.parse(bytes.copyOf(n)) is ParsedCommand.Bad)
    }

    @Test fun garbageNeverThrows() {
        for (g in NetFixtures.garbage(21, 3000, 120)) CommandPayload.parse(g)
    }

    @Test fun mutatedCommandsNeverThrowAndAcceptedOnesAreCanonical() {
        val rnd = app.many.economytycoon.core.DeterministicRandom(5)
        val base = CommandPayload.encode(Command.ProposeContract(co, CompanyId(9), app.many.economytycoon.contracts.ContractSide.SELLER, FacilityId(2), GoodId("wheat"), Quantity(1000), Money.units(3), 7, 12))
        repeat(3000) {
            val b = base.copyOf(); b[rnd.nextInt(b.size)] = rnd.nextInt(256).toByte()
            val r = CommandPayload.parse(b)
            if (r is ParsedCommand.Ok) assertTrue(CommandPayload.encode(r.command).contentEquals(b))
        }
    }

    // ---- plausibility ----

    private fun rule(c: Command) = CommandSanity.check(c)

    @Test fun reasonableCommandsPass() {
        assertNull(rule(Command.Rename(co, "Fine Name")))
        assertNull(rule(Command.Borrow(co, Money.units(1_000), 30)))
        assertNull(rule(Command.SetBudget(co, Department.LEGAL, Money.ZERO)))
        assertNull(rule(Command.Dissolve(co)))
    }

    @Test fun foundingIsBlockedByDefaultAndCheckedWhenAllowed() {
        val found = Command.FoundCompany("Cheat Corp", CountryId(1), null, Money.units(5_000_000))
        assertEquals("founding_not_allowed", rule(found))
        val allow = CommandPolicy(allowFounding = true)
        assertNull(CommandSanity.check(found, allow))
        assertEquals("impossible_value", CommandSanity.check(found.copy(capital = Money.units(-5)), allow))
        assertEquals("impossible_value", CommandSanity.check(found.copy(name = "x"), allow))
    }

    @Test fun negativeAndZeroAmountsAreImpossible() {
        assertEquals("impossible_value", rule(Command.Borrow(co, Money.units(-1), 30)))
        assertEquals("impossible_value", rule(Command.Borrow(co, Money.ZERO, 30)))
        assertEquals("impossible_value", rule(Command.Borrow(co, Money.units(10), 0)))
        assertEquals("impossible_value", rule(Command.Borrow(co, Money.units(10), 100_000)))
        assertEquals("impossible_value", rule(Command.SetBudget(co, Department.LEGAL, Money.units(-1))))
        assertEquals("impossible_value", rule(Command.BuyShares(co, CompanyId(2), 0)))
        assertEquals("impossible_value", rule(Command.SellShares(co, CompanyId(2), -4)))
    }

    @Test fun absurdAmountsAreImpossible() {
        assertEquals("impossible_value", rule(Command.Borrow(co, Money.units(2_000_000_000_000L), 30)))
        assertEquals("impossible_value", rule(Command.Ship(co, app.many.economytycoon.logistics.StockRef(app.many.economytycoon.logistics.StockKind.values()[0], 1), app.many.economytycoon.logistics.StockRef(app.many.economytycoon.logistics.StockKind.values()[0], 2), GoodId("a"), Quantity(Long.MAX_VALUE), null)))
    }

    @Test fun ordersNeedPositiveQuantityAndSaneTimeToLive() {
        fun order(q: Long, ttl: Int, limit: Price? = null) = Command.PlaceOrder(co, FacilityId(1), Side.BUY, GoodId("w"), Quantity(q), limit, ttl)
        assertNull(rule(order(1000, 5)))
        assertEquals("impossible_value", rule(order(0, 5)))
        assertEquals("impossible_value", rule(order(1000, 0)))
        assertEquals("impossible_value", rule(order(1000, 400)))
        assertEquals("impossible_value", rule(order(1000, 5, Price.of(-1))))
    }

    @Test fun ratesAreBoundedToTheirRange() {
        assertNull(rule(Command.SetQuality(co, FacilityId(1), 1_000_000)))
        assertEquals("impossible_value", rule(Command.SetQuality(co, FacilityId(1), 1_000_001)))
        assertEquals("impossible_value", rule(Command.SetQuality(co, FacilityId(1), -1)))
        assertEquals("impossible_value", rule(Command.ListCompany(co, 0)))
        assertEquals("impossible_value", rule(Command.ListCompany(co, 2_000_000)))
    }

    @Test fun namesWithControlCharactersOrBadLengthAreImpossible() {
        assertEquals("impossible_value", rule(Command.Rename(co, "ab")))
        assertEquals("impossible_value", rule(Command.Rename(co, "a\nb c")))
        assertEquals("impossible_value", rule(Command.Rename(co, "x".repeat(41))))
    }
}
