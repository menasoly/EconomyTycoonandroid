package app.many.economytycoon.simulation

import app.many.economytycoon.activity.ActivityArea
import app.many.economytycoon.activity.CommandDescriber
import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.TechId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.ip.IpCommand
import app.many.economytycoon.persistence.CommandCodec
import app.many.economytycoon.persistence.CommandSamples
import app.many.economytycoon.persistence.SectionedFile
import app.many.economytycoon.persistence.SimulationSnapshot
import app.many.economytycoon.research.ResearchEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The patent module as the simulation wires it: the switch, commands, sales history, production effects, snapshots and replay. */
class IpIntegrationTest {
    private class Rig(seed: Long = 12L, ai: Int = 3, ip: Boolean = true) {
        val sim = Simulation(SimTestSupport.config(seed, ai), SimTestSupport.content, bootstrap = ai > 0)
        val e = sim.economy
        val country = e.world.countries().sortedBy { it.id }.first()
        val me: CompanyId = CompanyId((sim.execute(Actor.Player(1), Command.FoundCompany("Mill Works", country.id, null, Money.units(5_000_000))) as Outcome.Ok).value.created!!)
        val recipe = SimTestSupport.content.allRecipes.first { it.requiredTech != null }
        val tech: TechId = recipe.requiredTech!!
        init { if (ip) sim.execute(Actor.System, Command.EstablishIp) }

        fun cash(c: CompanyId = me) = e.finance.ledger.cash(LedgerOwner.company(c))
        fun grant(c: CompanyId = me, t: TechId = tech) { e.research.grant(c, t, e.tick) }
        fun ip(cmd: IpCommand, by: Long = 1L) = sim.execute(Actor.Player(by), Command.Ip(cmd))
        fun file(c: CompanyId = me) = ip(IpCommand.FilePatent(c, tech, listOf(country.id)))
        fun sell(c: CompanyId, units: Long = 1_000L, value: Long = 50_000L) =
            e.ipContext.recordSale(e.tick, c, recipe.outputs.first().good, country.id, Quantity.units(units), Money.units(value))
    }

    @Test fun aGameWithoutAPatentOfficeLeavesNoStateNoSectionAndNoSalesHistory() {
        val r = Rig(ip = false)
        r.grant()
        r.sim.advance(40)
        assertFalse(r.e.ipActive)
        assertFalse(r.e.ipRunner.active)
        assertTrue(r.e.ipSales.isEmpty)
        assertTrue(SectionedFile.read(SimulationSnapshot.save(r.sim)).none { it.name == "ip" || it.name == "ipsales" })
        assertTrue(r.sim.advance().ipEvents.isEmpty())
        assertEquals(Rules.WRONG_STATE, (r.file() as Outcome.Rejected).rejection.rule)
    }

    @Test fun onlyTheSystemOpensTheOfficeOnceAndItSeedsTheScriptedHolders() {
        val r = Rig(ip = false)
        assertTrue(r.sim.execute(Actor.Player(1), Command.EstablishIp) is Outcome.Rejected)
        assertFalse(r.e.ipActive)
        assertTrue(r.sim.execute(Actor.System, Command.EstablishIp) is Outcome.Ok)
        assertTrue(r.sim.execute(Actor.System, Command.EstablishIp) is Outcome.Rejected)
        assertTrue(r.e.ipActive)
        assertEquals(3, r.e.ip.state.npes().size)
        assertTrue(SectionedFile.read(SimulationSnapshot.save(r.sim)).any { it.name == "ip" })
    }

    @Test fun theIpCommandsRoundTripThroughTheCodecAndDescriber() {
        val cs = CommandSamples.all().filter { it is Command.EstablishIp || it is Command.Ip }
        assertEquals(5, cs.size)
        for (c in cs) {
            val w = ByteWriter(); CommandCodec.writeCommand(c, w)
            assertEquals(c, CommandCodec.readCommand(ByteReader(w.toByteArray())))
            assertEquals(ActivityArea.RESEARCH, CommandDescriber.area(c))
            assertTrue(CommandDescriber.describe(c).isNotBlank())
        }
        assertTrue(CommandDescriber.movesMoney(Command.Ip(IpCommand.FilePatent(CompanyId(1), TechId("x"), emptyList()))))
        assertFalse(CommandDescriber.movesMoney(Command.Ip(IpCommand.KeepSecret(CompanyId(1), TechId("x")))))
    }

    @Test fun aCorruptCompanyInsideAnIpCommandIsRefused() {
        fun bytes(company: Long) = ByteWriter().also { CommandCodec.writeCommand(Command.Ip(IpCommand.KeepSecret(CompanyId(company), TechId("milling"))), it) }.toByteArray()
        val seven = bytes(7L)
        val nine = bytes(9L)
        // The company is written twice, once for the command and once inside its body; change only the second.
        val places = seven.indices.filter { seven[it] != nine[it] }
        assertEquals(2, places.size)
        seven[places[1]] = nine[places[1]]
        try { CommandCodec.readCommand(ByteReader(seven)); org.junit.Assert.fail("accepted a mismatched company") } catch (e: ByteReader.CorruptDataException) { assertTrue(e.message!!.contains("company")) }
    }

    @Test fun filingChecksTheOwnerTheResearchAndTheCashThenChargesTheFees() {
        val r = Rig()
        assertEquals(Rules.NOT_OWNER, (r.ip(IpCommand.FilePatent(r.me, r.tech, listOf(r.country.id)), by = 2L) as Outcome.Rejected).rejection.rule)
        assertEquals(Rules.NOT_UNLOCKED, (r.file() as Outcome.Rejected).rejection.rule)
        r.grant()
        val before = r.cash()
        val filed = r.file()
        assertTrue(filed.toString(), filed is Outcome.Ok)
        assertTrue(r.cash() < before)
        assertEquals(1, r.e.ip.state.patentsOf(r.me).size)
        assertTrue(r.e.ipRunner.recentEvents().any { it is app.many.economytycoon.ip.IpEvent.PatentFiled })
        assertEquals(Rules.DUPLICATE, (r.file() as Outcome.Rejected).rejection.rule)
        assertTrue(r.e.finance.invariants().isEmpty())
    }

    @Test fun aCompanyThatCannotPayForTheFilingIsRefusedAndNothingChanges() {
        val r = Rig(seed = 3L)
        val broke = r.e.companies.all().first { it.id != r.me && it.kind == app.many.economytycoon.companies.CompanyKind.AI }.id
        r.grant(broke)
        val cash = r.cash(broke)
        val drained = r.e.finance.ledger.cash(LedgerOwner.company(broke))
        assertEquals(cash, drained)
        // The same fee asked of a company with almost nothing: take its cash away through a loss-making charge.
        val owner = LedgerOwner.company(broke)
        r.e.finance.ledger.post(
            app.many.economytycoon.finance.TransactionDraft(
                "test:drain", r.e.tick, app.many.economytycoon.finance.ReasonCode.TRANSFER,
                listOf(
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(owner, app.many.economytycoon.finance.Chart.OTHER_EXPENSE), cash),
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(owner, app.many.economytycoon.finance.Chart.CASH), -cash),
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(r.e.finance.households, app.many.economytycoon.finance.Chart.CASH), cash),
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(r.e.finance.households, app.many.economytycoon.finance.Chart.REVENUE_OTHER), -cash),
                ),
                "drain",
            ),
        )
        val result = r.sim.execute(Actor.System, Command.Ip(IpCommand.FilePatent(broke, r.tech, listOf(r.country.id))))
        assertEquals(Rules.INSUFFICIENT_FUNDS, (result as Outcome.Rejected).rejection.rule)
        assertTrue(r.e.ip.state.patentsOf(broke).isEmpty())
        assertEquals(Money.ZERO, r.cash(broke))
    }

    @Test fun aSecretLowersTheCostOfARecipeThatNeedsItsTechnology() {
        val r = Rig()
        r.grant()
        val plain = r.e.modifiers.inputSavings(r.me, r.recipe)
        assertTrue(r.ip(IpCommand.KeepSecret(r.me, r.tech)) is Outcome.Ok)
        val kept = r.e.modifiers.inputSavings(r.me, r.recipe)
        assertTrue("$plain -> $kept", kept.ppm >= plain.ppm)
        val without = Rig(ip = false)
        without.grant()
        assertEquals(plain, without.e.modifiers.inputSavings(without.me, without.recipe))
        assertEquals(without.e.modifiers.throughputBonus(without.me, without.recipe), r.e.modifiers.throughputBonus(r.me, r.recipe))
    }

    @Test fun theSalesHistoryAnswersTheModulesQuestionsFromRealRecords() {
        val r = Rig()
        r.grant()
        r.sim.advance(3)
        r.sell(r.me, units = 400L, value = 20_000L)
        r.sell(r.me, units = 100L, value = 5_000L)
        val ctx = r.e.ipContext
        val now = r.e.tick
        val used = ctx.usage(r.me, r.tech, emptyList(), listOf(r.country.id), Tick(0), now)
        assertEquals(Money.units(25_000), used.sales)
        assertEquals(Quantity.units(500L), used.units)
        assertTrue(ctx.usage(r.me, r.tech, emptyList(), listOf(app.many.economytycoon.core.CountryId(9_999)), Tick(0), now).isEmpty)
        assertEquals(listOf(r.me), ctx.practicing(r.tech, r.country.id, Tick(0), now).map { it.company })
        assertTrue(ctx.practicing(r.tech, r.country.id, now, now).isEmpty())
        assertEquals(listOf(r.tech), ctx.knownTechs(r.me))
        assertTrue(r.me in ctx.independentHolders(r.tech, CompanyId(9_999)))
        assertFalse(r.me in ctx.independentHolders(r.tech, r.me))
        assertTrue(ctx.cash(app.many.economytycoon.ip.IpOwner.company(r.me)).raw > 0L)
        assertEquals(Money.ZERO, ctx.cash(app.many.economytycoon.ip.IpOwner.npe(r.e.ip.state.npes().first().id)))
        val field = app.many.economytycoon.ip.TechFields.of(r.e.content.technology(r.tech))
        assertEquals(Money.units(25_000), ctx.annualSalesInField(r.me, field))
        assertEquals(1_000_000L, ctx.marketShare(r.me, field).ppm)
    }

    @Test fun aSaleOfAGoodNoTechnologyBehindItIsNotRecordedAndNeitherIsOneFromAnUntrainedCompany() {
        val r = Rig()
        r.sell(r.me)
        assertTrue(r.e.ipSales.isEmpty)
        r.grant()
        r.e.ipContext.recordSale(r.e.tick, r.me, app.many.economytycoon.core.GoodId("no_such_good"), r.country.id, Quantity.units(1L), Money.units(1))
        assertTrue(r.e.ipSales.isEmpty)
    }

    @Test fun theRunnerSettlesRoyaltiesAndFeesWithTheDaysPaymentsAndTheBooksStayBalanced() {
        val r = Rig(seed = 21L, ai = 4)
        r.grant()
        r.file()
        r.ip(IpCommand.SetAutoMaintain(r.me, r.e.ip.state.patentsOf(r.me).first().id, true))
        val supply = r.e.finance.ledger.moneySupply()
        r.sim.advance(330)
        assertTrue(r.e.finance.invariants().isEmpty())
        assertEquals(emptyList<String>(), Invariants.check(r.e))
        // Only government top-ups and founding capital add money; the patent module only moves it.
        assertTrue(r.e.finance.ledger.moneySupply() >= supply - Money.units(1))
        assertTrue(r.e.ip.state.patents().any { it.filingMap.values.any { f -> f.status != app.many.economytycoon.ip.FilingStatus.PENDING } })
    }

    @Test fun aComputerCompanyProtectsATechnologyTheDayItFinishesResearchingIt() {
        val r = Rig(seed = 17L, ai = 3)
        r.sim.advance(3)
        val ai = r.e.companies.all().first { it.kind == app.many.economytycoon.companies.CompanyKind.AI }.id
        r.grant(ai)
        r.sell(ai, units = 50_000L, value = 5_000_000L)
        AiIp(r.e, r.sim.executor).onResearch(r.e.tick, listOf(ResearchEvent.Completed(ai, r.tech, r.e.tick)))
        val protectedNow = r.e.ip.state.patentOfTech(ai, r.tech) != null || r.e.ip.state.liveSecretFor(ai, r.tech) != null
        assertTrue("the computer company did nothing", protectedNow)
        // A player's research is never decided for them.
        r.grant()
        r.sell(r.me, units = 50_000L, value = 5_000_000L)
        AiIp(r.e, r.sim.executor).onResearch(r.e.tick, listOf(ResearchEvent.Completed(r.me, r.tech, r.e.tick)))
        assertTrue(r.e.ip.state.patentsOf(r.me).isEmpty() && r.e.ip.state.secretsOf(r.me).isEmpty())
    }

    @Test fun closingACompanyWindsUpItsRights() {
        val r = Rig(seed = 9L, ai = 2)
        r.grant()
        assertTrue(r.ip(IpCommand.KeepSecret(r.me, r.tech)) is Outcome.Ok)
        val secret = r.e.ip.state.secretsOf(r.me).first()
        assertTrue(secret.isLive)
        assertTrue(r.sim.execute(Actor.Player(1), Command.Dissolve(r.me)) is Outcome.Ok)
        assertFalse(r.e.ip.state.secret(secret.id)!!.isLive)
        r.sim.advance(40)
        assertEquals(emptyList<String>(), Invariants.check(r.e))
    }

    @Test fun savingAndLoadingContinuesExactlyWithThePatentOfficeOpen() {
        val r = Rig(seed = 31L, ai = 4)
        r.grant(); r.file()
        r.sell(r.me)
        r.sim.advance(70)
        val sections = SectionedFile.read(SimulationSnapshot.save(r.sim))
        assertTrue(sections.first { it.name == "ip" }.payload.isNotEmpty())
        assertTrue(sections.any { it.name == "ipsales" })
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(r.sim), SimTestSupport.content)
        assertEquals(r.sim.hash(), copy.hash())
        assertEquals(r.e.ip.stateHash(), copy.economy.ip.stateHash())
        assertTrue(copy.economy.ipActive)
        for (i in 0 until 40) { r.sim.advance(); copy.advance(); assertEquals("tick ${r.sim.tick.value}", r.sim.hash(), copy.hash()) }
    }

    @Test fun anOlderSaveWithoutTheSectionsLoadsWithNoOffice() {
        val r = Rig(seed = 8L, ai = 2, ip = false)
        r.sim.advance(10)
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(r.sim), SimTestSupport.content)
        assertFalse(copy.economy.ipActive)
        assertEquals(r.sim.hash(), copy.hash())
    }

    @Test fun theCommandLogReplaysIdenticallyAndTheOfficeChangesTheHash() {
        val a = Rig(seed = 33L, ai = 4)
        val plain = Rig(seed = 33L, ai = 4, ip = false)
        // Research is not a command, so the logged run uses only commands the log can carry.
        a.ip(IpCommand.SetPolicy(a.me, app.many.economytycoon.ip.IpPolicy(app.many.economytycoon.ip.EnforcementStance.PASSIVE, 123_000L, app.many.economytycoon.ip.BreadthChoice.NARROW, listOf(a.country.id), false)))
        a.sim.advance(5)
        a.sim.submit(Actor.Player(1), Command.Ip(IpCommand.SetPolicy(a.me, app.many.economytycoon.ip.IpPolicy(app.many.economytycoon.ip.EnforcementStance.LITIGATE, 200_000L, app.many.economytycoon.ip.BreadthChoice.BROAD, listOf(a.country.id), true))))
        a.sim.advance(100); plain.sim.advance(105)
        val again = Simulation.replay(a.sim.config, SimTestSupport.content, a.sim.commandLog(), a.sim.tick.value)
        assertEquals(a.sim.hash(), again.hash())
        assertNotEquals(plain.sim.hash(), a.sim.hash())
        assertEquals(emptyList<String>(), Invariants.check(a.e))
    }

    @Test fun aMultiMonthRunWithTheOfficeOpenIsDeterministic() {
        val x = Rig(seed = 41L, ai = 5); val y = Rig(seed = 41L, ai = 5)
        for (rig in listOf(x, y)) { rig.grant(); rig.file() }
        x.sim.advance(150); y.sim.advance(150)
        assertEquals(x.sim.hash(), y.sim.hash())
        assertEquals(x.e.ip.stateHash(), y.e.ip.stateHash())
        assertTrue(Invariants.check(x.e).isEmpty())
        assertTrue(x.e.finance.invariants().isEmpty())
    }
}
