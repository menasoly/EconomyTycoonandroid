package app.many.economytycoon.simulation

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.production.Facility
import app.many.economytycoon.production.FacilityStatus
import app.many.economytycoon.regulation.Law
import app.many.economytycoon.regulation.LawStatus
import app.many.economytycoon.regulation.Lever
import app.many.economytycoon.regulation.RegulationEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegulationRunnerTest {
    private class Rig {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        val e = sim.economy
        val run = e.regulationRunner
        val country = e.world.countries().first()
        val region = e.world.regionsOf(country.id).first().id
        val gov = LedgerOwner.government(country.id)
        init { run.ensureGovernments(e.tick) }
        fun company(name: String, p: Long): CompanyId =
            CompanyId((sim.execute(Actor.Player(p), Command.FoundCompany(name, country.id, null, Money.units(300_000))) as Outcome.Ok).value.created!!)
        fun farm(owner: CompanyId): Facility = e.production.grantFacility(owner, FacilityTypeId("grain_farm"), region, null, null, null, e.tick)
        fun cash(c: CompanyId) = e.finance.ledger.cash(LedgerOwner.company(c))
        fun country() = e.world.country(country.id)
        fun law(l: Lever, raise: Boolean, tick: Long = 0): Law {
            val c = country(); val g = e.regulation.government(c.id)!!
            return (e.regulation.propose(c, l, e.regulation.target(l, l.valueIn(c), raise, g.baselineWage)!!, Tick(tick)) as Outcome.Ok).value
        }
        /** A day on which the country's monthly inspection falls. */
        fun inspectionDay(month: Long) = 30L * month - country.id.value % 30
    }

    @Test fun applyingATaxLawChangesTheCountryAndItsTaxPolicy() {
        for ((lever, read) in listOf<Pair<Lever, (app.many.economytycoon.finance.TaxPolicy) -> Long>>(
            Lever.CORPORATE_TAX to { it.corporateRate.ppm }, Lever.SALES_TAX to { it.salesRate.ppm }, Lever.PAYROLL_TAX to { it.payrollRate.ppm },
        )) {
            val r = Rig()
            val law = r.law(lever, true)
            r.run.apply(law)
            assertEquals(lever.toString(), law.to, lever.valueIn(r.country()))
            assertEquals(lever.toString(), law.to, read(r.e.finance.taxPolicy(r.gov)))
        }
    }

    @Test fun aMinimumWageLawReachesEveryLabourMarketOfTheCountry() {
        val r = Rig(); val law = r.law(Lever.MINIMUM_WAGE, true)
        r.run.apply(law)
        assertEquals(law.to, r.country().minimumWagePerTick)
        for (reg in r.e.world.regionsOf(r.country.id)) assertEquals(Money(law.to * Money.SCALE), r.e.laborMarkets.minimumWage(reg.id))
    }

    @Test fun tariffAndEnvironmentLawsChangeOnlyTheCountry() {
        val r = Rig(); val before = r.e.finance.taxPolicy(r.gov)
        r.run.apply(r.law(Lever.ENVIRONMENT, true)); r.run.apply(r.law(Lever.TARIFF, true))
        assertEquals(before, r.e.finance.taxPolicy(r.gov))
        assertTrue(r.country().environmentalStrictnessPpm > 0 && r.country().tariffPpm >= 10_000L)
    }

    @Test fun aLobbiedLawPassesThenTakesEffectAfterTheNoticePeriod() {
        val r = Rig(); val c = r.company("Lobby Works", 1); val law = r.law(Lever.SALES_TAX, true)
        assertTrue(r.run.lobby(c, law.id, true, Money.units(200_000), r.e.tick) is Outcome.Ok)
        val vote = r.run.run(law.voteAt)
        assertTrue(vote.any { it is RegulationEvent.Passed && it.law == law.id })
        assertEquals(LawStatus.PASSED, law.status)
        assertEquals(law.from, r.country().salesTaxPpm)
        val enacted = r.run.run(law.effectiveAt!!)
        assertTrue(enacted.any { it is RegulationEvent.Enacted && it.law == law.id })
        assertEquals(law.to, r.country().salesTaxPpm)
        assertEquals(LawStatus.ENACTED, law.status)
    }

    @Test fun anOpposedLawIsRejectedAndNothingChanges() {
        val r = Rig(); val c = r.company("Block Works", 1); val law = r.law(Lever.PAYROLL_TAX, true)
        r.run.lobby(c, law.id, false, Money.units(250_000), r.e.tick)
        val events = r.run.run(law.voteAt)
        assertTrue(events.any { it is RegulationEvent.Rejected && it.law == law.id })
        assertEquals(law.from, r.country().payrollTaxPpm)
        assertTrue(r.run.run(Tick(law.voteAt.value + 40)).none { it is RegulationEvent.Enacted && it.law == law.id })
    }

    @Test fun lobbyingMovesMoneyToTheTreasuryAndIsRefusedWhenUnaffordable() {
        val r = Rig(); val c = r.company("Donor Works", 1); val law = r.law(Lever.SALES_TAX, true)
        val cash = r.cash(c); val treasury = r.e.finance.ledger.cash(r.gov)
        assertTrue(r.run.lobby(c, law.id, true, Money.units(5_000), r.e.tick) is Outcome.Ok)
        assertEquals(cash - Money.units(5_000), r.cash(c))
        assertEquals(treasury + Money.units(5_000), r.e.finance.ledger.cash(r.gov))
        assertEquals(Money.units(5_000), law.backedBy(c))
        val broke = r.run.lobby(c, law.id, true, r.cash(c) + Money.units(1), r.e.tick) as Outcome.Rejected
        assertEquals(Rules.INSUFFICIENT_FUNDS, broke.rejection.rule)
        assertEquals(Money.units(5_000), law.backedBy(c))
    }

    @Test fun lobbyingHasAFloorAndNeedsAnOpenLawAndARealCompany() {
        val r = Rig(); val c = r.company("Donor Works", 1); val law = r.law(Lever.SALES_TAX, true)
        assertEquals(Rules.INVALID_ARGUMENT, (r.run.lobby(c, law.id, true, Money.units(10), r.e.tick) as Outcome.Rejected).rejection.rule)
        assertEquals(Rules.UNKNOWN_ENTITY, (r.run.lobby(CompanyId(999), law.id, true, Money.units(5_000), r.e.tick) as Outcome.Rejected).rejection.rule)
        assertEquals(Rules.UNKNOWN_ENTITY, (r.run.lobby(c, app.many.economytycoon.core.RegulationId(999), true, Money.units(5_000), r.e.tick) as Outcome.Rejected).rejection.rule)
        r.run.run(law.voteAt)
        assertEquals(Rules.WRONG_STATE, (r.run.lobby(c, law.id, true, Money.units(5_000), r.e.tick) as Outcome.Rejected).rejection.rule)
    }

    @Test fun theCommandGoesThroughTheExecutorLikeAnyOther() {
        val r = Rig(); val c = r.company("Donor Works", 1); val law = r.law(Lever.SALES_TAX, true)
        val ok = r.sim.execute(Actor.Player(1), Command.LobbyLaw(c, law.id, true, Money.units(3_000)))
        assertTrue(ok.toString(), ok is Outcome.Ok)
        val other = r.sim.execute(Actor.Player(2), Command.LobbyLaw(c, law.id, true, Money.units(3_000)))
        assertTrue(other is Outcome.Rejected)
        assertEquals(Money.units(3_000), law.backedBy(c))
    }

    @Test fun theLimitTightensWithStrictness() {
        val r = Rig(); val c = r.country()
        assertEquals(1_000_000L, r.run.limitFor(c.copy(environmentalStrictnessPpm = 0)))
        assertEquals(200_000L, r.run.limitFor(c.copy(environmentalStrictnessPpm = 1_000_000L)))
        assertEquals(600_000L, r.run.limitFor(c.copy(environmentalStrictnessPpm = 500_000L)))
    }

    @Test fun aFineScalesWithTheOverageAndTheBuildingCost() {
        val r = Rig(); val c = r.company("Smoke Works", 1); val f = r.farm(c)
        val strict = r.country().copy(environmentalStrictnessPpm = 500_000L)
        f.pollutionPpm = 600_000L
        assertEquals(Money.ZERO, r.run.fineFor(f, strict))
        f.pollutionPpm = 700_000L; val small = r.run.fineFor(f, strict)
        f.pollutionPpm = 900_000L; val big = r.run.fineFor(f, strict)
        assertTrue(small >= Money.units(10) && big > small)
    }

    @Test fun inspectorsFineAPollutingFactoryAndPayTheTreasury() {
        val r = Rig(); val c = r.company("Smoke Works", 1); val f = r.farm(c)
        r.e.world.replaceCountry(r.country().copy(environmentalStrictnessPpm = 500_000L))
        f.pollutionPpm = 900_000L
        val cash = r.cash(c); val treasury = r.e.finance.ledger.cash(r.gov)
        val events = r.run.run(Tick(r.inspectionDay(10)))
        val fined = events.filterIsInstance<RegulationEvent.Fined>().single().fine
        assertEquals(c, fined.company); assertEquals(f.id, fined.facility); assertEquals(600_000L, fined.limitPpm); assertFalse(fined.closed)
        assertTrue(fined.amount.raw > 0)
        assertEquals(cash - fined.amount, r.cash(c))
        assertEquals(treasury + fined.amount, r.e.finance.ledger.cash(r.gov))
        assertEquals(FacilityStatus.OPERATING, f.status)
    }

    @Test fun cleanFactoriesAreLeftAlone() {
        val r = Rig(); val c = r.company("Clean Works", 1); val f = r.farm(c)
        f.pollutionPpm = 100_000L
        assertTrue(r.run.run(Tick(r.inspectionDay(10))).none { it is RegulationEvent.Fined })
        assertTrue(r.e.regulation.fines().isEmpty())
    }

    @Test fun inspectionsOnlyHappenOnTheCountrysDay() {
        val r = Rig(); val c = r.company("Smoke Works", 1); val f = r.farm(c)
        f.pollutionPpm = 1_000_000L
        assertTrue(r.run.run(Tick(r.inspectionDay(10) + 1)).none { it is RegulationEvent.Fined })
    }

    @Test fun theThirdFineInHalfAYearShutsTheFactory() {
        val r = Rig(); val c = r.company("Smoke Works", 1); val f = r.farm(c)
        r.e.world.replaceCountry(r.country().copy(environmentalStrictnessPpm = 500_000L))
        f.pollutionPpm = 950_000L
        val results = (10L..12L).map { m -> r.run.run(Tick(r.inspectionDay(m))).filterIsInstance<RegulationEvent.Fined>().single().fine }
        assertEquals(listOf(false, false, true), results.map { it.closed })
        assertEquals(FacilityStatus.SHUTDOWN, f.status)
        assertEquals(3, r.e.regulation.finesOf(c).size)
        assertTrue(r.run.run(Tick(r.inspectionDay(13))).none { it is RegulationEvent.Fined })
    }

    @Test fun finesOlderThanTheWindowDoNotCount() {
        val r = Rig(); val c = r.company("Smoke Works", 1); val f = r.farm(c)
        r.e.world.replaceCountry(r.country().copy(environmentalStrictnessPpm = 500_000L))
        f.pollutionPpm = 950_000L
        val first = r.run.run(Tick(r.inspectionDay(10))).filterIsInstance<RegulationEvent.Fined>().single().fine
        val second = r.run.run(Tick(r.inspectionDay(10 + 8))).filterIsInstance<RegulationEvent.Fined>().single().fine
        assertFalse(first.closed || second.closed)
        assertEquals(1, r.e.regulation.recentFines(c, f.id, Tick(r.inspectionDay(10 + 8))))
    }

    @Test fun aBrokeCompanyPaysWhatItCanAndStillGetsTheFine() {
        val r = Rig(); val c = r.company("Poor Works", 1); val f = r.farm(c)
        r.e.world.replaceCountry(r.country().copy(environmentalStrictnessPpm = 1_000_000L))
        f.pollutionPpm = 1_000_000L
        val take = r.cash(c) - Money.units(2)
        r.e.finance.ledger.post(
            app.many.economytycoon.finance.TransactionDraft(
                "test:drain", Tick(0), app.many.economytycoon.finance.ReasonCode.TRANSFER,
                listOf(
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(LedgerOwner.company(c), app.many.economytycoon.finance.Chart.CASH), -take),
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(r.gov, app.many.economytycoon.finance.Chart.CASH), take),
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(LedgerOwner.company(c), app.many.economytycoon.finance.Chart.OTHER_EXPENSE), take),
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(r.gov, app.many.economytycoon.finance.Chart.REVENUE_OTHER), -take),
                ),
                "drain",
            ),
        )
        val fine = r.run.run(Tick(r.inspectionDay(10))).filterIsInstance<RegulationEvent.Fined>().single().fine
        assertTrue(fine.amount <= Money.units(2))
        assertTrue(r.cash(c).raw >= 0)
    }

    @Test fun moodReadsPeopleAndPollution() {
        val r = Rig(); val c = r.company("Mood Works", 1); val f = r.farm(c)
        val calm = r.run.mood(r.country.id)
        assertEquals(0L, calm.pollutionPpm)
        assertTrue(calm.happinessPpm in 1..1_000_000L && calm.unemploymentPpm in 0..1_000_000L)
        f.pollutionPpm = 400_000L
        assertEquals(400_000L, r.run.mood(r.country.id).pollutionPpm)
        f.pollutionPpm = 5_000_000L
        assertEquals(1_000_000L, r.run.mood(r.country.id).pollutionPpm)
    }

    @Test fun aLongGameKeepsGovernmentsProposingAndHoldingElections() {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
        val proposed = HashSet<Long>(); var elections = 0; var enacted = 0
        repeat(1_600) {
            for (ev in sim.advance().regulationEvents) when (ev) {
                is RegulationEvent.Proposed -> proposed += ev.law.value
                is RegulationEvent.Election -> elections++
                is RegulationEvent.Enacted -> enacted++
                else -> Unit
            }
        }
        assertTrue("laws proposed ${proposed.size}", proposed.size >= 10)
        assertTrue("elections $elections", elections >= sim.economy.world.countries().size)
        assertTrue("laws enacted $enacted", enacted >= 1)
        for (c in sim.economy.world.countries()) {
            val g = sim.economy.regulation.government(c.id)!!
            assertTrue(g.approvalPpm in 0..1_000_000L)
            assertTrue(c.corporateTaxPpm in 50_000L..400_000L && c.salesTaxPpm in 0L..250_000L)
        }
    }

    @Test fun twoRunsOfTheSameGameLegislateIdentically() {
        fun laws(): List<String> {
            val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.content, bootstrap = false)
            sim.advance(500)
            return sim.economy.regulation.laws().map { "${it.id.value}:${it.country.value}:${it.lever}:${it.to}:${it.status}" }
        }
        assertEquals(laws(), laws())
    }
}
