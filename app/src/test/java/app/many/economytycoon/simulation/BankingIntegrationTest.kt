package app.many.economytycoon.simulation

import app.many.economytycoon.activity.ActivityArea
import app.many.economytycoon.activity.CommandDescriber
import app.many.economytycoon.banking.BankAction
import app.many.economytycoon.banking.BankId
import app.many.economytycoon.banking.BankOperator
import app.many.economytycoon.banking.CapitalInstrument
import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Rules
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.persistence.CommandCodec
import app.many.economytycoon.persistence.CommandSamples
import app.many.economytycoon.persistence.SectionedFile
import app.many.economytycoon.persistence.SimulationSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The banking package as the simulation wires it: set-up, commands, the daily round, money, snapshots and replay. */
class BankingIntegrationTest {
    private class Rig(seed: Long = 12L, ai: Int = 3, banks: Boolean = true) {
        val sim = Simulation(SimTestSupport.config(seed, ai), SimTestSupport.content, bootstrap = ai > 0)
        val e = sim.economy
        val country = e.world.countries().sortedBy { it.id }.first()
        val me: CompanyId = CompanyId((sim.execute(Actor.Player(1), Command.FoundCompany("Vault Holdings", country.id, null, Money.units(5_000_000))) as Outcome.Ok).value.created!!)
        init { if (banks) sim.execute(Actor.System, Command.EstablishBanks) }

        fun cash(c: CompanyId = me) = e.finance.ledger.cash(LedgerOwner.company(c))
        fun found(name: String = "Vault Bank", capital: Money = Money.units(1_000_000)) =
            sim.execute(Actor.Player(1), Command.FoundBank(me, name, country.id, capital))
        fun mine(): List<app.many.economytycoon.banking.BankSnapshot> = e.banking.banksOf(me)
    }

    @Test fun aGameWithoutBanksLeavesNoStateNoStreamAndNoSnapshotSection() {
        val r = Rig(ai = 3, banks = false)
        r.sim.advance(40)
        assertTrue(r.e.banking.isPristine)
        assertFalse(r.e.bankingRunner.active)
        assertTrue(r.e.random.snapshot().keys.none { it.startsWith("banking") })
        assertTrue(SectionedFile.read(SimulationSnapshot.save(r.sim)).none { it.name == "banking" })
        assertTrue(r.sim.advance().bankingEvents.isEmpty())
    }

    @Test fun onlyTheSystemOpensTheBanksOnceAndEveryCountryGetsThreeToFive() {
        val r = Rig(banks = false)
        assertTrue(r.sim.execute(Actor.Player(1), Command.EstablishBanks) is Outcome.Rejected)
        assertTrue(r.e.banking.isPristine)
        assertTrue(r.sim.execute(Actor.System, Command.EstablishBanks) is Outcome.Ok)
        assertTrue(r.sim.execute(Actor.System, Command.EstablishBanks) is Outcome.Rejected)
        for (c in r.e.world.countries()) {
            val n = r.e.banking.banksIn(c.id).size
            assertTrue("country ${c.id.value} has $n banks", n in 3..5)
        }
        assertTrue(r.e.banking.banks().all { it.operator == BankOperator.AI && it.owner == null })
        assertTrue(r.e.finance.invariants().isEmpty())
    }

    @Test fun theBankCommandsRoundTripThroughTheCodecAndDescriber() {
        val cs = CommandSamples.all().filter { it is Command.EstablishBanks || it is Command.FoundBank || it is Command.BankDecision }
        assertEquals(6, cs.size)
        for (c in cs) {
            val w = ByteWriter(); CommandCodec.writeCommand(c, w)
            assertEquals(c, CommandCodec.readCommand(ByteReader(w.toByteArray())))
            assertEquals(ActivityArea.FINANCE, CommandDescriber.area(c))
            assertTrue(CommandDescriber.describe(c).isNotBlank())
        }
        assertTrue(CommandDescriber.movesMoney(Command.FoundBank(CompanyId(1), "B", app.many.economytycoon.core.CountryId(1), Money.units(1))))
    }

    @Test fun foundingABankChecksTheGameTheCashAndTheRules() {
        val none = Rig(banks = false)
        assertEquals(Rules.WRONG_STATE, (none.found() as Outcome.Rejected).rejection.rule)

        val r = Rig()
        assertEquals(Rules.INSUFFICIENT_FUNDS, (r.found(capital = Money.units(9_000_000)) as Outcome.Rejected).rejection.rule)
        assertEquals(Rules.INVALID_ARGUMENT, (r.found(capital = Money.units(1_000)) as Outcome.Rejected).rejection.rule)
        assertEquals(Rules.NOT_OWNER, (r.sim.execute(Actor.Player(2), Command.FoundBank(r.me, "Stolen", r.country.id, Money.units(1_000_000))) as Outcome.Rejected).rejection.rule)
        assertTrue(r.mine().isEmpty())

        val before = r.cash()
        val made = r.found() as Outcome.Ok
        assertEquals(before - Money.units(1_000_000), r.cash())
        val bank = r.e.banking.bank(BankId(made.value.created!!))!!
        assertEquals(BankOperator.PLAYER, bank.operator)
        assertEquals(r.me, bank.owner)
        assertEquals(Rules.NAME_TAKEN, (r.found("vault bank") as Outcome.Rejected).rejection.rule)
        assertTrue(r.e.finance.invariants().isEmpty())
    }

    @Test fun bankDecisionsOnlyWorkOnABankTheCompanyRuns() {
        val r = Rig()
        val rival = r.e.banking.banks().first()
        val rivalMargin = BankAction.SetLoanMargin(rival.id, 20L)
        assertEquals(Rules.NOT_OWNER, (r.sim.execute(Actor.Player(1), Command.BankDecision(r.me, rivalMargin)) as Outcome.Rejected).rejection.rule)
        val id = BankId((r.found() as Outcome.Ok).value.created!!)
        assertTrue(r.sim.execute(Actor.Player(1), Command.BankDecision(r.me, BankAction.SetLoanMargin(id, 30L))) is Outcome.Ok)
        assertEquals(30L, r.e.banking.bank(id)!!.policy.loanMarginBps)
        assertTrue(r.sim.execute(Actor.Player(1), Command.BankDecision(r.me, BankAction.SetLoanMargin(id, 9_000L))) is Outcome.Rejected)
        assertEquals(Rules.INVALID_ARGUMENT, (r.sim.execute(Actor.Player(1), Command.BankDecision(r.me, BankAction.FoundBank("X", r.country.id, r.me, Money.units(1), true))) as Outcome.Rejected).rejection.rule)
        val tooMuch = BankAction.RaiseCapital(id, CapitalInstrument.COMMON_EQUITY, r.cash() + Money.units(1))
        assertEquals(Rules.INSUFFICIENT_FUNDS, (r.sim.execute(Actor.Player(1), Command.BankDecision(r.me, tooMuch)) as Outcome.Rejected).rejection.rule)
        val cash = r.cash()
        assertTrue(r.sim.execute(Actor.Player(1), Command.BankDecision(r.me, BankAction.RaiseCapital(id, CapitalInstrument.COMMON_EQUITY, Money.units(200_000)))) is Outcome.Ok)
        assertEquals(cash - Money.units(200_000), r.cash())
        // Two raises in one day are both paid.
        assertTrue(r.sim.execute(Actor.Player(1), Command.BankDecision(r.me, BankAction.RaiseCapital(id, CapitalInstrument.COMMON_EQUITY, Money.units(200_000)))) is Outcome.Ok)
        assertEquals(cash - Money.units(400_000), r.cash())
        assertTrue(r.e.finance.invariants().isEmpty())
    }

    @Test fun theBankersWorkCreatesNoMoneyBeyondShareCapitalAndTheBooksStayBalanced() {
        val r = Rig(seed = 21L, ai = 4)
        r.found()
        val supplyStart = r.e.finance.ledger.moneySupply()
        r.sim.advance(125)
        assertTrue(r.e.finance.invariants().isEmpty())
        assertEquals(emptyList<String>(), Invariants.check(r.e))
        assertTrue(r.e.banking.banks().all { it.imbalance == Money.ZERO })
        // Customers are the households account, so a run moves money around; only government top-ups and share capital add to it.
        val created = r.e.finance.ledger.transactions()
            .filter { it.reason == app.many.economytycoon.finance.ReasonCode.MINT || it.reason == app.many.economytycoon.finance.ReasonCode.CAPITAL }
        assertTrue(created.isNotEmpty())
        assertTrue(r.e.finance.ledger.moneySupply() >= supplyStart - Money.units(1))
    }

    @Test fun theSectorLendsAndTakesDepositsAndTheBanksEarnFromTheRealEconomy() {
        val r = Rig(seed = 14L, ai = 5)
        r.sim.advance(125)
        val banks = r.e.banking.banks()
        assertTrue(banks.all { it.history.isNotEmpty() })
        assertTrue(banks.all { b -> b.history.any { it.loanInterest.isPositive } })
        assertTrue(banks.any { b -> b.history.any { it.netIncome != Money.ZERO } })
        assertTrue(r.e.bankingInputs.loanDemand(r.country.id, app.many.economytycoon.banking.LoanSector.CORPORATE).raw > 0L)
        val ledgerCash = r.e.finance.ledger.cashHeldBy(app.many.economytycoon.finance.OwnerKind.BANK)
        assertTrue(ledgerCash.raw > 0L)
    }

    @Test fun theAdapterReadsTheRealEconomyAndChangesNothing() {
        val r = Rig(seed = 5L, ai = 4)
        r.sim.advance(65)
        val before = r.sim.hash()
        val inputs = r.e.bankingInputs
        val c = r.country.id
        assertEquals(r.e.macro.policyRatePpm(c), inputs.policyRate(c).ppm)
        assertEquals(r.e.macro.latest(c)!!.inflationPpm, inputs.inflation(c).ppm)
        assertTrue(inputs.sovereignYield(c, 84) >= inputs.sovereignYield(c, 6))
        assertTrue(inputs.collateralIndex(c) in 500_000L..1_300_000L)
        for (s in app.many.economytycoon.banking.LoanSector.values()) assertTrue(inputs.defaultMultiplier(c, s) >= 600_000L)
        assertTrue(inputs.savingsFlow(c, app.many.economytycoon.banking.Depositor.HOUSEHOLD).raw > 0L)
        assertEquals(before, r.sim.hash())
    }

    @Test fun savingAndLoadingContinuesExactlyWithBanksPresent() {
        val r = Rig(seed = 31L, ai = 4)
        r.found()
        r.sim.advance(70)
        val section = SectionedFile.read(SimulationSnapshot.save(r.sim)).first { it.name == "banking" }
        assertTrue(section.payload.isNotEmpty())
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(r.sim), SimTestSupport.content)
        assertEquals(r.sim.hash(), copy.hash())
        assertEquals(r.e.banking.stateHash(), copy.economy.banking.stateHash())
        for (i in 0 until 40) { r.sim.advance(); copy.advance(); assertEquals("tick ${r.sim.tick.value}", r.sim.hash(), copy.hash()) }
    }

    @Test fun anOlderSaveWithoutTheSectionLoadsWithNoBanks() {
        val r = Rig(seed = 8L, ai = 2, banks = false)
        r.sim.advance(10)
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(r.sim), SimTestSupport.content)
        assertTrue(copy.economy.banking.isPristine)
        assertEquals(r.sim.hash(), copy.hash())
    }

    @Test fun theCommandLogReplaysIdenticallyAndTheBanksChangeTheHash() {
        val a = Rig(seed = 33L, ai = 4)
        val plain = Rig(seed = 33L, ai = 4, banks = false)
        val id = BankId((a.found() as Outcome.Ok).value.created!!)
        a.sim.advance(5)
        a.sim.submit(Actor.Player(1), Command.BankDecision(a.me, BankAction.SetCapitalPolicy(id, Rate.percent(4), Rate.percent(30))))
        a.sim.advance(100); plain.sim.advance(105)
        val again = Simulation.replay(a.sim.config, SimTestSupport.content, a.sim.commandLog(), a.sim.tick.value)
        assertEquals(a.sim.hash(), again.hash())
        assertNotEquals(plain.sim.hash(), a.sim.hash())
        assertEquals(emptyList<String>(), Invariants.check(a.e))
    }

    @Test fun aMultiMonthRunWithBanksIsDeterministic() {
        val x = Rig(seed = 41L, ai = 5); val y = Rig(seed = 41L, ai = 5)
        x.sim.advance(130); y.sim.advance(130)
        assertEquals(x.sim.hash(), y.sim.hash())
        assertTrue(Invariants.check(x.e).isEmpty())
        assertTrue(x.e.finance.invariants().isEmpty())
    }
}
