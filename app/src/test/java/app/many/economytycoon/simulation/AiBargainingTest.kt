package app.many.economytycoon.simulation

import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import app.many.economytycoon.unions.DemandStatus
import app.many.economytycoon.unions.ResponseKind
import app.many.economytycoon.workforce.CohortKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiBargainingTest {
    private class Rig {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        val e = sim.economy
        val country = e.world.countries().first()
        val region = e.world.regionsOf(country.id).first().id
        val role = e.content.allRoles.first().id
        val bargaining = AiBargaining(e, CommandExecutor(e))
        val rivals = (1..6).map { i ->
            CompanyId((sim.execute(Actor.System, Command.FoundCompany("Rival $i", country.id, null, Money.units(1_000_000), CompanyKind.AI)) as Outcome.Ok).value.created!!)
        }
        fun demandFor(c: CompanyId, raise: Long): app.many.economytycoon.unions.Demand {
            val k = e.workforce.ensure(CohortKey(c, FacilityId(1), role, region))
            k.headcount = 40; k.moralePpm = 300_000L; k.target = 40
            val u = (e.unions.form(c, region, 40, Tick(1)) as Outcome.Ok).value
            u.strengthPpm = 500_000L
            return e.unions.makeDemand(u, raise, Tick(5))
        }
    }

    @Test fun temperamentFollowsTheCompanyId() {
        val r = Rig()
        for (c in r.rivals) assertEquals((c.value % 3L).toInt(), r.bargaining.temperament(c))
    }

    @Test fun smallAsksAreAcceptedByEveryone() {
        val r = Rig()
        for (c in r.rivals) {
            val d = r.demandFor(c, 20_000L)
            assertEquals(ResponseKind.ACCEPT, r.bargaining.decide(c, d).first)
        }
    }

    @Test fun counterOffersAreAShareOfTheDemandAndBelowIt() {
        val r = Rig()
        for (c in r.rivals) {
            val d = r.demandFor(c, 90_000L)
            val (kind, counter) = r.bargaining.decide(c, d)
            if (kind == ResponseKind.COUNTER) assertTrue(counter in 1 until 90_000L)
        }
    }

    @Test fun theHardlinersRefuseHugeAsks() {
        val r = Rig()
        val hard = r.rivals.first { r.bargaining.temperament(it) == 2 }
        assertEquals(ResponseKind.REFUSE, r.bargaining.decide(hard, r.demandFor(hard, 130_000L)).first)
    }

    @Test fun runAnswersEveryOpenDemandOfAComputerCompany() {
        val r = Rig()
        val ds = r.rivals.map { r.demandFor(it, 60_000L) }
        val report = r.bargaining.run(Tick(6))
        assertEquals(r.rivals.size, report.accepted + report.countered + report.refused)
        assertTrue(ds.none { it.status == DemandStatus.OPEN })
    }

    @Test fun aHumanCompanyIsLeftAlone() {
        val r = Rig()
        val me = CompanyId((r.sim.execute(Actor.Player(1), Command.FoundCompany("Human Works", r.country.id, null, Money.units(300_000))) as Outcome.Ok).value.created!!)
        val d = r.demandFor(me, 60_000L)
        r.bargaining.run(Tick(6))
        assertTrue(d.isOpen)
        assertFalse(r.bargaining.run(Tick(7)).let { it.accepted + it.countered + it.refused > 0 })
    }

    @Test fun answeringTwiceDoesNothingTheSecondTime() {
        val r = Rig(); val c = r.rivals.first()
        r.demandFor(c, 30_000L)
        val first = r.bargaining.run(Tick(6)); val second = r.bargaining.run(Tick(7))
        assertEquals(1, first.accepted + first.countered + first.refused)
        assertEquals(0, second.accepted + second.countered + second.refused)
    }
}
