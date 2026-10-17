package app.many.economytycoon.simulation

import app.many.economytycoon.activity.ActivityArea
import app.many.economytycoon.activity.CommandDescriber
import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.persistence.CommandCodec
import app.many.economytycoon.persistence.CommandSamples
import app.many.economytycoon.persistence.SectionedFile
import app.many.economytycoon.persistence.SimulationSnapshot
import app.many.economytycoon.tourism.AttractionKind
import app.many.economytycoon.tourism.DestinationId
import app.many.economytycoon.tourism.PropertyId
import app.many.economytycoon.tourism.PropertyKind
import app.many.economytycoon.tourism.RatePolicy
import app.many.economytycoon.tourism.TourismCommand
import app.many.economytycoon.tourism.VisitorSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tourism as the simulation wires it: the switch, commands, payments, the day and the month, the world inputs, snapshots and replay. */
class TourismIntegrationTest {
    private class Rig(seed: Long = 12L, ai: Int = 3, tourism: Boolean = true) {
        val sim = Simulation(SimTestSupport.config(seed, ai), SimTestSupport.content, bootstrap = ai > 0)
        val e = sim.economy
        val country = e.world.countries().sortedBy { it.id }.first()
        val me: CompanyId = CompanyId((sim.execute(Actor.Player(1), Command.FoundCompany("Sun Hotels", country.id, null, Money.units(5_000_000))) as Outcome.Ok).value.created!!)
        init { if (tourism) sim.execute(Actor.System, Command.EstablishTourism) }

        fun cash(c: CompanyId = me) = e.finance.ledger.cash(LedgerOwner.company(c))
        fun tourism(cmd: TourismCommand, by: Long = 1L) = sim.execute(Actor.Player(by), Command.Tourism(cmd))
        val dest get() = e.tourism.destinations.first()
        fun build(rooms: List<Int> = listOf(30, 10, 5), kind: PropertyKind = PropertyKind.HOTEL, stars: Int = 3) =
            tourism(TourismCommand.BuildProperty(me, dest.id, kind, stars, rooms, "Sun Inn"))
    }

    @Test fun aGameWithoutTourismLeavesNoStateNoSectionAndNoDay() {
        val r = Rig(tourism = false)
        val summaries = r.sim.advance(40)
        assertFalse(r.e.tourismRunner.active)
        assertTrue(r.e.tourism.isEmpty)
        assertTrue(summaries.all { it.tourismReport == null && it.tourismSettlement == null })
        assertTrue(SectionedFile.read(SimulationSnapshot.save(r.sim)).none { it.name == "tourism" })
        val early = r.tourism(TourismCommand.BuildProperty(r.me, DestinationId(1), PropertyKind.HOTEL, 3, listOf(10, 0, 0), "Early Inn"))
        assertEquals(Rules.WRONG_STATE, (early as Outcome.Rejected).rejection.rule)
        assertEquals(1_000_000L, r.e.tourismRunner.liftDemand(1_000_000L, r.e.world.regions().first().id))
    }

    @Test fun onlyTheSystemSetsTourismUpOnceAndTheWorldBecomesAMarket() {
        val r = Rig(tourism = false)
        assertTrue(r.sim.execute(Actor.Player(1), Command.EstablishTourism) is Outcome.Rejected)
        assertFalse(r.e.tourismRunner.active)
        assertTrue(r.sim.execute(Actor.System, Command.EstablishTourism) is Outcome.Ok)
        assertTrue(r.sim.execute(Actor.System, Command.EstablishTourism) is Outcome.Rejected)
        val t = r.e.tourism
        val regions = r.e.world.regions().size
        assertEquals(regions * TourismRunner.DESTINATIONS_PER_REGION, t.destinations.size.coerceAtMost(regions * TourismRunner.DESTINATIONS_PER_REGION))
        assertTrue(t.destinations.isNotEmpty())
        assertEquals(regions + 1, t.origins.size)
        assertTrue(t.origins.any { it.country == null })
        assertTrue(t.destinations.all { t.openPropertiesIn(it.id).isNotEmpty() })
        assertTrue(t.properties.all { it.listedOn.isNotEmpty() })
        assertTrue(t.properties.any { it.owner != null })
        assertTrue(t.properties.mapNotNull { it.owner }.all { r.e.companies.company(it)?.kind == app.many.economytycoon.companies.CompanyKind.AI })
        assertTrue(t.intermediaries.size >= 2 && t.operators.isNotEmpty())
        assertTrue(SectionedFile.read(SimulationSnapshot.save(r.sim)).any { it.name == "tourism" })
    }

    @Test fun theTourismCommandsRoundTripThroughTheCodecAndDescriber() {
        val cs = CommandSamples.all().filter { it is Command.EstablishTourism || it is Command.Tourism }
        assertEquals(27, cs.size)
        for (c in cs) {
            val w = ByteWriter(); CommandCodec.writeCommand(c, w)
            assertEquals(c, CommandCodec.readCommand(ByteReader(w.toByteArray())))
            assertEquals(ActivityArea.TOURISM, CommandDescriber.area(c))
            assertTrue(CommandDescriber.describe(c).isNotBlank())
        }
        val build = cs.first { it is Command.Tourism && it.command is TourismCommand.BuildProperty }
        assertTrue(CommandDescriber.movesMoney(build))
        assertFalse(CommandDescriber.movesMoney(cs.first { it is Command.Tourism && it.command is TourismCommand.SetOverbooking }))
    }

    @Test fun aCorruptTourismCommandIsRefusedRatherThanSkipped() {
        val w = ByteWriter(); CommandCodec.writeCommand(Command.Tourism(TourismCommand.CloseProperty(CompanyId(3), PropertyId(1))), w)
        val bytes = w.toByteArray()
        // The last two bytes are the sub-tag and the property; make the sub-tag unknown.
        bytes[bytes.size - 2] = 90
        val failed = runCatching { CommandCodec.readCommand(ByteReader(bytes)) }.exceptionOrNull()
        assertTrue(failed is ByteReader.CorruptDataException)
    }

    @Test fun buildingChecksCashOwnershipAndThePlaceBeforeAnythingIsCreated() {
        val r = Rig()
        val before = r.cash()
        val ok = r.build()
        assertTrue(ok is Outcome.Ok)
        val mine = r.e.tourism.propertiesOf(r.me)
        assertEquals(1, mine.size)
        val cost = r.e.tourismExecutor.cost(TourismCommand.BuildProperty(r.me, r.dest.id, PropertyKind.HOTEL, 3, listOf(30, 10, 5), "Sun Inn"))
        assertEquals(before - cost, r.cash())
        // A rival may not touch it, and a company that cannot pay gets nothing built.
        val rival = CompanyId((r.sim.execute(Actor.Player(2), Command.FoundCompany("Poor Co", r.country.id, null, Money.units(20_000))) as Outcome.Ok).value.created!!)
        val steal = r.sim.execute(Actor.Player(2), Command.Tourism(TourismCommand.SetRates(rival, mine[0].id, RatePolicy.FIXED, Money.units(1))))
        assertEquals(Rules.NOT_OWNER, (steal as Outcome.Rejected).rejection.rule)
        val broke = r.sim.execute(Actor.Player(2), Command.Tourism(TourismCommand.BuildProperty(rival, r.dest.id, PropertyKind.RESORT, 5, listOf(200, 100, 50), "Palace")))
        assertEquals(Rules.INSUFFICIENT_FUNDS, (broke as Outcome.Rejected).rejection.rule)
        assertTrue(r.e.tourism.propertiesOf(rival).isEmpty())
        val forged = r.sim.execute(Actor.Player(2), Command.Tourism(TourismCommand.CloseProperty(CompanyId(r.me.value), mine[0].id)))
        assertEquals(Rules.NOT_OWNER, (forged as Outcome.Rejected).rejection.rule)
        val nowhere = r.tourism(TourismCommand.BuildProperty(r.me, DestinationId(9_999), PropertyKind.HOTEL, 3, listOf(10, 0, 0), "Lost Inn"))
        assertEquals(Rules.UNKNOWN_ENTITY, (nowhere as Outcome.Rejected).rejection.rule)
    }

    @Test fun aCampaignIsPaidOnceADayAndSpendIsChecked() {
        val r = Rig()
        val before = r.cash()
        val spend = Money.units(20_000)
        assertTrue(r.tourism(TourismCommand.LaunchCampaign(r.me, r.dest.id, VisitorSegment.FAMILY, spend)) is Outcome.Ok)
        assertEquals(before - spend, r.cash())
        val again = r.tourism(TourismCommand.LaunchCampaign(r.me, r.dest.id, null, spend))
        assertEquals(Rules.WRONG_STATE, (again as Outcome.Rejected).rejection.rule)
        assertEquals(before - spend, r.cash())
        val huge = r.tourism(TourismCommand.LaunchCampaign(r.me, r.dest.id, null, Money.units(900_000_000)))
        assertEquals(Rules.INSUFFICIENT_FUNDS, (huge as Outcome.Rejected).rejection.rule)
    }

    @Test fun aHotelOpensTradesAndIsBilledEveryMonthThroughTheLedger() {
        val r = Rig(seed = 21L)
        assertTrue(r.build(listOf(40, 10, 0)) is Outcome.Ok)
        val id = r.e.tourism.propertiesOf(r.me).first().id
        val months = ArrayList<app.many.economytycoon.tourism.SettlementRun>()
        for (s in r.sim.advance(190)) s.tourismSettlement?.let { months += it }
        assertEquals(6, months.size)
        val p = r.e.tourism.property(id)!!
        assertTrue(p.isOpen)
        assertTrue(months.mapNotNull { it.statementOf(id) }.any { it.roomsSold > 0L })
        assertTrue(r.e.tourism.destinations.any { it.days.isNotEmpty() })
        assertEquals(emptyList<String>(), Invariants.check(r.e))
        assertTrue(r.e.finance.invariants().isEmpty())
    }

    @Test fun visitorsSpendingLiftsLocalDemandAndNothingElseMoves() {
        val r = Rig(seed = 5L)
        r.sim.advance(60)
        val regions = r.e.world.regions().map { it.id }
        assertTrue(regions.any { r.e.tourismRunner.liftDemand(1_000_000L, it) >= 1_000_000L })
        assertTrue(regions.all { r.e.tourismRunner.liftDemand(1_000_000L, it) <= 1_000_000L + TourismRunner.MAX_LIFT_PPM })
        assertEquals(0L, r.e.tourismRunner.liftDemand(0L, regions.first()))
    }

    @Test fun transportSeatsFollowTheNetworkAndStaffFollowTheCityLabourForce() {
        val r = Rig(seed = 9L)
        val d = r.dest
        val origin = r.e.tourism.origins.first().id
        val transport = WorldTransport(r.e.world, r.e.tourism)
        val open = transport.seatsPerDay(origin, d.id, Tick(10))
        assertTrue(open > 0L)
        val links = r.e.world.network.touching(d.city)
        for (s in links) r.e.world.network.close(s.id, 1_000L)
        assertTrue(transport.seatsPerDay(origin, d.id, Tick(10)) <= open)
        if (links.isNotEmpty()) assertTrue(transport.seatsPerDay(origin, d.id, Tick(10)) < open)
        assertEquals(0L, transport.seatsPerDay(origin, DestinationId(9_999), Tick(10)))

        val staff = WorldStaffSupply(r.e.world, r.e.tourism)
        assertEquals(10, staff.available(null, d.id, 10, Tick(1)))
        assertEquals(0, staff.available(null, d.id, 0, Tick(1)))
        // A hotel far larger than the town can staff leaves its business short.
        r.e.tourismService.addProperty(d.id, null, null, PropertyKind.RESORT, 5, intArrayOf(300, 300, 200), Tick(1), "Mega", null, instant = true)
        r.e.tourismService.addProperty(d.id, null, null, PropertyKind.RESORT, 5, intArrayOf(300, 300, 200), Tick(1), "Mega II", null, instant = true)
        val got = staff.available(null, d.id, 100, Tick(1))
        assertTrue("staff $got", got in 0..100)
        val pool = r.e.world.population(d.city).laborForce * WorldStaffSupply.POOL_SHARE_PPM / 1_000_000L
        val peak = r.e.tourism.openPropertiesIn(d.id).sumOf { app.many.economytycoon.tourism.Staffing.needed(it.kind, it.stars, it.totalRooms, it.totalRooms).toLong() }
        if (peak > pool) assertTrue(got < 100)
    }

    @Test fun computerCompaniesStartWithHotelsAndTheirHoteliersManageThemWithoutBreakingTheBooks() {
        val r = Rig(seed = 14L, ai = 6)
        val ai = r.e.companies.all().filter { it.kind == app.many.economytycoon.companies.CompanyKind.AI }
        assertTrue(ai.isNotEmpty())
        assertTrue("some computer company runs a hotel", ai.any { r.e.tourism.propertiesOf(it.id).isNotEmpty() })
        val before = r.e.tourism.stateHash()
        val report = AiHoteliers(r.e).run(Tick(35))
        assertTrue(report.moves > 0 && report.applied > 0)
        assertNotEquals(before, r.e.tourism.stateHash())
        assertEquals(0, AiHoteliers(r.e).run(Tick(36)).moves)
        r.sim.advance(240)
        assertEquals(emptyList<String>(), Invariants.check(r.e))
        assertTrue(ai.any { r.e.tourism.propertiesOf(it.id).any { p -> p.days.isNotEmpty() } })
    }

    @Test fun aCompanyThatCeasesToExistClosesItsHotels() {
        val r = Rig(seed = 17L)
        r.build(); r.tourism(TourismCommand.BuildAttraction(r.me, r.dest.id, AttractionKind.MUSEUM, "Sun Museum", 400, Money.units(10)))
        r.sim.advance(5)
        assertTrue(r.sim.execute(Actor.Player(1), Command.Dissolve(r.me)) is Outcome.Ok)
        r.sim.advance(130)
        assertTrue(r.e.tourism.propertiesOf(r.me).none { it.isOpen })
        assertTrue(r.e.tourism.attractionsOf(r.me).none { it.open })
        assertEquals(emptyList<String>(), Invariants.check(r.e))
    }

    @Test fun savingAndLoadingContinuesExactlyWithTourismSetUp() {
        val r = Rig(seed = 31L, ai = 4)
        r.build(listOf(40, 10, 0))
        r.tourism(TourismCommand.LaunchCampaign(r.me, r.dest.id, null, Money.units(10_000)))
        r.sim.advance(75)
        val sections = SectionedFile.read(SimulationSnapshot.save(r.sim))
        assertTrue(sections.first { it.name == "tourism" }.payload.isNotEmpty())
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(r.sim), SimTestSupport.content)
        assertEquals(r.sim.hash(), copy.hash())
        assertEquals(r.e.tourism.stateHash(), copy.economy.tourism.stateHash())
        assertTrue(copy.economy.tourismRunner.active)
        for (i in 0 until 45) { r.sim.advance(); copy.advance(); assertEquals("tick ${r.sim.tick.value}", r.sim.hash(), copy.hash()) }
    }

    @Test fun anOlderSaveWithoutTheSectionLoadsWithNoTourism() {
        val r = Rig(seed = 8L, ai = 2, tourism = false)
        r.sim.advance(10)
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(r.sim), SimTestSupport.content)
        assertFalse(copy.economy.tourismRunner.active)
        assertEquals(r.sim.hash(), copy.hash())
    }

    @Test fun theCommandLogReplaysIdenticallyAndTourismChangesTheHash() {
        val a = Rig(seed = 33L, ai = 4)
        val plain = Rig(seed = 33L, ai = 4, tourism = false)
        a.tourism(TourismCommand.LaunchCampaign(a.me, a.dest.id, VisitorSegment.LUXURY, Money.units(15_000)))
        a.sim.advance(5)
        a.sim.submit(Actor.Player(1), Command.Tourism(TourismCommand.BuildProperty(a.me, a.dest.id, PropertyKind.BOUTIQUE, 4, listOf(20, 10, 4), "Sun Boutique")))
        a.sim.advance(110); plain.sim.advance(115)
        val again = Simulation.replay(a.sim.config, SimTestSupport.content, a.sim.commandLog(), a.sim.tick.value)
        assertEquals(a.sim.hash(), again.hash())
        assertNotEquals(plain.sim.hash(), a.sim.hash())
        assertNotNull(a.e.tourism.propertiesOf(a.me).firstOrNull())
        assertEquals(emptyList<String>(), Invariants.check(a.e))
    }

    @Test fun aMultiMonthRunWithTourismIsDeterministic() {
        val x = Rig(seed = 41L, ai = 5); val y = Rig(seed = 41L, ai = 5)
        for (rig in listOf(x, y)) rig.build()
        x.sim.advance(180); y.sim.advance(180)
        assertEquals(x.sim.hash(), y.sim.hash())
        assertEquals(x.e.tourism.stateHash(), y.e.tourism.stateHash())
        assertTrue(Invariants.check(x.e).isEmpty())
        assertTrue(x.e.finance.invariants().isEmpty())
    }
}
