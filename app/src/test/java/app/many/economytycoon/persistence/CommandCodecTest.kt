package app.many.economytycoon.persistence

import app.many.economytycoon.companies.BuyRule
import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.companies.Department
import app.many.economytycoon.companies.DividendPolicy
import app.many.economytycoon.companies.PriceStrategy
import app.many.economytycoon.companies.SellRule
import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.DepositId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.LineId
import app.many.economytycoon.core.LoanId
import app.many.economytycoon.core.MachineTypeId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.OrderId
import app.many.economytycoon.core.ParcelId
import app.many.economytycoon.core.Price
import app.many.economytycoon.core.ProjectId
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.TechId
import app.many.economytycoon.core.VehicleId
import app.many.economytycoon.core.VehicleTypeId
import app.many.economytycoon.core.WarehouseId
import app.many.economytycoon.logistics.StockRef
import app.many.economytycoon.markets.Side
import app.many.economytycoon.production.SchedulePolicy
import app.many.economytycoon.simulation.Actor
import app.many.economytycoon.simulation.Command
import app.many.economytycoon.simulation.LoggedCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandCodecTest {
    private val c = CompanyId(7)
    private val f = FacilityId(3)

    private fun every(): List<Command> = CommandSamples.all()

    private fun roundTrip(cmd: Command): Command {
        val w = ByteWriter(); CommandCodec.writeCommand(cmd, w)
        val r = ByteReader(w.toByteArray()); val back = CommandCodec.readCommand(r)
        return back
    }

    @Test fun everyCommandSurvivesARoundTrip() {
        for (cmd in every()) assertEquals(cmd, roundTrip(cmd))
    }

    @Test fun theTestListCoversEveryCommandClass() {
        val covered = every().map { it.javaClass.name }.toSet()
        assertEquals("command classes covered by the codec test", 80, covered.size)
    }

    @Test fun logsKeepOrderActorsAndFlags() {
        val log = listOf(
            LoggedCommand(1, 0, Actor.Player(4), Command.Rename(c, "A Name"), immediate = true),
            LoggedCommand(2, 5, Actor.Ai(CompanyId(3)), Command.Shutdown(CompanyId(3), f)),
            LoggedCommand(3, 9, Actor.System, Command.FileInsolvency(c)),
        )
        val w = ByteWriter(); CommandCodec.writeLog(log, w)
        assertEquals(log, CommandCodec.readLog(ByteReader(w.toByteArray())))
    }

    @Test fun unknownTagsAndTruncationAreRejected() {
        val w = ByteWriter(); w.writeVarInt(99); w.writeVarLong(1)
        var failed = false
        try { CommandCodec.readCommand(ByteReader(w.toByteArray())) } catch (e: ByteReader.CorruptDataException) { failed = true }
        assertTrue(failed)
        val ok = ByteWriter(); CommandCodec.writeCommand(Command.Rename(c, "Truncated Name"), ok)
        val bytes = ok.toByteArray().copyOf(ok.toByteArray().size - 3)
        failed = false
        try { CommandCodec.readCommand(ByteReader(bytes)) } catch (e: ByteReader.CorruptDataException) { failed = true }
        assertTrue(failed)
    }

    @Test fun invalidRulesInTheStreamAreCorruptionNotCrashes() {
        val w = ByteWriter()
        w.writeVarInt(34); w.writeVarLong(1); w.writeVarLong(5_000_000); w.writeVarInt(1); w.writeVarInt(1)
        var failed = false
        try { CommandCodec.readCommand(ByteReader(w.toByteArray())) } catch (e: ByteReader.CorruptDataException) { failed = true }
        assertTrue(failed)
    }

    @Test fun badVersionOrHugeCountsAreRejected() {
        val w = ByteWriter(); w.writeVarInt(9)
        var failed = false
        try { CommandCodec.readLog(ByteReader(w.toByteArray())) } catch (e: ByteReader.CorruptDataException) { failed = true }
        assertTrue(failed)
        val h = ByteWriter(); h.writeVarInt(CommandCodec.VERSION); h.writeVarInt(CommandCodec.MAX_ENTRIES + 1)
        failed = false
        try { CommandCodec.readLog(ByteReader(h.toByteArray())) } catch (e: ByteReader.CorruptDataException) { failed = true }
        assertTrue(failed)
    }
}
