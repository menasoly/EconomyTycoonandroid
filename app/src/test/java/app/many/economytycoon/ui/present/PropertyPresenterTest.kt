package app.many.economytycoon.ui.present

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.simulation.Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PropertyPresenterTest {
    private val session by lazy { PresentSupport.session("Estate Hall", 8L) }
    private val e get() = session.economy
    private val me get() = session.selectedCompany!!

    private fun buy(): app.many.economytycoon.world.Parcel {
        val country = e.companies.company(me)!!.country
        val city = e.world.citiesOf(country).first()
        val p = e.world.land.available(city.id).filter { e.propertyRunner.appraise(it).raw > 0L }.minByOrNull { e.propertyRunner.appraise(it).raw }!!
        assertTrue(session.issueNow(Command.BuyParcel(me, p.id)).isOk)
        return e.world.land.get(p.id)!!
    }

    @Test fun withNoLandThePageSaysWhatToDo() {
        val m = PropertyPresenter.build(session)!!
        assertEquals(4, m.tiles.size)
        assertTrue(m.parcels.isEmpty() && m.leases.isEmpty())
        assertEquals(e.property.params.leaseTerms, m.terms)
    }

    @Test fun aBoughtPlotIsVacantAndCanBeSoldOrLet() {
        val p = buy()
        val card = PropertyPresenter.build(session)!!.parcels.single()
        assertEquals(p.id.value, card.id); assertEquals(ParcelState.FREE, card.state)
        assertTrue(card.canSell && card.canLet)
        assertTrue(card.title.contains("plot") && card.title.contains("units"))
        assertTrue(card.paid.startsWith("Paid"))
    }

    @Test fun aListedPlotCannotBeSoldOrLetAgain() {
        val p = buy()
        assertTrue(session.issueNow(Command.ListParcel(me, p.id, e.propertyRunner.appraise(p))).isOk)
        val card = PropertyPresenter.build(session)!!.parcels.single()
        assertEquals(ParcelState.LISTED, card.state); assertFalse(card.canSell || card.canLet)
        val listing = PropertyPresenter.build(session)!!.market.single()
        assertTrue(listing.mine); assertEquals("Near appraisal", listing.verdict)
    }

    @Test fun anOfferedAndALetPlotShowTheirState() {
        val p = buy()
        assertTrue(session.issueNow(Command.OfferLease(me, p.id, 90, e.property.fairRent(e.propertyRunner.appraise(p)))).isOk)
        assertEquals(ParcelState.OFFERED, PropertyPresenter.build(session)!!.parcels.single().state)
        e.property.startLease(e.property.offer(p.id)!!, 3, Tick(1))
        val m = PropertyPresenter.build(session)!!
        assertEquals(ParcelState.LET, m.parcels.single().state)
        val lease = m.leases.single()
        assertEquals(PropertyPresenter.TENANTS[3], lease.tenant); assertNull(lease.trouble)
        assertTrue(lease.buyout.startsWith("Ending early costs"))
        assertEquals(Tone.GOOD, m.tiles[2].tone)
    }

    @Test fun lateRentShowsAsTrouble() {
        val p = buy()
        val lease = e.property.startLease(e.property.addOffer(p.id, me, 90, Money.units(300), Tick(0)), 0, Tick(0))
        e.property.rentMissed(lease)
        assertEquals("1 payment missed", PropertyPresenter.build(session)!!.leases.single().trouble)
    }

    @Test fun verdictsCompareAskWithAppraisal() {
        val ap = Money.units(1_000)
        assertEquals("Near appraisal" to Tone.NEUTRAL, PropertyPresenter.verdict(Money.units(1_010), ap))
        assertEquals(Tone.GOOD, PropertyPresenter.verdict(Money.units(800), ap).second)
        assertTrue(PropertyPresenter.verdict(Money.units(800), ap).first.endsWith("under appraisal"))
        assertEquals(Tone.WARN, PropertyPresenter.verdict(Money.units(1_300), ap).second)
        assertEquals(Tone.NEUTRAL, PropertyPresenter.verdict(Money.units(10), Money.ZERO).second)
    }

    @Test fun otherCompaniesListingsAppearInTheMarketWithABuyOption() {
        val p = buy()
        e.world.land.transfer(p.id, app.many.economytycoon.core.CompanyId(9_999), Money.units(1))
        e.property.addListing(p.id, app.many.economytycoon.core.CompanyId(9_999), e.propertyRunner.appraise(p), session.tick)
        val l = PropertyPresenter.build(session)!!.market.single()
        assertFalse(l.mine); assertNotNull(l.expires)
    }
}
