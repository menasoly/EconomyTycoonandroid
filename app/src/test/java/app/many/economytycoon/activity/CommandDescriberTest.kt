package app.many.economytycoon.activity

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Price
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.markets.Side
import app.many.economytycoon.persistence.CommandSamples
import app.many.economytycoon.simulation.Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandDescriberTest {
    private val c = CompanyId(7)
    private val f = FacilityId(3)
    private val names = DescribeNames(
        company = { "Company #${it.value}" }, facility = { "Farm ${it.value}" }, good = { it.key.uppercase() },
        money = { "$" + it.wholeUnits() }, quantity = { "${it.raw / 1000}u" }, city = { "City ${it.value}" }, region = { "Region ${it.value}" },
    )
    private fun say(cmd: Command) = CommandDescriber.describe(cmd, names)

    @Test fun everyKindOfCommandHasADescriptionAndAnArea() {
        for (cmd in CommandSamples.all()) {
            val text = CommandDescriber.describe(cmd)
            assertTrue(cmd.toString(), text.isNotBlank() && text.length > 8)
            assertFalse(text.contains("Command."))
            CommandDescriber.area(cmd)
        }
    }

    @Test fun descriptionsStartWithACapitalAndHaveNoTrailingStop() {
        for (cmd in CommandSamples.all()) {
            val text = CommandDescriber.describe(cmd)
            assertTrue(text, text.first().isUpperCase())
            assertFalse(text, text.endsWith("."))
        }
    }

    @Test fun namesAndAmountsComeFromTheLookup() {
        assertEquals("Bid for 10u of WHEAT at $21 for Farm 3", say(Command.PlaceOrder(c, f, Side.BUY, GoodId("wheat"), Quantity.units(10), Price(Money.units(21)))))
        assertEquals("Offered 10u of WHEAT at market for Farm 3", say(Command.PlaceOrder(c, f, Side.SELL, GoodId("wheat"), Quantity.units(10), null)))
        assertEquals("Borrowed $10000 for 180 days", say(Command.Borrow(c, Money.units(10_000), 180)))
        assertEquals("Bought 1250 shares of Company #9", say(Command.BuyShares(c, CompanyId(9), 1_250)))
    }

    @Test fun plainNamesWorkWithoutALookup() {
        val s = CommandDescriber.describe(Command.Shutdown(c, f))
        assertEquals("Shut down facility 3", s)
        assertEquals("Started building grain farm in region 1", CommandDescriber.describe(Command.Build(c, FacilityTypeId("grain_farm"), RegionId(1))))
    }

    @Test fun optionalPartsAppearOnlyWhenSet() {
        assertEquals("Started building grain farm in Region 1 called North field", say(Command.Build(c, FacilityTypeId("grain_farm"), RegionId(1), name = "North field")))
        assertTrue(say(Command.SetStaffing(c, f, app.many.economytycoon.core.RoleId("farmhand"), 12, Money.units(60))).endsWith("paying $60"))
        assertFalse(say(Command.SetStaffing(c, f, app.many.economytycoon.core.RoleId("farmhand"), 12)).contains("paying"))
        assertEquals("Repaid $500", say(Command.Repay(c, null, Money.units(500))))
        assertEquals("Repaid $500 on loan 2", say(Command.Repay(c, app.many.economytycoon.core.LoanId(2), Money.units(500))))
    }

    @Test fun clearingARecipeSaysSo() {
        assertEquals("Cleared line 2 of Farm 3", say(Command.SetRecipe(c, f, app.many.economytycoon.core.LineId(2), null)))
        assertEquals("Set line 2 of Farm 3 to grow wheat", say(Command.SetRecipe(c, f, app.many.economytycoon.core.LineId(2), RecipeId("grow_wheat"))))
    }

    @Test fun percentagesDropTheDecimalWhenWhole() {
        assertEquals("Set quality assurance at Farm 3 to 25%", say(Command.SetQuality(c, f, 250_000)))
        assertEquals("Set quality assurance at Farm 3 to 12.5%", say(Command.SetQuality(c, f, 125_000)))
    }

    @Test fun eachResponseToAUnionReadsDifferently() {
        val r = app.many.economytycoon.unions.ResponseKind.values().map { say(Command.RespondDemand(c, 4, it, 35_000)) }
        assertEquals(r.size, r.toSet().size)
    }

    @Test fun buyingAndSellingBondsReadDifferently() {
        val b = app.many.economytycoon.core.BondId(3)
        assertTrue(say(Command.TradeBond(c, b, 10, true)).startsWith("Bought"))
        assertTrue(say(Command.TradeBond(c, b, 10, false)).startsWith("Sold"))
    }

    @Test fun areasGroupRelatedCommands() {
        assertEquals(ActivityArea.FACILITIES, CommandDescriber.area(Command.Shutdown(c, f)))
        assertEquals(ActivityArea.FINANCE, CommandDescriber.area(Command.Borrow(c, Money.units(1), 30)))
        assertEquals(ActivityArea.TRADE, CommandDescriber.area(Command.CancelOrder(c, app.many.economytycoon.core.OrderId(1))))
        assertEquals(ActivityArea.PUBLIC, CommandDescriber.area(Command.ApplyGrant(c, app.many.economytycoon.core.GrantId(1))))
        assertEquals(ActivityArea.CORPORATE, CommandDescriber.area(Command.FileInsolvency(c)))
    }

    @Test fun everyAreaIsUsedBySomeCommand() {
        val used = CommandSamples.all().map { CommandDescriber.area(it) }.toSet()
        assertEquals(ActivityArea.values().toSet(), used)
    }

    @Test fun areaLabelsAreDistinct() {
        assertEquals(ActivityArea.values().size, ActivityArea.values().map { it.label }.toSet().size)
    }

    @Test fun spendingCommandsAreFlagged() {
        assertTrue(CommandDescriber.movesMoney(Command.Borrow(c, Money.units(1), 30)))
        assertTrue(CommandDescriber.movesMoney(Command.Build(c, FacilityTypeId("x"), RegionId(1))))
        assertFalse(CommandDescriber.movesMoney(Command.Rename(c, "Name")))
        assertFalse(CommandDescriber.movesMoney(Command.SetBenefits(c, 1)))
    }
}
