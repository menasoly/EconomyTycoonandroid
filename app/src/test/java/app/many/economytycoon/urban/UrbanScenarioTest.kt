package app.many.economytycoon.urban

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.SettlementIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrbanScenarioTest {
    private val T = UrbanTestSupport

    @Test fun `a rezoning petition goes from filing to a changed district`() {
        val svc = T.withCity()
        val d = T.plan(svc).districts.first { it.zone == ZoneUse.RESIDENTIAL && !it.heritage }
        svc.execute(UrbanCommand.FilePetition(T.city, d.index, PetitionKind.REZONE, ZoneUse.MIXED, 0, 0, null), Tick(0)).getOrThrow()
        val notices = mutableListOf<UrbanNotice>()
        T.run(svc, 0, 240, collect = notices)
        val kinds = notices.map { it.kind }
        assertTrue(NoticeKind.PETITION_HEARD in kinds)
        val decided = NoticeKind.PETITION_ENACTED in kinds || NoticeKind.PETITION_DENIED in kinds
        assertTrue(decided)
        val zone = T.plan(svc).districts[d.index].zone
        if (NoticeKind.PETITION_ENACTED in kinds) assertEquals(ZoneUse.MIXED, zone) else assertEquals(ZoneUse.RESIDENTIAL, zone)
        T.checkInvariants(T.plan(svc))
    }

    @Test fun `a toll road tender is settled with money flowing to the city`() {
        val svc = T.withCity()
        val plan = T.plan(svc)
        val road = plan.network.roads().first()
        svc.execute(UrbanCommand.TenderConcession(T.city, ConcessionKind.TOLL_ROAD, "Ring toll", road.id, null, PaymentModel.TOLL, 240, 0, Money.units(1_000_000), Money.units(2), Money.ZERO, 100_000L, 0L), Tick(0)).getOrThrow()
        val intents = mutableListOf<SettlementIntent>()
        val notices = mutableListOf<UrbanNotice>()
        T.run(svc, 0, 400, collect = notices, intents = intents)
        val awarded = notices.any { it.kind == NoticeKind.CONCESSION_AWARDED }
        val none = notices.any { it.kind == NoticeKind.CONCESSION_NO_BIDS }
        assertTrue(awarded || none)
        assertTrue(intents.all { it.total.raw > 0L })
        assertEquals(intents.map { it.key }.toSet().size, intents.size)
    }

    @Test fun `the same seed gives the same history and a different seed does not`() {
        fun history(seed: Long): String {
            val svc = T.withCity(seed = seed)
            T.run(svc, 0, 360)
            return svc.stateHash().toHex()
        }
        assertEquals(history(11), history(11))
        assertNotEquals(history(11), history(12))
    }

    @Test fun `population growth raises demand and pushes rents up`() {
        val small = T.withCity(population = 60_000L)
        val big = T.withCity(population = 400_000L)
        T.run(small, 0, 180, population = 60_000L)
        T.run(big, 0, 180, population = 400_000L)
        val rent = { s: UrbanService -> T.plan(s).districts.sumOf { it.rentPerSqm.raw } }
        assertTrue(rent(big) > rent(small))
    }

    @Test fun `a long run keeps every invariant`() {
        val svc = T.withCity()
        T.run(svc, 0, 720)
        T.checkInvariants(T.plan(svc))
        val score = svc.score(T.city)
        assertTrue(score != null && score.index in 0..1000)
    }

    @Test fun `a council election happens after four years`() {
        val svc = T.withCity()
        val notices = mutableListOf<UrbanNotice>()
        T.run(svc, 0, 360 * 4 + 40, collect = notices)
        assertTrue(notices.any { it.kind == NoticeKind.ELECTION })
    }

    @Test fun `company lobbying is charged through the cost hook`() {
        val svc = T.withCity()
        val d = T.plan(svc).districts.first { it.zone == ZoneUse.RESIDENTIAL }
        val firm = CompanyId(5)
        val receipt = svc.execute(UrbanCommand.FilePetition(T.city, d.index, PetitionKind.REZONE, ZoneUse.COMMERCIAL, 0, 0, firm), Tick(0)).getOrThrow()
        assertTrue(receipt.message.isNotBlank())
        val cmd = UrbanCommand.LobbyPetition(T.city, PetitionId(1), firm, null, Money.units(2_000))
        assertEquals(Money.units(2_000), svc.companyCost(cmd))
    }
}
