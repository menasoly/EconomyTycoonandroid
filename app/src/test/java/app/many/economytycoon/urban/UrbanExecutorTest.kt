package app.many.economytycoon.urban

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrbanExecutorTest {
    private val T = UrbanTestSupport
    private val firm = CompanyId(5)
    private val now = Tick(0)

    private fun rule(o: app.many.economytycoon.core.Outcome<*>) = o.rejectionOrNull()?.rule
    private fun exec(svc: UrbanService, cmd: UrbanCommand, at: Long = 0) = svc.execute(cmd, Tick(at))
    private fun residential(svc: UrbanService) = T.plan(svc).districts.first { it.zone == ZoneUse.RESIDENTIAL }

    @Test fun `tax rates and service levels are set`() {
        val svc = T.withCity()
        val rates = TaxRates(propertyPerYearPpm = 12_000L, incomePpm = 30_000L)
        assertTrue(exec(svc, UrbanCommand.SetTaxRates(T.city, rates)).isOk)
        assertEquals(rates, T.plan(svc).budget.rates)
        assertTrue(exec(svc, UrbanCommand.SetServiceLevel(T.city, Service.PARKS, 900)).isOk)
        assertEquals(900, T.plan(svc).budget.level(Service.PARKS))
        assertEquals(Rules.INVALID_ARGUMENT, rule(exec(svc, UrbanCommand.SetServiceLevel(T.city, Service.PARKS, 2000))))
        assertEquals(900, T.plan(svc).budget.level(Service.PARKS))
    }

    @Test fun `density rules and tenant protection apply to a district`() {
        val svc = T.withCity()
        val d = residential(svc)
        assertTrue(exec(svc, UrbanCommand.SetDensityRules(T.city, d.index, DensityRules(1_200, 4))).isOk)
        assertEquals(1_200, d.rules.maxFarMilli)
        assertTrue(exec(svc, UrbanCommand.SetTenantProtection(T.city, d.index, 700)).isOk)
        assertEquals(700, d.tenantProtectionPermille)
        assertEquals(Rules.INVALID_ARGUMENT, rule(exec(svc, UrbanCommand.SetTenantProtection(T.city, d.index, 1500))))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(exec(svc, UrbanCommand.SetTenantProtection(T.city, 99, 100))))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(exec(svc, UrbanCommand.SetDensityRules(T.city, -1, DensityRules(1000, 2)))))
    }

    @Test fun `roads and lines are added with fresh ids`() {
        val svc = T.withCity()
        val net = T.plan(svc).network
        val missing = (0 until 9).flatMap { a -> (a + 1 until 9).map { a to it } }.first { (a, b) -> net.roadBetween(a, b) == null }
        val road = exec(svc, UrbanCommand.AddRoad(T.city, missing.first, missing.second, RoadClass.ARTERIAL, 2)).getOrThrow()
        assertNotNull(net.road(LinkId(road.created!!)))
        assertEquals(Rules.DUPLICATE, rule(exec(svc, UrbanCommand.AddRoad(T.city, missing.second, missing.first, RoadClass.ARTERIAL, 2))))
        val line = exec(svc, UrbanCommand.PlanLine(T.city, "Ring", TransitMode.BUS, listOf(0, 1, 2), 600, Money.units(1), false)).getOrThrow()
        assertEquals(LineStatus.PLANNED, net.line(TransitLineId(line.created!!))?.status)
        assertEquals(Rules.NAME_TAKEN, rule(exec(svc, UrbanCommand.PlanLine(T.city, "Ring", TransitMode.BUS, listOf(1, 2), 600, Money.units(1), false))))
    }

    @Test fun `headway and fare changes reach the line`() {
        val svc = T.withCity()
        val line = T.plan(svc).network.lines().first()
        assertTrue(exec(svc, UrbanCommand.SetLineHeadway(T.city, line.id, 240)).isOk)
        assertEquals(240, line.headwaySeconds)
        assertTrue(exec(svc, UrbanCommand.SetLineFare(T.city, line.id, Money.cents(80))).isOk)
        assertEquals(Money.cents(80), line.fare)
        assertEquals(Rules.INVALID_ARGUMENT, rule(exec(svc, UrbanCommand.SetLineHeadway(T.city, line.id, 5))))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(exec(svc, UrbanCommand.SetLineFare(T.city, TransitLineId(99), Money.ZERO))))
    }

    @Test fun `a line can be suspended and resumed but a planned one cannot`() {
        val svc = T.withCity()
        val net = T.plan(svc).network
        val active = net.activeLines().first()
        assertTrue(exec(svc, UrbanCommand.ToggleLine(T.city, active.id)).isOk)
        assertEquals(LineStatus.SUSPENDED, active.status)
        assertTrue(exec(svc, UrbanCommand.ToggleLine(T.city, active.id)).isOk)
        assertEquals(LineStatus.ACTIVE, active.status)
        val planned = exec(svc, UrbanCommand.PlanLine(T.city, "Later", TransitMode.BUS, listOf(3, 4), 600, Money.units(1), false)).getOrThrow()
        assertEquals(Rules.WRONG_STATE, rule(exec(svc, UrbanCommand.ToggleLine(T.city, TransitLineId(planned.created!!)))))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(exec(svc, UrbanCommand.ToggleLine(T.city, TransitLineId(99)))))
    }

    @Test fun `a transit project builds a planned line and puts it in service`() {
        val svc = T.withCity()
        val plan = T.plan(svc)
        plan.budget.restore(plan.budget.rates, Money.units(2_000_000_000), Money.ZERO, 40_000L, Money.ZERO, 0, plan.budget.levelsList(), plan.budget.qualityList())
        val line = exec(svc, UrbanCommand.PlanLine(T.city, "Blue", TransitMode.METRO, listOf(0, 4, 8), 240, Money.units(2), true)).getOrThrow()
        val id = TransitLineId(line.created!!)
        val r = exec(svc, UrbanCommand.StartProject(T.city, ProjectKind.TRANSIT_LINE, "Blue line", null, id, null, 0L, true)).getOrThrow()
        assertEquals(LineStatus.UNDER_CONSTRUCTION, plan.network.line(id)?.status)
        assertEquals(plan.network.line(id)!!.buildCost(), plan.projects.get(CapitalProjectId(r.created!!))!!.originalBudget)
        assertEquals(Rules.WRONG_STATE, rule(exec(svc, UrbanCommand.StartProject(T.city, ProjectKind.TRANSIT_LINE, "Again", null, id, null, 0L, true))))
        val notices = ArrayList<UrbanNotice>()
        T.run(svc, 0, 1500, collect = notices)
        assertEquals(LineStatus.ACTIVE, plan.network.line(id)?.status)
        assertTrue(notices.any { it.kind == NoticeKind.PROJECT_DONE })
    }

    @Test fun `projects check their inputs`() {
        val svc = T.withCity()
        val d = residential(svc)
        fun start(kind: ProjectKind, district: Int? = d.index, size: Long = 0L, line: TransitLineId? = null, road: LinkId? = null) =
            exec(svc, UrbanCommand.StartProject(T.city, kind, "P", district, line, road, size, false))
        assertEquals(Rules.INVALID_ARGUMENT, rule(start(ProjectKind.PARK, district = null)))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(start(ProjectKind.PARK, district = 40)))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(start(ProjectKind.TRANSIT_LINE, line = TransitLineId(77))))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(start(ProjectKind.ROAD_UPGRADE, district = null, road = LinkId(77))))
        assertEquals(Rules.INVALID_ARGUMENT, rule(start(ProjectKind.HOUSING_PROGRAM)))
        assertEquals(Rules.LIMIT_EXCEEDED, rule(start(ProjectKind.HOUSING_PROGRAM, size = d.maxFloorArea * 5)))
        assertTrue(start(ProjectKind.PARK).isOk)
    }

    @Test fun `a poor city cannot start a big project`() {
        val svc = T.withCity()
        val plan = T.plan(svc)
        plan.budget.restore(plan.budget.rates, Money.units(1_000), Money.ZERO, 40_000L, Money.ZERO, 0, plan.budget.levelsList(), plan.budget.qualityList())
        val r = exec(svc, UrbanCommand.StartProject(T.city, ProjectKind.HOSPITAL, "Hospital", residential(svc).index, null, null, 0L, false))
        assertEquals(Rules.INSUFFICIENT_FUNDS, rule(r))
        assertTrue(plan.projects.running().isEmpty())
    }

    @Test fun `cancelling a line project returns the line to planned`() {
        val svc = T.withCity()
        val plan = T.plan(svc)
        plan.budget.restore(plan.budget.rates, Money.units(2_000_000_000), Money.ZERO, 40_000L, Money.ZERO, 0, plan.budget.levelsList(), plan.budget.qualityList())
        val line = TransitLineId(exec(svc, UrbanCommand.PlanLine(T.city, "Red", TransitMode.RAIL, listOf(0, 4), 600, Money.units(2), true)).getOrThrow().created!!)
        val p = exec(svc, UrbanCommand.StartProject(T.city, ProjectKind.TRANSIT_LINE, "Red", null, line, null, 0L, false)).getOrThrow()
        assertTrue(exec(svc, UrbanCommand.CancelProject(T.city, CapitalProjectId(p.created!!))).isOk)
        assertEquals(LineStatus.PLANNED, plan.network.line(line)?.status)
        assertEquals(Rules.WRONG_STATE, rule(exec(svc, UrbanCommand.CancelProject(T.city, CapitalProjectId(p.created!!)))))
    }

    @Test fun `borrowing and repaying go through the budget`() {
        val svc = T.withCity()
        val b = T.plan(svc).budget
        val cash = b.cash
        assertEquals(Rules.INVALID_ARGUMENT, rule(exec(svc, UrbanCommand.Borrow(T.city, Money.ZERO))))
        assertTrue(exec(svc, UrbanCommand.Borrow(T.city, Money.units(1_000_000))).isOk)
        assertEquals(cash + Money.units(1_000_000), b.cash)
        assertTrue(exec(svc, UrbanCommand.Repay(T.city, Money.units(1_000_000))).isOk)
        assertEquals(Money.ZERO, b.debt)
        assertEquals(Rules.CREDIT_LIMIT, rule(exec(svc, UrbanCommand.Borrow(T.city, Money.units(9_000_000_000_000)))))
    }

    @Test fun `a petition from a company carries a fee and the city's own costs nothing`() {
        val svc = T.withCity()
        val d = residential(svc)
        val own = exec(svc, UrbanCommand.FilePetition(T.city, d.index, PetitionKind.REZONE, ZoneUse.MIXED, 0, 0, null)).getOrThrow()
        assertTrue(own.intents.isEmpty())
        val other = T.plan(svc).districts.first { it.zone == ZoneUse.RESIDENTIAL && it.index != d.index }
        val r = exec(svc, UrbanCommand.FilePetition(T.city, other.index, PetitionKind.REZONE, ZoneUse.MIXED, 0, 0, firm)).getOrThrow()
        assertEquals(1, r.intents.size)
        val fee = Zoning.rezoneCost(other.landSqm, other.zone, ZoneUse.MIXED, T.params)
        assertEquals(fee, r.intents[0].total)
        assertEquals(fee, svc.companyCost(UrbanCommand.FilePetition(T.city, other.index, PetitionKind.REZONE, ZoneUse.MIXED, 0, 0, firm)))
        assertEquals(Money.ZERO, svc.companyCost(UrbanCommand.FilePetition(T.city, other.index, PetitionKind.REZONE, ZoneUse.MIXED, 0, 0, null)))
    }

    @Test fun `petition fees count as city revenue in the month`() {
        val svc = T.withCity()
        val d = residential(svc)
        val r = exec(svc, UrbanCommand.FilePetition(T.city, d.index, PetitionKind.REZONE, ZoneUse.MIXED, 0, 0, firm)).getOrThrow()
        assertEquals(r.intents[0].total, T.plan(svc).feesThisMonth)
        svc.step(Tick(30), true) { T.inputs() }
        assertEquals(Money.ZERO, T.plan(svc).feesThisMonth)
    }

    @Test fun `a rejected petition costs nothing`() {
        val svc = T.withCity()
        val d = residential(svc)
        assertTrue(exec(svc, UrbanCommand.FilePetition(T.city, d.index, PetitionKind.REZONE, ZoneUse.MIXED, 0, 0, firm)).isOk)
        val fees = T.plan(svc).feesThisMonth
        assertEquals(Rules.DUPLICATE, rule(exec(svc, UrbanCommand.FilePetition(T.city, d.index, PetitionKind.REZONE, ZoneUse.COMMERCIAL, 0, 0, firm))))
        assertEquals(fees, T.plan(svc).feesThisMonth)
        assertEquals(Rules.UNKNOWN_ENTITY, rule(exec(svc, UrbanCommand.FilePetition(T.city, 99, PetitionKind.REZONE, ZoneUse.MIXED, 0, 0, firm))))
    }

    @Test fun `lobbying intents have unique keys even after a reload of the efforts`() {
        val svc = T.withCity()
        val d = residential(svc)
        val p = exec(svc, UrbanCommand.FilePetition(T.city, d.index, PetitionKind.REZONE, ZoneUse.MIXED, 0, 0, firm)).getOrThrow()
        val id = PetitionId(p.created!!)
        val keys = HashSet<String>()
        repeat(3) {
            val r = exec(svc, UrbanCommand.LobbyPetition(T.city, id, firm, null, Money.units(10_000))).getOrThrow()
            assertEquals(1, r.intents.size)
            assertTrue(keys.add(r.intents[0].key))
        }
        assertEquals(3, keys.size)
        assertEquals(Money.units(10_000), svc.companyCost(UrbanCommand.LobbyPetition(T.city, id, firm, null, Money.units(10_000))))
        assertEquals(Rules.NOT_OWNER, rule(exec(svc, UrbanCommand.LobbyPetition(T.city, id, CompanyId(9), null, Money.units(1_000)))))
    }

    @Test fun `withdrawing needs the applicant`() {
        val svc = T.withCity()
        val d = residential(svc)
        val p = exec(svc, UrbanCommand.FilePetition(T.city, d.index, PetitionKind.REZONE, ZoneUse.MIXED, 0, 0, firm)).getOrThrow()
        val id = PetitionId(p.created!!)
        assertEquals(Rules.NOT_OWNER, rule(exec(svc, UrbanCommand.WithdrawPetition(T.city, id, null))))
        assertTrue(exec(svc, UrbanCommand.WithdrawPetition(T.city, id, firm)).isOk)
        assertTrue(T.plan(svc).petitions.open().isEmpty())
    }

    private fun roadToll(svc: UrbanService) = T.plan(svc).network.roads().first()

    private fun tender(svc: UrbanService, road: LinkId? = roadToll(svc).id, model: PaymentModel = PaymentModel.TOLL, build: Int = 0) =
        exec(svc, UrbanCommand.TenderConcession(T.city, ConcessionKind.TOLL_ROAD, "Toll way", road, null, model, 240, build, Money.units(50_000_000), Money.units(2), if (model == PaymentModel.TOLL) Money.ZERO else Money.units(300_000), 200_000L, 0L))

    @Test fun `a concession is tendered, bid for and awarded`() {
        val svc = T.withCity()
        val c = tender(svc).getOrThrow()
        val id = ConcessionId(c.created!!)
        assertEquals(Rules.UNKNOWN_ENTITY, rule(tender(svc, road = LinkId(999))))
        assertEquals(Rules.DUPLICATE, rule(tender(svc)))
        assertTrue(exec(svc, UrbanCommand.BidConcession(T.city, id, firm, 120_000L), 1).isOk)
        assertEquals(Rules.DUPLICATE, rule(exec(svc, UrbanCommand.BidConcession(T.city, id, firm, 130_000L), 2)))
        val notices = ArrayList<UrbanNotice>()
        T.run(svc, 0, 60, collect = notices)
        assertEquals(ConcessionStatus.OPERATING, T.plan(svc).concessions.get(id)?.status)
        assertEquals(Money.units(2), T.plan(svc).network.road(roadToll(svc).id)?.toll)
        assertTrue(notices.any { it.kind == NoticeKind.CONCESSION_AWARDED })
        assertTrue(notices.any { it.kind == NoticeKind.CONCESSION_OPENED })
    }

    @Test fun `ending a concession removes the toll and owes compensation`() {
        val svc = T.withCity()
        val id = ConcessionId(tender(svc).getOrThrow().created!!)
        exec(svc, UrbanCommand.BidConcession(T.city, id, firm, 0L), 1)
        T.run(svc, 0, 50)
        T.plan(svc).concessions.get(id)!!.let { it.capexPaid = Money.units(50_000_000) }
        val r = exec(svc, UrbanCommand.TerminateConcession(T.city, id), 100).getOrThrow()
        assertEquals(1, r.intents.size)
        assertTrue(r.intents[0].total.isPositive)
        assertEquals(Money.ZERO, T.plan(svc).network.road(roadToll(svc).id)?.toll)
        assertEquals(Rules.WRONG_STATE, rule(exec(svc, UrbanCommand.TerminateConcession(T.city, id), 101)))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(exec(svc, UrbanCommand.TerminateConcession(T.city, ConcessionId(55)), 101)))
    }

    @Test fun `a concession with no bids ends without a toll`() {
        val svc = T.withCity()
        val id = ConcessionId(tender(svc).getOrThrow().created!!)
        val notices = ArrayList<UrbanNotice>()
        T.run(svc, 0, 60, collect = notices)
        assertEquals(ConcessionStatus.NO_BIDS, T.plan(svc).concessions.get(id)?.status)
        assertTrue(notices.any { it.kind == NoticeKind.CONCESSION_NO_BIDS })
        assertEquals(Money.ZERO, roadToll(svc).toll)
    }

    @Test fun `a line cannot be built by the city and tendered at once`() {
        val svc = T.withCity()
        val plan = T.plan(svc)
        plan.budget.restore(plan.budget.rates, Money.units(2_000_000_000), Money.ZERO, 40_000L, Money.ZERO, 0, plan.budget.levelsList(), plan.budget.qualityList())
        val line = TransitLineId(exec(svc, UrbanCommand.PlanLine(T.city, "Gold", TransitMode.METRO, listOf(0, 4, 8), 240, Money.units(2), true)).getOrThrow().created!!)
        exec(svc, UrbanCommand.TenderConcession(T.city, ConcessionKind.TRANSIT_LINE, "Gold line", null, line, PaymentModel.MIXED, 300, 24, Money.units(900_000_000), Money.ZERO, Money.units(2_000_000), 300_000L, 0L)).getOrThrow()
        assertEquals(Rules.DUPLICATE, rule(exec(svc, UrbanCommand.StartProject(T.city, ProjectKind.TRANSIT_LINE, "Gold", null, line, null, 0L, false))))
        val other = TransitLineId(exec(svc, UrbanCommand.PlanLine(T.city, "Silver", TransitMode.METRO, listOf(1, 4, 7), 240, Money.units(2), true)).getOrThrow().created!!)
        exec(svc, UrbanCommand.StartProject(T.city, ProjectKind.TRANSIT_LINE, "Silver", null, other, null, 0L, false)).getOrThrow()
        assertEquals(Rules.DUPLICATE, rule(exec(svc, UrbanCommand.TenderConcession(T.city, ConcessionKind.TRANSIT_LINE, "Silver line", null, other, PaymentModel.MIXED, 300, 24, Money.units(1), Money.ZERO, Money.units(2_000_000), 300_000L, 0L))))
    }

    @Test fun `ids are never reused across commands`() {
        val svc = T.withCity()
        val ids = HashSet<Long>()
        repeat(4) { i ->
            val d = T.plan(svc).districts[i]
            val r = exec(svc, UrbanCommand.FilePetition(T.city, d.index, PetitionKind.REZONE, ZoneUse.MIXED, 0, 0, null))
            if (r.isOk) assertTrue(ids.add(r.getOrThrow().created!!))
        }
        assertTrue(ids.size >= 2)
        assertFalse(ids.isEmpty())
    }
}
