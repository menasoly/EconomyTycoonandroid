package app.many.economytycoon.banking

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankingServiceTest {
    private val country = BankingFx.country
    private val inputs = StaticBankingInputs()
    private val me = CompanyId(7)

    private fun service(): BankingService = BankingService()

    private fun found(s: BankingService, name: String = "Mine", capital: Long = 1_000_000L, owner: CompanyId? = me, player: Boolean = true, random: RandomProvider = RandomProvider(1L)) =
        s.found(BankAction.FoundBank(name, country, owner, Money.units(capital), player), inputs, Tick(0), random)

    @Test fun foundingOpensBalancedBankAndReturnsCapitalIntent() {
        val s = service()
        val r = found(s).getOrThrow()
        val bank = s.bank(r.bank)
        assertNotNull(bank)
        assertEquals(Money.ZERO, bank!!.imbalance)
        assertEquals(BankOperator.PLAYER, bank.operator)
        assertEquals(1, r.intents.size)
        assertEquals(1, s.recentEvents().size)
    }

    @Test fun foundingNeedsMinimumCapital() {
        val s = service()
        val r = found(s, capital = 1_000L)
        assertEquals(Rules.INVALID_ARGUMENT, r.rejectionOrNull()?.rule)
    }

    @Test fun foundingRejectsBlankAndDuplicateNames() {
        val s = service()
        assertEquals(Rules.INVALID_ARGUMENT, found(s, name = "  ").rejectionOrNull()?.rule)
        assertTrue(found(s, name = "Alpha").isOk)
        assertEquals(Rules.NAME_TAKEN, found(s, name = "alpha").rejectionOrNull()?.rule)
    }

    @Test fun playerBankNeedsOwnerAndIsLimitedPerCompany() {
        val s = service()
        assertEquals(Rules.INVALID_ARGUMENT, found(s, owner = null).rejectionOrNull()?.rule)
        for (i in 1..s.params.maxPlayerBanks) assertTrue(found(s, name = "B$i").isOk)
        assertEquals(Rules.LIMIT_EXCEEDED, found(s, name = "Extra").rejectionOrNull()?.rule)
        assertTrue(found(s, name = "Other", owner = CompanyId(8)).isOk)
    }

    @Test fun countryMarketHasALimit() {
        val s = service()
        for (i in 1..s.params.maxBanksPerCountry) assertTrue(found(s, name = "R$i", owner = null, player = false).isOk)
        assertEquals(Rules.LIMIT_EXCEEDED, found(s, name = "Late", owner = null, player = false).rejectionOrNull()?.rule)
    }

    @Test fun seedRivalsMakesDistinctComputerBanks() {
        val s = service()
        val ids = s.seedRivals(country, 5, inputs, Tick(0), RandomProvider(3L))
        assertEquals(5, ids.size)
        assertEquals(5, s.banks().map { it.name }.toSet().size)
        assertTrue(s.banks().all { it.operator == BankOperator.AI })
        assertTrue(ids.all { s.strategyOf(it) != null })
    }

    @Test fun seedRivalsIsDeterministic() {
        val a = service(); val b = service()
        a.seedRivals(country, 4, inputs, Tick(0), RandomProvider(9L))
        b.seedRivals(country, 4, inputs, Tick(0), RandomProvider(9L))
        assertEquals(a.stateHash(), b.stateHash())
        val c = service()
        c.seedRivals(country, 4, inputs, Tick(0), RandomProvider(10L))
        assertFalse(a.stateHash() == c.stateHash())
    }

    @Test fun emptyServiceStepsToNothing() {
        val s = service()
        assertEquals(BankingStep.EMPTY, s.step(Tick(30), inputs, RandomProvider(1L)))
    }

    @Test fun monthEndClosesMonthOnlyEveryThirtyTicks() {
        val s = service()
        s.seedRivals(country, 2, inputs, Tick(0), RandomProvider(1L))
        val r = RandomProvider(1L)
        assertFalse(s.step(Tick(29), inputs, r).monthClosed)
        assertTrue(s.step(Tick(30), inputs, r).monthClosed)
        assertEquals(30L, s.bank(s.bankIds().first())!!.history.last().tick)
    }

    @Test fun booksStayBalancedEveryDayForAYear() {
        val s = service()
        val r = RandomProvider(5L)
        s.seedRivals(country, 4, inputs, Tick(0), r)
        for (t in 1L..360L) {
            s.step(Tick(t), inputs, r)
            for (b in s.banks()) assertEquals("tick $t ${b.name}", Money.ZERO, b.imbalance)
        }
    }

    @Test fun interbankPositionsMatchEveryDay() {
        val s = service()
        val r = RandomProvider(5L)
        s.seedRivals(country, 4, inputs, Tick(0), r)
        for (t in 1L..120L) {
            s.step(Tick(t), inputs, r)
            val all = s.banks()
            assertEquals("tick $t", Money.sum(all.map { it.interbankLent }), Money.sum(all.map { it.interbankBorrowed }))
        }
    }

    @Test fun sameSeedSameHistory() {
        fun run(): app.many.economytycoon.core.StateHash {
            val s = service(); val r = RandomProvider(11L)
            s.seedRivals(country, 3, inputs, Tick(0), r)
            for (t in 1L..150L) s.step(Tick(t), inputs, r)
            return s.stateHash()
        }
        assertEquals(run(), run())
    }

    @Test fun healthyBanksEarnAndGrow() {
        val s = service(); val r = RandomProvider(2L)
        val ids = s.seedRivals(country, 3, inputs, Tick(0), r)
        val before = ids.associateWith { s.bank(it)!!.totalAssets }
        for (t in 1L..180L) s.step(Tick(t), inputs, r)
        for (id in ids) {
            val b = s.bank(id)!!
            assertTrue(b.status.isOperating)
            assertTrue("${b.name} should have history", b.history.size >= 5)
        }
        assertTrue(ids.sumOf { s.bank(it)!!.totalAssets.raw } > before.values.sumOf { it.raw })
    }

    @Test fun playerActionsNeedOwnership() {
        val s = service()
        val id = found(s).getOrThrow().bank
        val a = BankAction.SetLoanMargin(id, 50)
        assertEquals(Rules.NOT_OWNER, s.apply(a, Tick(1), inputs, CompanyId(99)).rejectionOrNull()?.rule)
        assertEquals(Rules.NOT_OWNER, s.apply(a, Tick(1), inputs, null).rejectionOrNull()?.rule)
        assertTrue(s.apply(a, Tick(1), inputs, me).isOk)
        assertEquals(50L, s.bank(id)!!.policy.loanMarginBps)
    }

    @Test fun computerActionsCannotTouchPlayerBanksButCanTouchRivals() {
        val s = service()
        val rival = s.seedRivals(country, 1, inputs, Tick(0), RandomProvider(1L)).first()
        assertTrue(s.apply(BankAction.SetGrowthAppetite(rival, Rate.percent(30)), Tick(1), inputs, null).isOk)
        assertEquals(Rules.NOT_OWNER, s.apply(BankAction.SetGrowthAppetite(rival, Rate.percent(30)), Tick(1), inputs, me).rejectionOrNull()?.rule)
    }

    @Test fun unknownBankIsRejected() {
        val s = service()
        assertEquals(Rules.UNKNOWN_ENTITY, s.apply(BankAction.SetLoanMargin(BankId(99), 1), Tick(1), inputs, me).rejectionOrNull()?.rule)
        assertEquals(Rules.UNKNOWN_ENTITY, s.rumour(BankId(99), 100_000, Tick(1)).rejectionOrNull()?.rule)
    }

    @Test fun rumourRaisesAndIsValidated() {
        val s = service()
        val id = found(s).getOrThrow().bank
        assertEquals(Rules.INVALID_ARGUMENT, s.rumour(id, 0, Tick(1)).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, s.rumour(id, 2_000_000, Tick(1)).rejectionOrNull()?.rule)
        assertTrue(s.rumour(id, 300_000, Tick(1)).isOk)
        assertTrue(s.recentEvents().any { it is BankEvent.Rumour })
    }

    @Test fun viewAndAdviceExistForKnownBanksOnly() {
        val s = service()
        val id = found(s).getOrThrow().bank
        assertNotNull(s.view(id, inputs))
        assertNull(s.view(BankId(55), inputs))
        assertTrue(s.advice(BankId(55), inputs, Tick(1)).isEmpty())
        assertTrue(s.advice(id, inputs, Tick(1)).isNotEmpty())
    }

    @Test fun hiddenAdviceStaysHiddenUntilItsDay() {
        val s = service()
        val id = found(s).getOrThrow().bank
        val first = s.advice(id, inputs, Tick(1)).first().code
        s.hideAdvice(first, Tick(1), 10)
        assertFalse(s.advice(id, inputs, Tick(5)).any { it.code == first })
        assertTrue(s.advice(id, inputs, Tick(11)).any { it.code == first })
        s.hideAdvice(first, Tick(1), 100)
        s.showAdvice(first)
        assertTrue(s.advice(id, inputs, Tick(5)).any { it.code == first })
    }

    @Test fun eventLogIsCapped() {
        val s = service()
        val id = found(s).getOrThrow().bank
        repeat(s.params.eventLog + 30) { s.rumour(id, 1_000, Tick(it.toLong())) }
        assertEquals(s.params.eventLog, s.recentEvents().size)
        assertTrue(s.eventsOf(id).isNotEmpty())
    }

    @Test fun stressReportDoesNotChangeBanks() {
        val s = service()
        s.seedRivals(country, 3, inputs, Tick(0), RandomProvider(1L))
        val before = s.stateHash()
        val report = s.stressReport(country, StressScenario.SEVERE, inputs, Tick(5))
        assertEquals(3, report.results.size)
        assertEquals(before, s.stateHash())
    }

    @Test fun yearlyStressTestIsPublished() {
        val s = service(); val r = RandomProvider(4L)
        val ids = s.seedRivals(country, 2, inputs, Tick(0), r)
        var published = false
        for (t in 1L..361L) published = published or s.step(Tick(t), inputs, r).events.any { it is BankEvent.StressTestPublished }
        assertTrue(published)
        assertNotNull(s.lastStress(ids.first()))
    }

    @Test fun levyFillsTheGuaranteeFund() {
        val s = service(); val r = RandomProvider(4L)
        s.seedRivals(country, 3, inputs, Tick(0), r)
        for (t in 1L..120L) s.step(Tick(t), inputs, r)
        val fund = s.fund(country)
        assertNotNull(fund)
        assertTrue(fund!!.balance.isPositive)
        assertTrue(s.fundCoverage(country).ppm > 0L)
    }

    @Test fun confidenceDriversExplainACalmBank() {
        val s = service()
        val id = found(s).getOrThrow().bank
        val d = s.confidence(id, inputs)!!
        assertTrue(d.target > 0L)
        assertNull(s.confidence(BankId(77), inputs))
    }
}
