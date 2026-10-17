package app.many.economytycoon.ip

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.ip.IpTestSupport.Rig
import app.many.economytycoon.ip.IpTestSupport.a
import app.many.economytycoon.ip.IpTestSupport.b
import app.many.economytycoon.ip.IpTestSupport.c
import app.many.economytycoon.ip.IpTestSupport.milling
import app.many.economytycoon.ip.IpTestSupport.ovens
import app.many.economytycoon.ip.IpTestSupport.us
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PatentMarketTest {
    private val price = Money.units(40_000)

    @Test fun listingSetsAskingPrice() {
        val r = Rig(); val p = r.granted(a, milling)
        r.market.list(r.run(5), p.id, IpOwner.company(a), price).getOrThrow()
        assertEquals(price, p.askingPrice)
        assertEquals(Tick(5), p.listedAt)
        assertEquals(listOf(p.id), r.market.listed().map { it.id })
    }

    @Test fun onlyOwnerLists() {
        val r = Rig(); val p = r.granted(a, milling)
        assertEquals(Rules.NOT_OWNER, r.market.list(r.run(5), p.id, IpOwner.company(b), price).rejectionOrNull()?.rule)
    }

    @Test fun listingNeedsPositivePriceAndLivePatent() {
        val r = Rig(); val p = r.granted(a, milling)
        assertEquals(Rules.INVALID_ARGUMENT, r.market.list(r.run(5), p.id, IpOwner.company(a), Money.ZERO).rejectionOrNull()?.rule)
        p.filingMap.getValue(us).status = FilingStatus.EXPIRED
        assertEquals(Rules.WRONG_STATE, r.market.list(r.run(5), p.id, IpOwner.company(a), price).rejectionOrNull()?.rule)
    }

    @Test fun unlistClearsTheListing() {
        val r = Rig(); val p = r.granted(a, milling)
        r.market.list(r.run(5), p.id, IpOwner.company(a), price).getOrThrow()
        assertEquals(Rules.NOT_OWNER, r.market.unlist(p.id, IpOwner.company(b)).rejectionOrNull()?.rule)
        r.market.unlist(p.id, IpOwner.company(a)).getOrThrow()
        assertNull(p.askingPrice)
        assertTrue(r.market.listed().isEmpty())
    }

    @Test fun buyingMovesOwnershipAndMoney() {
        val r = Rig(); val p = r.granted(a, milling)
        r.market.list(r.run(5), p.id, IpOwner.company(a), price).getOrThrow()
        val sale = r.market.buy(r.run(6), p.id, IpOwner.company(b)).getOrThrow()
        assertEquals(price, sale.price)
        assertEquals(IpOwner.company(b), p.owner)
        assertEquals(1, p.transfers)
        assertNull(p.askingPrice)
        val ch = r.charges().single { it.kind == ChargeKind.PATENT_PURCHASE }
        assertEquals(IpLedger.of(b), ch.payer)
        assertEquals(IpLedger.of(a), ch.payee)
        assertTrue(r.events().any { it is IpEvent.PatentSold })
    }

    @Test fun cannotBuyWhatIsNotListedOrOwnPatent() {
        val r = Rig(); val p = r.granted(a, milling)
        assertEquals(Rules.WRONG_STATE, r.market.buy(r.run(5), p.id, IpOwner.company(b)).rejectionOrNull()?.rule)
        r.market.list(r.run(5), p.id, IpOwner.company(a), price).getOrThrow()
        assertEquals(Rules.INVALID_ARGUMENT, r.market.buy(r.run(5), p.id, IpOwner.company(a)).rejectionOrNull()?.rule)
    }

    @Test fun buyerNeedsCash() {
        val r = Rig(); val p = r.granted(a, milling)
        r.market.list(r.run(5), p.id, IpOwner.company(a), price).getOrThrow()
        r.ctx.cashOf[IpOwner.company(b)] = Money.units(10)
        assertEquals(Rules.INSUFFICIENT_FUNDS, r.market.buy(r.run(6), p.id, IpOwner.company(b)).rejectionOrNull()?.rule)
        assertEquals(IpOwner.company(a), p.owner)
    }

    @Test fun licensesFollowThePatentToItsNewOwner() {
        val r = Rig(); val p = r.granted(a, milling)
        val l = r.sign(a, c, listOf(p.id))
        r.market.list(r.run(5), p.id, IpOwner.company(a), price).getOrThrow()
        r.market.buy(r.run(6), p.id, IpOwner.company(b)).getOrThrow()
        assertEquals(IpOwner.company(b), l.licensor)
    }

    @Test fun bundleLicenceBlocksSellingOneOfItsPatents() {
        val r = Rig(); val p = r.granted(a, milling); val q = r.granted(a, ovens)
        r.sign(a, c, listOf(p.id, q.id))
        r.market.list(r.run(5), p.id, IpOwner.company(a), price).getOrThrow()
        assertEquals(Rules.WRONG_STATE, r.market.buy(r.run(6), p.id, IpOwner.company(b)).rejectionOrNull()?.rule)
    }

    @Test fun poolPatentsCannotBeListedOrSold() {
        val r = Rig(); val p = r.granted(a, milling)
        val pool = r.pools.form(r.run(), a, "Mills", TechFields.of(IpTestSupport.content.technology(milling)), r.percent(3), 100_000L).getOrThrow()
        r.pools.join(r.run(), pool.id, a, listOf(p.id)).getOrThrow()
        assertEquals(Rules.WRONG_STATE, r.market.list(r.run(5), p.id, IpOwner.company(a), price).rejectionOrNull()?.rule)
    }

    @Test fun inactiveBuyerIsRefused() {
        val r = Rig(); val p = r.granted(a, milling)
        r.market.list(r.run(5), p.id, IpOwner.company(a), price).getOrThrow()
        r.ctx.inactive += b
        assertEquals(Rules.WRONG_STATE, r.market.buy(r.run(6), p.id, IpOwner.company(b)).rejectionOrNull()?.rule)
    }

    @Test fun holdingCapIsEnforced() {
        val r = Rig(1L, IpParams(office = OfficeParams(maxPatentsPerOwner = 1)))
        r.granted(b, milling)
        val p = r.granted(a, ovens)
        r.market.list(r.run(5), p.id, IpOwner.company(a), price).getOrThrow()
        assertEquals(Rules.LIMIT_EXCEEDED, r.market.buy(r.run(6), p.id, IpOwner.company(b)).rejectionOrNull()?.rule)
    }

    @Test fun staleListingsExpire() {
        val r = Rig(); val p = r.granted(a, milling)
        r.market.list(r.run(5), p.id, IpOwner.company(a), price).getOrThrow()
        r.market.expireListings(Tick(5L + r.params.office.listingDays - 1))
        assertEquals(price, p.askingPrice)
        r.market.expireListings(Tick(5L + r.params.office.listingDays))
        assertNull(p.askingPrice)
    }

    @Test fun npeBuyerPaysFromItsChest() {
        val r = Rig(); val p = r.granted(a, milling)
        r.npes.seed()
        val n = r.state.npeMap.values.first()
        r.market.list(r.run(5), p.id, IpOwner.company(a), price).getOrThrow()
        val before = n.chest
        r.market.buy(r.run(6), p.id, n.owner).getOrThrow()
        assertEquals(before - price, n.chest)
        assertEquals(price, n.spent)
        assertEquals(n.owner, p.owner)
    }

    @Test fun npeSellerIsPaidIntoItsChest() {
        val r = Rig(); val p = r.granted(a, milling)
        r.npes.seed()
        val n = r.state.npeMap.values.first()
        p.owner = n.owner
        r.market.list(r.run(5), p.id, n.owner, price).getOrThrow()
        val before = n.chest
        r.market.buy(r.run(6), p.id, IpOwner.company(b)).getOrThrow()
        assertEquals(before + price, n.chest)
        assertEquals(price, n.earned)
    }

    @Test fun fireSaleSellsToAScriptedBuyerAtADiscount() {
        val r = Rig(); val p = r.granted(a, milling)
        r.npes.seed()
        val sales = r.market.fireSale(r.run(10), IpOwner.company(a))
        assertEquals(1, sales.size)
        assertTrue(p.owner.isNpe)
        val v = r.valuer.value(p, r.run(10)).value
        assertTrue(sales[0].price < v)
    }

    @Test fun fireSaleFallsBackToCashRichComputerCompanies() {
        val r = Rig(); val p = r.granted(a, milling)
        r.ctx.ai += listOf(b, c)
        r.ctx.cashOf[IpOwner.company(b)] = Money.units(10_000_000)
        r.ctx.cashOf[IpOwner.company(c)] = Money.units(90_000_000)
        r.market.fireSale(r.run(10), IpOwner.company(a))
        assertEquals(IpOwner.company(c), p.owner)
    }

    @Test fun fireSaleWithoutBuyersSellsNothing() {
        val r = Rig(); val p = r.granted(a, milling)
        assertTrue(r.market.fireSale(r.run(10), IpOwner.company(a)).isEmpty())
        assertEquals(IpOwner.company(a), p.owner)
    }

    @Test fun closedCompanyPatentsAreSoldOrAbandoned() {
        val r = Rig(); val p = r.granted(a, milling); val q = r.granted(a, ovens)
        r.ctx.ai += b
        r.market.companyClosed(r.run(10), a)
        assertEquals(IpOwner.company(b), p.owner)
        assertEquals(IpOwner.company(b), q.owner)
        val r2 = Rig(); val p2 = r2.granted(a, milling)
        r2.market.companyClosed(r2.run(10), a)
        assertFalse(p2.isLive)
    }

    @Test fun saleCarriesNoDoubleCharges() {
        val r = Rig(); val p = r.granted(a, milling)
        r.market.list(r.run(5), p.id, IpOwner.company(a), price).getOrThrow()
        r.market.buy(r.run(6), p.id, IpOwner.company(b)).getOrThrow()
        assertEquals(1, r.charges().count { it.kind == ChargeKind.PATENT_PURCHASE })
        assertEquals(r.charges().map { it.key }.toSet().size, r.charges().size)
    }
}

