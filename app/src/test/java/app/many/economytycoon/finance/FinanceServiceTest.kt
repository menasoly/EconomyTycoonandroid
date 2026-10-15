package app.many.economytycoon.finance

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceServiceTest {
    private val gov = LedgerOwner(OwnerKind.GOVERNMENT, 1)
    private val customers = LedgerOwner.households()

    private fun hash(f: FinanceService) = CanonicalHasher().also { f.hashInto(it) }.finish()

    /** Runs a seeded random economy for [days] ticks and returns the service. */
    private fun simulate(seed: Long, days: Int, companies: Int = 6): FinanceService {
        val rnd = DeterministicRandom(seed)
        val ids = IdAllocators()
        val f = FinanceService(ids)
        f.setTaxPolicy(gov, TaxPolicy())
        val owners = (1..companies).map { LedgerOwner(OwnerKind.COMPANY, it.toLong()) }
        f.mint(Tick(0), f.bank, Money.units(50_000_000), "bank")
        f.mint(Tick(0), customers, Money.units(20_000_000), "customers")
        for (o in owners) f.registerCompany(o, gov, Holder.player(o.id), Money.units(400_000), 1_000, Tick(0)).getOrThrow()
        val seller = LedgerOwner(OwnerKind.COMPANY, 99)
        f.mint(Tick(0), seller, Money.units(1_000), "seller")
        for (o in owners) f.purchaseAsset("asset-${o.id}", Tick(1), o, seller, AssetClasses.MACHINERY, "line", Money.units(150_000)).getOrThrow()
        for (t in 1L..days) {
            val tick = Tick(t)
            val intents = ArrayList<SettlementIntent>()
            for (o in owners) {
                if (rnd.chance(Rate.percent(70))) f.recordSale("sale-${o.id}-$t", tick, o, customers, Money.units(rnd.nextInRange(500, 9_000)), Money.ZERO)
                intents.add(SettlementIntent.simple("wage-${o.id}-$t", o, LedgerOwner.households(), Money.units(rnd.nextInRange(300, 3_000)), SettlementPriority.WAGES, ReasonCode.WAGES, Chart.WAGES_EXPENSE, Chart.REVENUE_OTHER))
                if (rnd.chance(Rate.percent(30))) intents.add(SettlementIntent.simple("sup-${o.id}-$t", o, seller, Money.units(rnd.nextInRange(100, 2_000)), SettlementPriority.SUPPLIERS, ReasonCode.PURCHASE, Chart.COGS, Chart.REVENUE_SALES))
            }
            if (t % 60L == 5L) {
                val o = owners[rnd.nextInt(owners.size)]
                f.borrow(tick, o, Money.units(rnd.nextInRange(10_000, 80_000)), 180)
            }
            f.settleTick(tick, intents)
            f.closeIfDue(tick) { gov }
            val problems = f.invariants()
            assertTrue("day $t: $problems", problems.isEmpty())
        }
        return f
    }

    @Test fun randomEconomyKeepsEveryInvariant() {
        val f = simulate(7, 400)
        assertEquals(0L, f.ledger.trialBalance())
        assertTrue(f.ledger.transactionCount > 1_000)
        assertTrue(f.companies().all { f.latestStatements(it.owner) != null })
    }

    @Test fun identicalSeedsGiveIdenticalStateHashes() {
        assertEquals(hash(simulate(11, 200)), hash(simulate(11, 200)))
    }

    @Test fun differentSeedsDiverge() {
        assertTrue(hash(simulate(11, 120)) != hash(simulate(12, 120)))
    }

    @Test fun saveAndLoadRoundTripsTheStateHash() {
        val f = simulate(21, 250)
        val w = ByteWriter()
        FinanceCodec.write(f, w)
        val ids = IdAllocators()
        ids.restore(f.ids.snapshot())
        val back = FinanceCodec.read(ByteReader(w.toByteArray()), ids, f.config)
        assertEquals(hash(f), hash(back))
        assertTrue(back.invariants().isEmpty())
        // Balances and ratings carry over.
        for (c in f.companies()) {
            assertEquals(f.ledger.cash(c.owner), back.ledger.cash(c.owner))
            assertEquals(c.rating.rating, back.company(c.owner)!!.rating.rating)
        }
    }

    @Test fun loadedStateKeepsRunningIdenticallyToTheOriginal() {
        val a = simulate(33, 150)
        val w = ByteWriter(); FinanceCodec.write(a, w)
        val ids = IdAllocators(); ids.restore(a.ids.snapshot())
        val b = FinanceCodec.read(ByteReader(w.toByteArray()), ids, a.config)
        for (f in listOf(a, b)) {
            val owner = LedgerOwner(OwnerKind.COMPANY, 1)
            for (t in 151L..200L) {
                f.recordSale("late-$t", Tick(t), owner, customers, Money.units(1_000 + t), Money.ZERO)
                f.settleTick(Tick(t), listOf(SettlementIntent.simple("late-w-$t", owner, LedgerOwner.households(), Money.units(500), SettlementPriority.WAGES, ReasonCode.WAGES, Chart.WAGES_EXPENSE, Chart.REVENUE_OTHER)))
                f.closeIfDue(Tick(t)) { gov }
            }
        }
        assertEquals(hash(a), hash(b))
    }

    @Test fun truncatedSaveIsDetected() {
        val f = simulate(5, 60)
        val w = ByteWriter(); FinanceCodec.write(f, w)
        val bytes = w.toByteArray()
        val cut = bytes.copyOf(bytes.size / 2)
        var failed = false
        try { FinanceCodec.read(ByteReader(cut), IdAllocators(), f.config) } catch (e: Exception) { failed = true }
        assertTrue(failed)
    }

    @Test fun borrowingRespectsCreditTerms() {
        val f = simulate(3, 100)
        val o = LedgerOwner(OwnerKind.COMPANY, 1)
        val terms = f.creditTerms(o, Tick(100))
        assertTrue(f.borrow(Tick(101), o, terms.limit + Money.units(1_000_000), 180).isRejected)
    }

    @Test fun assetDisposalBooksGainAndRemovesAsset() {
        val f = FinanceService(IdAllocators())
        val a = LedgerOwner(OwnerKind.COMPANY, 1); val b = LedgerOwner(OwnerKind.COMPANY, 2)
        f.mint(Tick(0), a, Money.units(500_000), "a"); f.mint(Tick(0), b, Money.units(500_000), "b")
        val asset = f.purchaseAsset("p", Tick(1), a, b, AssetClasses.EQUIPMENT, "lathe", Money.units(100_000)).getOrThrow()
        val dep = f.assets.depreciate(a, 720)
        assertTrue(dep.isPositive)
        f.ledger.post(TransactionDraft("dep", Tick(2), ReasonCode.DEPRECIATION, listOf(Entry(AccountRef(a, Chart.DEPRECIATION_EXPENSE), dep), Entry(AccountRef(a, Chart.ACCUMULATED_DEPRECIATION), -dep))))
        val gain = f.disposeAsset(Tick(3), asset.id, b, Money.units(75_000)).getOrThrow()
        assertEquals(Money.units(75_000) - (Money.units(100_000) - dep), gain)
        assertEquals(Money.ZERO, f.ledger.balance(a, Chart.FIXED_ASSETS))
        assertEquals(Money.ZERO, f.ledger.balance(a, Chart.ACCUMULATED_DEPRECIATION))
        assertTrue(f.disposeAsset(Tick(4), asset.id, b, Money.ZERO).isRejected)
        assertTrue(f.invariants().isEmpty())
    }

    @Test fun impairmentReducesBookValue() {
        val f = FinanceService(IdAllocators())
        val a = LedgerOwner(OwnerKind.COMPANY, 1); val b = LedgerOwner(OwnerKind.COMPANY, 2)
        f.mint(Tick(0), a, Money.units(500_000), "a")
        val asset = f.purchaseAsset("p", Tick(1), a, b, AssetClasses.BUILDING, "hall", Money.units(200_000)).getOrThrow()
        f.impairAsset("imp", Tick(2), asset.id, Money.units(50_000)).getOrThrow()
        assertEquals(Money.units(150_000), asset.bookValue)
        assertEquals(Money.units(150_000), f.statements.balance(a, Tick(2)).fixedAssetsNet)
    }
}
