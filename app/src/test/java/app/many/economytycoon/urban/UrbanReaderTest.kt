package app.many.economytycoon.urban

import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrbanReaderTest {
    private val T = UrbanTestSupport

    @Test fun `an empty service gives an empty view`() {
        val v = UrbanReader.view(T.service(), Tick(5))
        assertTrue(v.cities.isEmpty())
        assertTrue(v.ranking.isEmpty())
        assertNull(v.city(T.city))
        assertEquals(Tick(5), v.now)
    }

    @Test fun `the view mirrors the plan`() {
        val svc = T.withCity()
        T.run(svc, 0, 90)
        val plan = T.plan(svc)
        val c = UrbanReader.cityView(svc, T.city, Tick(90))!!
        assertEquals(plan.name, c.name)
        assertEquals(plan.population, c.population)
        assertEquals(plan.districts.size, c.districts.size)
        assertEquals(plan.network.roads().size, c.roads.size)
        assertEquals(plan.network.lines().size, c.lines.size)
        assertEquals(svc.score(T.city)!!.index, c.liveability)
        assertEquals(1, c.rank)
        assertEquals(1, c.cityCount)
        assertEquals(LiveabilityFactor.values().size, c.factors.size)
        assertEquals(plan.history(), c.liveabilityHistory)
        assertEquals(plan.budget.cash, c.budget.cash)
    }

    @Test fun `district views carry names, zones and rent`() {
        val svc = T.withCity()
        val c = UrbanReader.cityView(svc, T.city, Tick(0))!!
        val plan = T.plan(svc)
        for ((v, d) in c.districts.zip(plan.districts)) {
            assertEquals(d.name, v.name)
            assertEquals(d.zone, v.zone)
            assertEquals(d.rentPerSqm, v.rentPerSqm)
            assertEquals(d.farMilli, v.farMilli)
            assertEquals(d.heritage, v.heritage)
        }
    }

    @Test fun `road and line views use district names`() {
        val c = UrbanReader.cityView(T.withCity(), T.city, Tick(0))!!
        val names = c.districts.map { it.name }.toSet()
        assertTrue(c.roads.all { it.fromName in names && it.toName in names })
        assertTrue(c.lines.all { l -> l.stopNames.all { it in names } && l.lengthMeters > 0L })
    }

    @Test fun `petitions, projects, concessions and events appear`() {
        val svc = T.withCity()
        val plan = T.plan(svc)
        plan.budget.restore(plan.budget.rates, Money.units(3_000_000_000), Money.ZERO, 40_000L, Money.ZERO, 0, plan.budget.levelsList(), plan.budget.qualityList())
        val d = plan.districts.first { it.zone == ZoneUse.RESIDENTIAL }
        svc.execute(UrbanCommand.FilePetition(T.city, d.index, PetitionKind.REZONE, ZoneUse.MIXED, 0, 0, CompanyId(3)), Tick(0)).getOrThrow()
        svc.execute(UrbanCommand.StartProject(T.city, ProjectKind.PARK, "Green lung", d.index, null, null, 0L, false), Tick(0)).getOrThrow()
        svc.execute(UrbanCommand.TenderConcession(T.city, ConcessionKind.TOLL_ROAD, "Toll", plan.network.roads().first().id, null, PaymentModel.TOLL, 240, 0, Money.units(1_000_000), Money.units(2), Money.ZERO, 100_000L, 0L), Tick(0)).getOrThrow()
        plan.events.add(UrbanEvent(UrbanEventId(77), UrbanEventKind.FIRE, d.index, Tick(0), Tick(40), 100))
        val c = UrbanReader.cityView(svc, T.city, Tick(5))!!
        assertEquals(1, c.petitions.size)
        assertEquals(d.name, c.petitions[0].districtName)
        assertEquals(CompanyId(3), c.petitions[0].applicant)
        assertEquals(d.index, c.districts[d.index].index)
        assertEquals(c.petitions[0].id, c.districts[d.index].openPetition)
        assertEquals(1, c.projects.size)
        assertEquals("Green lung", c.projects[0].name)
        assertEquals(1, c.concessions.size)
        assertEquals(1, c.events.size)
        assertTrue(c.events[0].active)
    }

    @Test fun `the council view counts majorities`() {
        val c = UrbanReader.cityView(T.withCity(), T.city, Tick(0))!!
        assertEquals(9, c.council.seats.size)
        assertEquals(5, c.council.majority)
        assertEquals(6, c.council.supermajority)
        assertTrue(c.council.nextElectionIn in 0L..48L * 30L)
    }

    @Test fun `budget view lists every revenue and service line`() {
        val svc = T.withCity()
        T.run(svc, 0, 60)
        val b = UrbanReader.cityView(svc, T.city, Tick(60))!!.budget
        assertTrue(b.hasReport)
        assertEquals(RevenueLine.values().size, b.revenue.size)
        assertEquals(ExpenseLine.values().size, b.expenses.size)
        assertEquals(Service.values().size, b.services.size)
        assertTrue(b.debtLimit.isPositive)
    }

    @Test fun `the view lists several cities in id order with ranks`() {
        val svc = T.withCity()
        svc.addCity(CityId(0).let { CityId(7) }, T.country, "Seven", T.inputs(60_000L), Tick(0)).getOrThrow()
        val v = UrbanReader.view(svc, Tick(0))
        assertEquals(listOf(CityId(1), CityId(7)), v.cities.map { it.city })
        assertEquals(setOf(1, 2), v.cities.map { it.rank }.toSet())
        assertEquals(2, v.ranking.size)
        assertEquals(2, v.cities[0].cityCount)
    }

    @Test fun `reading does not change anything`() {
        val svc = T.withCity()
        val h = svc.stateHash()
        UrbanReader.view(svc, Tick(100))
        assertEquals(h, svc.stateHash())
    }

    @Test fun `unknown cities have no view`() {
        assertNull(UrbanReader.cityView(T.withCity(), CityId(99), Tick(0)))
    }
}
