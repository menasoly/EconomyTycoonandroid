package app.many.economytycoon.research

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.TechId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.research.ResearchTestSupport.Rig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchServiceTest {
    private val milling = TechId("milling"); private val ovens = TechId("ovens")
    private val efficiency = TechId("efficiency"); private val mega = TechId("megamill")
    private fun rule(o: app.many.economytycoon.core.Outcome<*>) = o.rejectionOrNull()?.rule

    @Test fun costsAreCountedInWholePoints() {
        val r = Rig()
        assertEquals(1_000L, r.service.costPoints(r.service.content.technology(milling)))
        assertEquals(200L, r.service.paceCap(r.service.content.technology(milling)))
        assertEquals(5L, r.service.minimumTicks(r.service.content.technology(milling)))
    }

    @Test fun startingRequiresPrerequisitesAndASlot() {
        val r = Rig()
        assertEquals(Rules.PREREQUISITE_MISSING, rule(r.service.start(r.a, ovens, Tick(0))))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(r.service.start(r.a, TechId("nope"), Tick(0))))
        r.service.start(r.a, milling, Tick(0)).getOrThrow()
        assertEquals(Rules.DUPLICATE, rule(r.service.start(r.a, milling, Tick(0))))
        r.service.grant(r.a, milling, Tick(0))
        r.service.start(r.a, ovens, Tick(0)).getOrThrow()
        r.service.start(r.a, efficiency, Tick(0)).getOrThrow()
        val tight = ResearchService(r.service.content, r.funding, ResearchParams(maxConcurrent = 1))
        tight.grant(r.a, milling, Tick(0))
        tight.start(r.a, ovens, Tick(0)).getOrThrow()
        assertEquals(Rules.LIMIT_EXCEEDED, rule(tight.start(r.a, efficiency, Tick(0))))
    }

    @Test fun projectsAbsorbPointsAtTheirPaceAndComplete() {
        val r = Rig(); r.funding.give(r.a, 10_000)
        r.service.start(r.a, milling, Tick(0)).getOrThrow()
        val events = r.run(10)
        assertEquals(1, events.filterIsInstance<ResearchEvent.Completed>().size)
        assertTrue(r.service.isUnlocked(r.a, milling))
        assertEquals(1_000L, r.funding.spent)
        assertEquals(5L, r.service.of(r.a).unlockedAt(milling)!!.value)
    }

    @Test fun slowFundingStretchesTheProject() {
        val r = Rig()
        r.service.start(r.a, milling, Tick(0)).getOrThrow()
        repeat(5) { r.funding.give(r.a, 50); r.run(1) }
        val p = r.service.of(r.a).project(milling)!!
        assertEquals(250L, p.invested)
        assertEquals(250_000L, p.progressPpm)
        assertFalse(r.service.isUnlocked(r.a, milling))
    }

    @Test fun companiesDoNotShareFunding() {
        val r = Rig(); r.funding.give(r.a, 10_000)
        r.service.start(r.a, milling, Tick(0)).getOrThrow()
        r.service.start(r.b, milling, Tick(0)).getOrThrow()
        r.run(10)
        assertTrue(r.service.isUnlocked(r.a, milling))
        assertFalse(r.service.isUnlocked(r.b, milling))
    }

    @Test fun queuedTechnologiesStartWhenTheirTurnComes() {
        val r = Rig(); r.funding.give(r.a, 100_000)
        r.service.start(r.a, milling, Tick(0)).getOrThrow()
        r.service.enqueue(r.a, ovens).getOrThrow()
        r.service.enqueue(r.a, mega).let { assertEquals(Rules.PREREQUISITE_MISSING, rule(it)) }
        r.service.enqueue(r.a, efficiency).getOrThrow()
        assertEquals(listOf(ovens, efficiency), r.service.of(r.a).queued())
        val events = r.run(6)
        assertEquals(2, events.filterIsInstance<ResearchEvent.Started>().size)
        assertTrue(r.service.of(r.a).queued().isEmpty())
        r.run(30)
        assertTrue(r.service.isUnlocked(r.a, ovens) && r.service.isUnlocked(r.a, efficiency))
    }

    @Test fun cancellingForfeitsProgress() {
        val r = Rig(); r.funding.give(r.a, 200)
        r.service.start(r.a, milling, Tick(0)).getOrThrow()
        r.run(1)
        r.service.cancel(r.a, milling, Tick(2)).getOrThrow()
        assertNull(r.service.of(r.a).project(milling))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(r.service.cancel(r.a, milling, Tick(3))))
        r.service.start(r.a, milling, Tick(3)).getOrThrow()
        assertEquals(0L, r.service.of(r.a).project(milling)!!.invested)
    }

    @Test fun availabilityTracksPrerequisites() {
        val r = Rig()
        assertEquals(listOf(milling), r.service.available(r.a).map { it.id })
        r.service.grant(r.a, milling, Tick(0))
        assertEquals(listOf(efficiency, ovens), r.service.available(r.a).map { it.id })
        r.service.start(r.a, ovens, Tick(0))
        assertEquals(listOf(efficiency), r.service.available(r.a).map { it.id })
    }

    @Test fun licensingCopiesATechnologyForAFee() {
        val r = Rig()
        assertEquals(Rules.NOT_UNLOCKED, rule(r.service.license(r.a, r.b, milling, Tick(0))))
        r.service.grant(r.a, milling, Tick(0))
        val fee = r.service.license(r.a, r.b, milling, Tick(5)).getOrThrow()
        assertEquals(Money.units(400), fee)
        assertTrue(r.service.isUnlocked(r.b, milling))
        assertEquals(1, r.payments.log.size)
        assertEquals(Rules.DUPLICATE, rule(r.service.license(r.a, r.b, milling, Tick(6))))
        assertEquals(Rules.INVALID_ARGUMENT, rule(r.service.license(r.a, r.a, milling, Tick(6))))
    }

    @Test fun licenceNeedsPayment() {
        val r = Rig(); r.service.grant(r.a, milling, Tick(0)); r.payments.allow = false
        assertEquals(Rules.INSUFFICIENT_FUNDS, rule(r.service.license(r.a, r.b, milling, Tick(1))))
        assertFalse(r.service.isUnlocked(r.b, milling))
    }

    @Test fun absorbingACompanyCopiesItsTechnologies() {
        val r = Rig()
        r.service.grant(r.a, milling, Tick(0)); r.service.grant(r.a, ovens, Tick(1)); r.service.grant(r.b, milling, Tick(0))
        assertEquals(1, r.service.absorb(r.b, r.a, Tick(9)))
        assertEquals(listOf(milling, ovens), r.service.of(r.b).unlockedTechs())
    }

    @Test fun saveAndLoadRoundTrips() {
        val r = Rig(); r.funding.give(r.a, 100_000)
        r.service.grant(r.a, milling, Tick(0))
        r.service.start(r.a, ovens, Tick(1)).getOrThrow()
        r.service.enqueue(r.a, efficiency).getOrThrow()
        r.run(3)
        val w = ByteWriter(); ResearchCodec.write(r.service, w)
        val copy = Rig(); ResearchCodec.read(ByteReader(w.toByteArray()), copy.service)
        fun hash(s: ResearchService) = CanonicalHasher().also { s.hashInto(it) }.finish().toHex()
        assertEquals(hash(r.service), hash(copy.service))
        assertNotNull(copy.service.of(r.a).project(ovens))
    }

    @Test fun badVersionIsRejected() {
        val w = ByteWriter(); w.writeVarInt(99)
        val r = Rig()
        val failed = runCatching { ResearchCodec.read(ByteReader(w.toByteArray()), r.service) }.isFailure
        assertTrue(failed)
    }

    @Test fun removingACompanyForgetsItsResearch() {
        val r = Rig(); r.service.grant(r.a, milling, Tick(0))
        r.service.remove(r.a)
        assertNull(r.service.peek(r.a))
    }
}
