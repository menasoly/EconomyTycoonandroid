package app.many.economytycoon.companies

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.IndustryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AcquisitionTest {
    private fun rule(o: app.many.economytycoon.core.Outcome<*>) = o.rejectionOrNull()?.rule

    private class Deal(val r: CompanyRig = CompanyRig()) {
        val buyer = r.found("Big Buyer", capital = Money.units(5_000_000))
        val target = r.found("Small Target", capital = Money.units(400_000))
        val shares = r.companies.params.initialShares
        val fair = r.acquisitions.fairPricePerShare(target.id, Tick(0))
        fun price(multiple: Long) = Money(fair.raw * multiple / 100)
    }

    @Test fun fairValueFollowsBookWithoutEarnings() {
        val d = Deal()
        assertEquals(Money.units(400_000), d.r.acquisitions.fairValue(d.target.id, Tick(0)))
        assertTrue(d.fair.raw > 0)
    }

    @Test fun offersAreValidated() {
        val d = Deal(); val a = d.r.acquisitions
        assertEquals(Rules.INVALID_ARGUMENT, rule(a.makeOffer(d.buyer.id, d.buyer.id, 10, d.fair, Tick(0))))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(a.makeOffer(d.buyer.id, CompanyId(99), 10, d.fair, Tick(0))))
        assertEquals(Rules.INVALID_ARGUMENT, rule(a.makeOffer(d.buyer.id, d.target.id, 0, d.fair, Tick(0))))
        assertEquals(Rules.INVALID_ARGUMENT, rule(a.makeOffer(d.buyer.id, d.target.id, 10, Money.ZERO, Tick(0))))
        assertEquals(Rules.INSUFFICIENT_STOCK, rule(a.makeOffer(d.buyer.id, d.target.id, d.shares + 1, d.fair, Tick(0))))
        val poor = d.r.found("Poor Buyer", capital = Money.units(60_000))
        assertEquals(Rules.INSUFFICIENT_FUNDS, rule(a.makeOffer(poor.id, d.target.id, d.shares, d.price(300), Tick(0))))
    }

    @Test fun stateEnterprisesAreNotForSale() {
        val d = Deal()
        val state = d.r.found("State Post", CompanyKind.STATE, capital = Money.units(1_000_000))
        assertEquals(Rules.REGULATION, rule(d.r.acquisitions.makeOffer(d.buyer.id, state.id, 10, d.fair, Tick(0))))
    }

    @Test fun aiBoardAcceptsAGenerousOfferAndLosesControl() {
        val d = Deal()
        val offer = d.r.acquisitions.makeOffer(d.buyer.id, d.target.id, d.shares * 6 / 10, d.price(150), Tick(0)).getOrThrow()
        val cashBefore = d.r.finance.ledger.cash(d.buyer.owner)
        val done = d.r.acquisitions.step(Tick(1))
        assertEquals(1, done.size)
        assertEquals(OfferState.ACCEPTED, offer.state)
        assertTrue(done.single().controlGained)
        assertEquals(d.buyer.id, d.r.companies.groups.controllerOf(d.target.id))
        assertEquals(cashBefore - offer.total, d.r.finance.ledger.cash(d.buyer.owner))
        assertTrue(d.r.finance.invariants().isEmpty())
    }

    @Test fun aiBoardIgnoresLowOffersUntilTheyExpire() {
        val d = Deal()
        val offer = d.r.acquisitions.makeOffer(d.buyer.id, d.target.id, d.shares / 2, d.price(100), Tick(0)).getOrThrow()
        assertTrue(d.r.acquisitions.step(Tick(1)).isEmpty())
        assertEquals(OfferState.PENDING, offer.state)
        d.r.acquisitions.step(Tick(d.r.acquisitions.params.offerLifetimeTicks.toLong()))
        assertEquals(OfferState.EXPIRED, offer.state)
        assertEquals(null, d.r.companies.groups.controllerOf(d.target.id))
    }

    @Test fun sellersAreRewardedAndHouseholdsReceiveFounderProceeds() {
        val d = Deal()
        val before = d.r.finance.ledger.cash(d.r.finance.households)
        val offer = d.r.acquisitions.makeOffer(d.buyer.id, d.target.id, d.shares / 4, d.price(200), Tick(0)).getOrThrow()
        d.r.acquisitions.respond(d.target.id, offer.id, true, Tick(1)).getOrThrow()
        assertEquals(before + offer.total, d.r.finance.ledger.cash(d.r.finance.households))
        assertFalse(d.r.companies.groups.controllerOf(d.target.id) == d.buyer.id)
    }

    @Test fun playersDecideForThemselves() {
        val d = Deal()
        val mine = d.r.found("My Company", CompanyKind.PLAYER, capital = Money.units(400_000), founder = app.many.economytycoon.finance.Holder.player(1))
        val offer = d.r.acquisitions.makeOffer(d.buyer.id, mine.id, 100, d.r.acquisitions.fairPricePerShare(mine.id, Tick(0)) * 3, Tick(0)).getOrThrow()
        assertTrue(d.r.acquisitions.step(Tick(1)).isEmpty())
        assertEquals(Rules.NOT_OWNER, rule(d.r.acquisitions.respond(d.target.id, offer.id, true, Tick(1))))
        assertEquals(null, d.r.acquisitions.respond(mine.id, offer.id, false, Tick(2)).getOrThrow())
        assertEquals(OfferState.REJECTED, offer.state)
        assertEquals(Rules.WRONG_STATE, rule(d.r.acquisitions.respond(mine.id, offer.id, true, Tick(3))))
    }

    @Test fun acquirersCanWithdrawOnlyTheirOwnPendingOffers() {
        val d = Deal()
        val offer = d.r.acquisitions.makeOffer(d.buyer.id, d.target.id, 100, d.price(100), Tick(0)).getOrThrow()
        assertEquals(Rules.NOT_OWNER, rule(d.r.acquisitions.withdraw(d.target.id, offer.id)))
        d.r.acquisitions.withdraw(d.buyer.id, offer.id).getOrThrow()
        assertEquals(OfferState.WITHDRAWN, offer.state)
        assertEquals(Rules.WRONG_STATE, rule(d.r.acquisitions.withdraw(d.buyer.id, offer.id)))
        assertTrue(d.r.acquisitions.pendingFor(d.target.id).isEmpty())
    }

    @Test fun antitrustBlocksDominantCombinations() {
        val d = Deal()
        val steel = IndustryId("steel")
        d.r.companies.addIndustry(d.buyer.id, steel); d.r.companies.addIndustry(d.target.id, steel)
        d.r.acquisitions.antitrust = AntitrustView { c, _ -> if (c == d.buyer.id) 300_000L else 150_000L }
        assertEquals(Rules.ANTITRUST, rule(d.r.acquisitions.makeOffer(d.buyer.id, d.target.id, d.shares / 2, d.price(200), Tick(0))))
        d.r.acquisitions.antitrust = AntitrustView { _, _ -> 100_000L }
        d.r.acquisitions.makeOffer(d.buyer.id, d.target.id, d.shares / 2, d.price(200), Tick(0)).getOrThrow()
    }

    @Test fun acquirerThatCannotPayAnymoreLetsTheOfferLapse() {
        val d = Deal()
        val offer = d.r.acquisitions.makeOffer(d.buyer.id, d.target.id, d.shares / 2, d.price(200), Tick(0)).getOrThrow()
        d.r.finance.ledger.post(
            app.many.economytycoon.finance.TransactionDraft(
                "drain", Tick(0), app.many.economytycoon.finance.ReasonCode.TRANSFER,
                listOf(
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(d.buyer.owner, app.many.economytycoon.finance.Chart.COGS), Money.units(4_990_000)),
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(d.buyer.owner, app.many.economytycoon.finance.Chart.CASH), -Money.units(4_990_000)),
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(d.r.finance.households, app.many.economytycoon.finance.Chart.CASH), Money.units(4_990_000)),
                    app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(d.r.finance.households, app.many.economytycoon.finance.Chart.REVENUE_OTHER), -Money.units(4_990_000)),
                ),
            ),
        ).getOrThrow()
        assertEquals(Rules.INSUFFICIENT_FUNDS, rule(d.r.acquisitions.respond(d.target.id, offer.id, true, Tick(1))))
        assertEquals(OfferState.EXPIRED, offer.state)
    }

    @Test fun cyclicTakeoversAreRefused() {
        val d = Deal()
        val o = d.r.acquisitions.makeOffer(d.buyer.id, d.target.id, d.shares * 6 / 10, d.price(200), Tick(0)).getOrThrow()
        d.r.acquisitions.respond(d.target.id, o.id, true, Tick(1)).getOrThrow()
        assertEquals(Rules.OWNERSHIP_CYCLE, rule(d.r.acquisitions.makeOffer(d.target.id, d.buyer.id, 10, d.price(200), Tick(2))))
    }

    @Test fun oneOffGainsDoNotInflateEarningsValue() {
        val d = Deal()
        val gain = Money.units(3_000_000)
        val tx = app.many.economytycoon.finance.TransactionDraft(
            "gain", Tick(5), app.many.economytycoon.finance.ReasonCode.TRANSFER,
            listOf(
                app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(d.buyer.owner, app.many.economytycoon.finance.Chart.CASH), -gain),
                app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(d.buyer.owner, app.many.economytycoon.finance.Chart.INVESTMENTS), gain),
                app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(d.target.owner, app.many.economytycoon.finance.Chart.CASH), gain),
                app.many.economytycoon.finance.Entry(app.many.economytycoon.finance.AccountRef(d.target.owner, app.many.economytycoon.finance.Chart.REVENUE_OTHER), -gain),
            ),
            "stake sold",
        )
        assertTrue(d.r.finance.ledger.post(tx) is app.many.economytycoon.core.Outcome.Ok)
        val value = d.r.acquisitions.fairValue(d.target.id, Tick(30))
        val book = app.many.economytycoon.finance.Valuation.book(d.r.finance.statements.balance(d.target.owner, Tick(30)))
        assertEquals(book, value)
    }
}
