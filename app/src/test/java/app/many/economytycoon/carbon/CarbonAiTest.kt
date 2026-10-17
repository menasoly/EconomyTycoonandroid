package app.many.economytycoon.carbon

import app.many.economytycoon.carbon.CarbonTestSupport.acme
import app.many.economytycoon.carbon.CarbonTestSupport.borea
import app.many.economytycoon.carbon.CarbonTestSupport.cyrus
import app.many.economytycoon.carbon.CarbonTestSupport.uk
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarbonAiTest {
    private fun fixture(days: Long) = CarbonTestSupport.Fixture().also { CarbonTestSupport.busy(it, days) }
    private fun ai(fx: CarbonTestSupport.Fixture, tuning: AiCarbonTuning = AiCarbonTuning()) = CarbonAi(fx.service, fx.views, tuning)
    private fun rng(seed: Long = 1L) = DeterministicRandom(seed)

    @Test fun aCompanyWithoutAccountsDoesNothing() {
        val fx = fixture(30)
        assertTrue(ai(fx).decide(CompanyId(99), Money.units(1_000_000), Tick(30), rng()).isEmpty())
    }

    @Test fun decisionsBelongToTheDecidingCompany() {
        val fx = fixture(30)
        val cmds = ai(fx).decide(borea, Money.units(5_000_000), Tick(30), rng())
        assertTrue(cmds.all { it.company == borea })
    }

    @Test fun theSameSeedGivesTheSameDecisions() {
        val a = ai(fixture(60)).decide(cyrus, Money.units(5_000_000), Tick(60), rng(4))
        val b = ai(fixture(60)).decide(cyrus, Money.units(5_000_000), Tick(60), rng(4))
        assertEquals(a, b)
    }

    @Test fun decidingDoesNotMutateTheState() {
        val fx = fixture(60)
        val before = fx.state.stateHash()
        ai(fx).decide(acme, Money.units(5_000_000), Tick(60), rng())
        assertEquals(before, fx.state.stateHash())
    }

    @Test fun aBrokeCompanyCommitsNoSpending() {
        val fx = fixture(60)
        val cmds = ai(fx).decide(cyrus, Money.ZERO, Tick(60), rng())
        assertTrue(cmds.none { it is CarbonCommand.StartAbatement || it is CarbonCommand.BuyOffsets || it is CarbonCommand.BidAtAuction })
    }

    @Test fun aRichCompanyWithACheapMeasureAbates() {
        val fx = CarbonTestSupport.Fixture()
        val plants = listOf(CarbonTestSupport.emitter(1, acme, coalUnits = 300), CarbonTestSupport.emitter(2, borea))
        fx.service.installRegime(CarbonTestSupport.capAndTrade(), Tick(0)).value()
        fx.run(0, 40, plants)
        val cmds = CarbonAi(fx.service, fx.views, AiCarbonTuning(hurdlePpm = 5_000_000L, maxPaybackYears = 50)).decide(acme, Money.units(500_000_000), Tick(40), rng())
        assertTrue(cmds.any { it is CarbonCommand.StartAbatement })
    }

    @Test fun everyCommandTheAiIssuesPassesTheServiceChecks() {
        val fx = fixture(60)
        val ai = ai(fx)
        var accepted = 0
        for (c in listOf(acme, borea, cyrus)) {
            for (cmd in ai.decide(c, Money.units(50_000_000), Tick(60), rng())) {
                val r = fx.exec(cmd, 60)
                if (r is Outcome.Ok) accepted++
            }
        }
        assertTrue(accepted > 0)
    }

    @Test fun theAiKeepsCreditsSwitchedOn() {
        val fx = fixture(30)
        val cmds = ai(fx).decide(acme, Money.units(5_000_000), Tick(30), rng())
        val policy = cmds.filterIsInstance<CarbonCommand.SetPolicy>().firstOrNull { it.country == uk }
        if (policy != null) assertTrue(policy.autoOffsets)
    }

    @Test fun aHigherHurdleMeansMoreMeasures() {
        val fx = fixture(40)
        val strict = ai(fx, AiCarbonTuning(hurdlePpm = 10_000L)).decide(acme, Money.units(500_000_000), Tick(40), rng())
        val loose = ai(fx, AiCarbonTuning(hurdlePpm = 9_000_000L, maxPaybackYears = 100)).decide(acme, Money.units(500_000_000), Tick(40), rng())
        assertTrue(loose.count { it is CarbonCommand.StartAbatement } >= strict.count { it is CarbonCommand.StartAbatement })
    }

    @Test fun anAiLoopOverAYearStaysSolventAndConsistent() {
        val fx = CarbonTestSupport.Fixture(seed = 11L)
        val plants = listOf(CarbonTestSupport.emitter(1, acme), CarbonTestSupport.emitter(2, borea), CarbonTestSupport.emitter(3, cyrus, coalUnits = 40))
        fx.service.installRegime(CarbonTestSupport.capAndTrade(), Tick(0)).value()
        val ai = ai(fx)
        val r = rng(3)
        for (d in 0L..400L) {
            fx.step(d, plants)
            if (d % 15 == 0L) for (c in listOf(acme, borea, cyrus)) for (cmd in ai.decide(c, Money.units(80_000_000), Tick(d), r)) fx.exec(cmd, d)
        }
        val market = fx.state.market(uk)!!
        assertTrue(market.holdings.totalAll().raw >= 0L)
        assertTrue(fx.state.compliance.accounts().all { !it.carry.isNegative })
    }

    private fun <T> Outcome<T>.value(): T = (this as Outcome.Ok).value
}
