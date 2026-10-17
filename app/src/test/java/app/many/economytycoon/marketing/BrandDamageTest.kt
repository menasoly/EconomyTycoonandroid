package app.many.economytycoon.marketing

import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.marketing.MarketingTestSupport.acme
import app.many.economytycoon.marketing.MarketingTestSupport.beer
import app.many.economytycoon.marketing.MarketingTestSupport.book
import app.many.economytycoon.marketing.MarketingTestSupport.bread
import app.many.economytycoon.marketing.MarketingTestSupport.north
import app.many.economytycoon.marketing.MarketingTestSupport.rejection
import app.many.economytycoon.marketing.MarketingTestSupport.request
import app.many.economytycoon.marketing.MarketingTestSupport.rival
import app.many.economytycoon.marketing.MarketingTestSupport.run
import app.many.economytycoon.marketing.MarketingTestSupport.south
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandDamageTest {
    private val params = MarketingParams()
    private val s = MarketingTestSupport.system()

    private fun known(awareness: Long = 800_000L, loyalty: Long = 500_000L): BrandStock = BrandStock().also { it.set(awareness, loyalty) }

    @Test fun aRecallTakesAShareOfAwarenessAndLoyalty() {
        val b = known()
        val lost = BrandDamage.hit(b, DamageKind.RECALL, 1_000_000L)
        assertEquals(800_000L - 280_000L, b.awarenessPpm)
        assertEquals(280_000L, lost)
        assertEquals(500_000L - 150_000L, b.loyaltyPpm)
        assertEquals(500_000L, b.taintPpm)
    }

    @Test fun severityScalesTheBlow() {
        val mild = known(); val severe = known()
        BrandDamage.hit(mild, DamageKind.SCANDAL, 200_000L)
        BrandDamage.hit(severe, DamageKind.SCANDAL, 900_000L)
        assertTrue(severe.awarenessPpm < mild.awarenessPpm)
        assertTrue(severe.loyaltyPpm < mild.loyaltyPpm)
        assertTrue(severe.taintPpm > mild.taintPpm)
    }

    @Test fun kindsDifferInWhatTheyHurt() {
        val dispute = known(); val scandal = known()
        BrandDamage.hit(dispute, DamageKind.LABOUR_DISPUTE, 800_000L)
        BrandDamage.hit(scandal, DamageKind.SCANDAL, 800_000L)
        assertTrue(scandal.loyaltyPpm < dispute.loyaltyPpm)
    }

    @Test fun loyaltyNeverExceedsAwarenessAfterABlow() {
        val b = known(300_000L, 300_000L)
        BrandDamage.hit(b, DamageKind.LABOUR_DISPUTE, 100_000L)
        assertTrue(b.loyaltyPpm <= b.awarenessPpm)
    }

    @Test fun repeatedBlowsRaiseTaintButNeverPastOne() {
        val b = known()
        repeat(20) { BrandDamage.hit(b, DamageKind.SAFETY_INCIDENT, 1_000_000L) }
        assertTrue(b.taintPpm <= 1_000_000L)
        assertTrue(b.taintPpm > 900_000L)
    }

    @Test fun anUnknownBrandLosesNothing() {
        val b = BrandStock()
        assertEquals(0L, BrandDamage.hit(b, DamageKind.RECALL, 1_000_000L))
        assertTrue(b.taintPpm > 0L)
    }

    @Test fun recoveryTakesLongerAfterWorseDamage() {
        val mild = BrandDamage.daysToRecover(DamageKind.LABOUR_DISPUTE, 300_000L, params)
        val bad = BrandDamage.daysToRecover(DamageKind.SCANDAL, 1_000_000L, params)
        assertTrue(bad > mild)
        assertEquals(0, BrandDamage.daysToRecover(DamageKind.LABOUR_DISPUTE, 10_000L, params))
    }

    @Test fun repairCostGrowsWithDamage() {
        assertTrue(BrandDamage.repairDays(DamageKind.SCANDAL, 1_000_000L) > BrandDamage.repairDays(DamageKind.LABOUR_DISPUTE, 200_000L))
        assertTrue(BrandDamage.repairDays(DamageKind.RECALL, 1L) >= 3)
    }

    // --- service ---

    private fun setUp() {
        book(s, request(good = beer, region = north, daily = 1_500, tier = CreativeTier.BASIC))
        book(s, request(good = bread, region = north, channel = Channel.ONLINE, daily = 1_500, tier = CreativeTier.BASIC))
        book(s, request(good = beer, region = south, daily = 1_500, tier = CreativeTier.BASIC))
        book(s, request(company = rival, good = beer, region = north, daily = 1_500, tier = CreativeTier.BASIC))
        run(s, 0, 40)
    }

    @Test fun aCompanyWideScandalHitsEveryBrandOfThatCompanyOnly() {
        setUp()
        val before = s.state.stocks().associate { it.first to it.second.awarenessPpm }
        s.damage.record(acme, null, null, DamageKind.SCANDAL, 800_000L, Tick(40)).getOrThrow()
        for ((k, st) in s.state.stocks()) {
            if (k.company == acme) assertTrue(st.awarenessPpm < before.getValue(k)) else assertEquals(before.getValue(k), st.awarenessPpm)
        }
    }

    @Test fun aRecallOfOneProductLeavesTheOthersAlone() {
        setUp()
        val bread0 = s.state.stock(BrandKey(acme, bread, north))!!.awarenessPpm
        val beer0 = s.state.stock(BrandKey(acme, beer, north))!!.awarenessPpm
        val south0 = s.state.stock(BrandKey(acme, beer, south))!!.awarenessPpm
        s.damage.record(acme, beer, null, DamageKind.RECALL, 700_000L, Tick(40)).getOrThrow()
        assertEquals(bread0, s.state.stock(BrandKey(acme, bread, north))!!.awarenessPpm)
        assertTrue(s.state.stock(BrandKey(acme, beer, north))!!.awarenessPpm < beer0)
        assertTrue(s.state.stock(BrandKey(acme, beer, south))!!.awarenessPpm < south0)
    }

    @Test fun aLocalIncidentHitsOnlyThatRegion() {
        setUp()
        val northBefore = s.state.stock(BrandKey(acme, beer, north))!!.awarenessPpm
        val southBefore = s.state.stock(BrandKey(acme, beer, south))!!.awarenessPpm
        s.damage.record(acme, beer, south, DamageKind.SAFETY_INCIDENT, 700_000L, Tick(40)).getOrThrow()
        assertEquals(northBefore, s.state.stock(BrandKey(acme, beer, north))!!.awarenessPpm)
        assertTrue(s.state.stock(BrandKey(acme, beer, south))!!.awarenessPpm < southBefore)
    }

    @Test fun theRecordKeepsWhatHappened() {
        setUp()
        val (record, event) = s.damage.record(acme, beer, null, DamageKind.DATA_BREACH, 500_000L, Tick(41)).getOrThrow()
        assertEquals(record, s.state.damages().single())
        assertEquals(DamageKind.DATA_BREACH, event.kind)
        assertEquals(record.awarenessLostPpm, event.awarenessLostPpm)
        assertTrue(record.awarenessLostPpm > 0L)
        assertEquals(Tick(41), record.tick)
    }

    @Test fun nonsenseSeverityIsRefused() {
        setUp()
        assertEquals(Rules.INVALID_ARGUMENT, rejection(s.damage.record(acme, null, null, DamageKind.RECALL, 0L, Tick(1))))
        assertEquals(Rules.INVALID_ARGUMENT, rejection(s.damage.record(acme, null, null, DamageKind.RECALL, 2_000_000L, Tick(1))))
    }

    @Test fun aCompanyWithNoBrandCannotBeDamaged() {
        setUp()
        assertEquals(Rules.UNKNOWN_ENTITY, rejection(s.damage.record(app.many.economytycoon.core.CompanyId(99), null, null, DamageKind.RECALL, 500_000L, Tick(1))))
        assertTrue(s.state.damages().isEmpty())
    }

    @Test fun damageBluntsAdvertisingUntilItFades() {
        val hit = MarketingTestSupport.system(); val calm = MarketingTestSupport.system()
        for (sys in listOf(hit, calm)) book(sys, request(daily = 1_500, tier = CreativeTier.BASIC))
        run(hit, 0, 40); run(calm, 0, 40)
        hit.damage.record(acme, null, null, DamageKind.SCANDAL, 900_000L, Tick(40))
        run(hit, 40, 40); run(calm, 40, 40)
        val k = BrandKey(acme, beer, north)
        assertTrue(hit.state.stock(k)!!.awarenessPpm < calm.state.stock(k)!!.awarenessPpm)
        run(hit, 80, 400); run(calm, 80, 400)
        val gap = calm.state.stock(k)!!.awarenessPpm - hit.state.stock(k)!!.awarenessPpm
        assertTrue("gap $gap", gap < 20_000L)
    }

    @Test fun averageTaintReflectsTheMark() {
        setUp()
        assertEquals(0L, s.damage.averageTaintPpm(acme))
        s.damage.record(acme, null, null, DamageKind.SCANDAL, 1_000_000L, Tick(40))
        assertTrue(s.damage.averageTaintPpm(acme) > 500_000L)
        assertEquals(0L, s.damage.averageTaintPpm(app.many.economytycoon.core.CompanyId(99)))
    }

    @Test fun oldDamageRecordsAreForgotten() {
        setUp()
        s.damage.record(acme, null, null, DamageKind.RECALL, 500_000L, Tick(40))
        run(s, 41, 300)
        assertTrue(s.state.damages().isEmpty())
    }
}
