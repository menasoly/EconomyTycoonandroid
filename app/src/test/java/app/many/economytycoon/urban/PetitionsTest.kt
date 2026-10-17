package app.many.economytycoon.urban

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PetitionsTest {
    private val p = UrbanParams()
    private val g = UrbanTestSupport.geometry()
    private val firm = CompanyId(7)

    private fun council(ideology: Int = 0, integrity: Int = 800, ties: Int = 400, size: Int = 9): Council =
        Council(List(size) { CouncilSeat(it, "M$it", ideology, it * 9 / size, ties, integrity) })

    private fun ctx(ds: List<District>, c: Council, seed: Long = 1L, hooks: List<LobbyingHook> = emptyList()) =
        PetitionContext(g, ds, c, p, DeterministicRandom(seed), hooks)

    private fun file(book: PetitionBook, ds: List<District>, id: Long = 1, district: Int = 4, kind: PetitionKind = PetitionKind.REZONE, to: ZoneUse = ZoneUse.MIXED, far: Int = 0,
                     floors: Int = 0, who: CompanyId? = firm, now: Long = 0) =
        book.file(PetitionId(id), ds[district], kind, who, to, far, floors, Money.units(2_500), Tick(now), p)

    /** Runs days until the petition is decided or the limit is hit. */
    private fun decide(book: PetitionBook, c: PetitionContext, from: Long = 0, days: Int = 120): List<PetitionEvent> {
        val out = ArrayList<PetitionEvent>()
        for (d in 1..days) out += book.advanceDay(Tick(from + d), c)
        return out
    }

    @Test fun `filing records the request with the zone's defaults`() {
        val ds = UrbanTestSupport.districts()
        val book = PetitionBook()
        val pet = file(book, ds).getOrThrow()
        assertEquals(ZoneUse.RESIDENTIAL, pet.fromZone)
        assertEquals(ZoneUse.MIXED.defaultFarMilli, pet.requestedFarMilli)
        assertEquals(ZoneUse.MIXED.defaultFloors, pet.requestedFloors)
        assertEquals(PetitionStatus.FILED, pet.status)
        assertEquals(Tick(p.hearingNoticeDays.toLong()), pet.hearingAt)
        assertEquals(pet, book.openFor(4))
    }

    @Test fun `explicit limits are kept`() {
        val ds = UrbanTestSupport.districts()
        val pet = file(PetitionBook(), ds, far = 6_000, floors = 30).getOrThrow()
        assertEquals(6_000, pet.requestedFarMilli)
        assertEquals(30, pet.requestedFloors)
    }

    @Test fun `only one petition may be open for a district`() {
        val ds = UrbanTestSupport.districts()
        val book = PetitionBook()
        file(book, ds)
        assertEquals(Rules.DUPLICATE, file(book, ds, id = 2).rejectionOrNull()?.rule)
        assertTrue(file(book, ds, id = 3, district = 5).isOk)
    }

    @Test fun `the docket has a limit`() {
        val ds = UrbanTestSupport.districts()
        val book = PetitionBook()
        val small = p.copy(maxOpenPetitionsPerCity = 2)
        for (i in 0..1) assertTrue(book.file(PetitionId(i + 1L), ds[i], PetitionKind.REZONE, null, ZoneUse.MIXED, 0, 0, Money.ZERO, Tick(0), small).isOk)
        assertEquals(Rules.LIMIT_EXCEEDED, book.file(PetitionId(9), ds[2], PetitionKind.REZONE, null, ZoneUse.MIXED, 0, 0, Money.ZERO, Tick(0), small).rejectionOrNull()?.rule)
    }

    @Test fun `variances keep the zone and must ask for more`() {
        val ds = UrbanTestSupport.districts()
        assertEquals(Rules.INVALID_ARGUMENT, file(PetitionBook(), ds, kind = PetitionKind.VARIANCE, to = ZoneUse.MIXED, far = 9_000).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, file(PetitionBook(), ds, kind = PetitionKind.VARIANCE, to = ZoneUse.RESIDENTIAL, far = 2_000, floors = 5).rejectionOrNull()?.rule)
        assertTrue(file(PetitionBook(), ds, kind = PetitionKind.VARIANCE, to = ZoneUse.RESIDENTIAL, far = 4_000, floors = 12).isOk)
    }

    @Test fun `forbidden changes of use are refused`() {
        val ds = UrbanTestSupport.districts(zones = mapOf(4 to ZoneUse.CIVIC))
        assertEquals(Rules.INVALID_ARGUMENT, file(PetitionBook(), ds, to = ZoneUse.INDUSTRIAL).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, file(PetitionBook(), ds, to = ZoneUse.CIVIC).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, file(PetitionBook(), UrbanTestSupport.districts(), far = -1).rejectionOrNull()?.rule)
    }

    @Test fun `the applicant alone may withdraw`() {
        val ds = UrbanTestSupport.districts()
        val book = PetitionBook()
        val pet = file(book, ds).getOrThrow()
        assertEquals(Rules.NOT_OWNER, book.withdraw(pet.id, CompanyId(99), Tick(2)).rejectionOrNull()?.rule)
        assertEquals(Rules.NOT_OWNER, book.withdraw(pet.id, null, Tick(2)).rejectionOrNull()?.rule)
        assertEquals(PetitionStatus.WITHDRAWN, book.withdraw(pet.id, firm, Tick(2)).getOrThrow().status)
        assertEquals(Rules.WRONG_STATE, book.withdraw(pet.id, firm, Tick(3)).rejectionOrNull()?.rule)
        assertEquals(Rules.UNKNOWN_ENTITY, book.withdraw(PetitionId(55), firm, Tick(3)).rejectionOrNull()?.rule)
        assertNull(book.openFor(4))
        assertEquals(listOf(pet), book.history())
    }

    @Test fun `a petition goes through hearing, vote and enactment`() {
        val ds = UrbanTestSupport.districts()
        val book = PetitionBook()
        val pet = file(book, ds).getOrThrow()
        val c = ctx(ds, council(ideology = 400))
        val events = decide(book, c)
        assertEquals(1, events.filterIsInstance<PetitionEvent.Heard>().size)
        assertEquals(1, events.filterIsInstance<PetitionEvent.Voted>().size)
        assertEquals(PetitionStatus.ENACTED, pet.status)
        assertEquals(1, events.filterIsInstance<PetitionEvent.Enacted>().size)
        assertEquals(9, pet.votes.size)
        assertNotNull(pet.resolvedAt)
        assertTrue(pet.hearingAt < pet.voteAt)
    }

    @Test fun `the vote waits for the notice period and the delay`() {
        val ds = UrbanTestSupport.districts()
        val book = PetitionBook()
        val pet = file(book, ds).getOrThrow()
        val c = ctx(ds, council())
        for (d in 1..13) book.advanceDay(Tick(d.toLong()), c)
        assertEquals(PetitionStatus.FILED, pet.status)
        book.advanceDay(Tick(14), c)
        assertEquals(PetitionStatus.HEARING, pet.status)
        for (d in 15..20) book.advanceDay(Tick(d.toLong()), c)
        assertEquals(PetitionStatus.HEARING, pet.status)
        book.advanceDay(Tick(21), c)
        assertTrue(pet.status != PetitionStatus.HEARING)
    }

    @Test fun `growth friendly councils approve and hostile ones deny`() {
        val friendly = run(ideology = 500)
        val hostile = run(ideology = -500)
        assertTrue(friendly.yes > hostile.yes)
        assertEquals(PetitionStatus.DENIED, hostile.status)
        assertTrue(friendly.status == PetitionStatus.ENACTED)
    }

    private fun run(ideology: Int, to: ZoneUse = ZoneUse.MIXED, seed: Long = 1L): Petition {
        val ds = UrbanTestSupport.districts()
        val book = PetitionBook()
        val pet = file(book, ds, to = to).getOrThrow()
        decide(book, ctx(ds, council(ideology = ideology), seed))
        return pet
    }

    @Test fun `a denied petition cannot be filed again for six months`() {
        val ds = UrbanTestSupport.districts()
        val book = PetitionBook()
        val pet = file(book, ds).getOrThrow()
        decide(book, ctx(ds, council(ideology = -500)))
        assertEquals(PetitionStatus.DENIED, pet.status)
        val deniedAt = pet.resolvedAt!!.value
        assertEquals(Rules.RATE_LIMITED, file(book, ds, id = 2, now = deniedAt + 10).rejectionOrNull()?.rule)
        assertTrue(file(book, ds, id = 3, now = deniedAt + PetitionBook.REAPPLY_DAYS).isOk)
    }

    @Test fun `lobbying is limited to the applicant before the vote`() {
        val ds = UrbanTestSupport.districts()
        val c = council()
        val book = PetitionBook()
        val pet = file(book, ds).getOrThrow()
        assertEquals(Rules.NOT_OWNER, book.lobby(pet.id, CompanyId(2), null, Money.units(1_000), c, p, Tick(1)).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, book.lobby(pet.id, firm, null, Money.ZERO, c, p, Tick(1)).rejectionOrNull()?.rule)
        assertEquals(Rules.UNKNOWN_ENTITY, book.lobby(pet.id, firm, 99, Money.units(1_000), c, p, Tick(1)).rejectionOrNull()?.rule)
        assertEquals(Rules.UNKNOWN_ENTITY, book.lobby(PetitionId(9), firm, null, Money.units(1_000), c, p, Tick(1)).rejectionOrNull()?.rule)
        assertEquals(Rules.LIMIT_EXCEEDED, book.lobby(pet.id, firm, null, p.lobbyingCapPerPetition + Money.units(1), c, p, Tick(1)).rejectionOrNull()?.rule)
        assertTrue(book.lobby(pet.id, firm, 3, Money.units(20_000), c, p, Tick(1)).isOk)
        assertEquals(Money.units(20_000), pet.lobbied)
        assertTrue(c.seats[3].pressureFor(pet.id) > 0)
        assertEquals(0, c.seats[2].pressureFor(pet.id))
        decide(book, ctx(ds, c))
        assertEquals(Rules.WRONG_STATE, book.lobby(pet.id, firm, null, Money.units(1_000), c, p, Tick(200)).rejectionOrNull()?.rule)
    }

    @Test fun `the total spent on one petition is capped across efforts`() {
        val ds = UrbanTestSupport.districts()
        val c = council()
        val book = PetitionBook()
        val pet = file(book, ds).getOrThrow()
        assertTrue(book.lobby(pet.id, firm, null, Money.units(100_000), c, p, Tick(1)).isOk)
        assertEquals(Rules.LIMIT_EXCEEDED, book.lobby(pet.id, firm, null, Money.units(60_000), c, p, Tick(1)).rejectionOrNull()?.rule)
        assertEquals(1, book.effortsFor(pet.id).size)
        assertEquals(1, book.efforts().size)
    }

    @Test fun `modest lobbying can swing a close vote`() {
        var flipped = 0
        for (seed in 1L..12L) {
            val without = voteWith(seed, Money.ZERO)
            val with = voteWith(seed, Money.units(60_000))
            assertTrue(with >= without - 1)
            if (with > without) flipped++
        }
        assertTrue(flipped >= 6)
    }

    private fun voteWith(seed: Long, money: Money): Int {
        val ds = UrbanTestSupport.districts()
        val c = Council.seeded(9, 9, DeterministicRandom(seed))
        val book = PetitionBook()
        val pet = file(book, ds, to = ZoneUse.COMMERCIAL).getOrThrow()
        if (money.isPositive) book.lobby(pet.id, firm, null, money, c, p, Tick(1))
        decide(book, PetitionContext(g, ds, c, p, DeterministicRandom(seed + 100), emptyList()))
        return if (pet.votes.isEmpty()) 0 else pet.yes
    }

    @Test fun `excess lobbying can end in a scandal`() {
        var scandals = 0
        for (seed in 1L..40L) {
            val ds = UrbanTestSupport.districts()
            val c = council(ideology = 100, integrity = 900, ties = 0)
            val book = PetitionBook()
            val pet = file(book, ds).getOrThrow()
            book.lobby(pet.id, firm, null, p.lobbyingCapPerPetition, c, p, Tick(1))
            val events = decide(book, ctx(ds, c, seed))
            if (events.any { it is PetitionEvent.Scandal }) {
                scandals++
                assertEquals(PetitionStatus.DENIED, pet.status)
                assertEquals("Lobbying scandal", pet.note)
                assertTrue(c.moodAgainstGrowth >= 150)
                assertTrue(pet.votes.isEmpty())
            }
        }
        assertTrue(scandals in 1..39)
    }

    @Test fun `hooks add to inclination`() {
        val ds = UrbanTestSupport.districts()
        val book = PetitionBook()
        val pet = file(book, ds).getOrThrow()
        decide(book, ctx(ds, council(ideology = -500), hooks = listOf(LobbyingHook { _, _, _ -> 1_500 })))
        assertEquals(9, pet.yes)
    }

    @Test fun `public protest raises the bar to a supermajority`() {
        val ds = UrbanTestSupport.districts()
        val book = PetitionBook()
        val pet = file(book, ds, to = ZoneUse.INDUSTRIAL).getOrThrow()
        ds[4].backlashPpm = 900_000L
        val events = decide(book, ctx(ds, council(ideology = 0)))
        val voted = events.filterIsInstance<PetitionEvent.Voted>().firstOrNull()
        if (pet.protestPetition) assertEquals(6, voted?.needed) else assertEquals(5, voted?.needed)
    }

    @Test fun `staff favour compatible uses and dislike hard changes`() {
        val ds = UrbanTestSupport.districts()
        val book = PetitionBook()
        val good = file(book, ds, id = 1, district = 4, to = ZoneUse.MIXED).getOrThrow()
        val bad = file(book, ds, id = 2, district = 5, to = ZoneUse.INDUSTRIAL).getOrThrow()
        assertTrue(PetitionRules.staffScore(good, g, ds) > PetitionRules.staffScore(bad, g, ds))
        assertTrue(PetitionRules.staffScore(bad, g, ds) in -1000..1000)
    }

    @Test fun `opinion reflects shortage and anger`() {
        val ds = UrbanTestSupport.districts()
        val pet = file(PetitionBook(), ds, to = ZoneUse.MIXED).getOrThrow()
        ds[4].vacancyPpm = 0L
        val tight = PetitionRules.opinion(pet, g, ds, p)
        ds[4].vacancyPpm = 200_000L
        val loose = PetitionRules.opinion(pet, g, ds, p)
        assertTrue(tight.supportPermille > loose.supportPermille)
        ds[4].backlashPpm = 800_000L
        assertTrue(PetitionRules.opinion(pet, g, ds, p).oppositionPermille > loose.oppositionPermille)
        assertTrue(tight.supportPermille in 0..1000 && tight.oppositionPermille in 0..1000)
    }

    @Test fun `intensity change is signed and bounded`() {
        val ds = UrbanTestSupport.districts()
        val up = file(PetitionBook(), ds, to = ZoneUse.COMMERCIAL).getOrThrow()
        assertTrue(PetitionRules.intensityChange(up, ds[4]) > 0)
        val down = file(PetitionBook(), ds, to = ZoneUse.GREEN).getOrThrow()
        assertTrue(PetitionRules.intensityChange(down, ds[4]) < 0)
        assertEquals(down.toZone.defaultFarMilli, PetitionRules.targetFar(down))
    }

    @Test fun `neighbour compatibility averages the districts around`() {
        val ds = UrbanTestSupport.districts(zones = mapOf(1 to ZoneUse.INDUSTRIAL, 3 to ZoneUse.INDUSTRIAL, 5 to ZoneUse.INDUSTRIAL, 7 to ZoneUse.INDUSTRIAL))
        assertEquals(Zoning.compatibility(ZoneUse.RESIDENTIAL, ZoneUse.INDUSTRIAL), PetitionRules.neighbourCompatibility(ZoneUse.RESIDENTIAL, 4, g, ds))
        assertTrue(PetitionRules.neighbourCompatibility(ZoneUse.INDUSTRIAL, 4, g, ds) > 0)
    }

    @Test fun `decisions are repeatable`() {
        val a = run(ideology = 50, seed = 7L)
        val b = run(ideology = 50, seed = 7L)
        assertEquals(a.votes, b.votes)
        assertEquals(a.status, b.status)
        assertFalse(a.votes.isEmpty())
    }

    @Test fun `hash covers petitions and efforts`() {
        fun h(b: PetitionBook) = CanonicalHasher().also { b.hashInto(it) }.finish()
        val ds = UrbanTestSupport.districts()
        val a = PetitionBook(); val b = PetitionBook()
        file(a, ds); file(b, ds)
        assertEquals(h(a), h(b))
        b.lobby(PetitionId(1), firm, null, Money.units(100), council(), p, Tick(1))
        assertTrue(h(a) != h(b))
    }
}
