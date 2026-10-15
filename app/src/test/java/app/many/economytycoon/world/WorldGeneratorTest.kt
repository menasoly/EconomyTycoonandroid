package app.many.economytycoon.world

import app.many.economytycoon.world.WorldTestSupport.generate
import app.many.economytycoon.world.WorldTestSupport.hash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldGeneratorTest {
    @Test fun sameSeedGivesSameWorld() {
        assertEquals(hash(generate(42).first), hash(generate(42).first))
    }

    @Test fun differentSeedsGiveDifferentWorlds() {
        assertTrue(hash(generate(42).first) != hash(generate(43).first))
    }

    @Test fun sizesProduceTheRequestedStructure() {
        for (size in listOf(WorldSize.SMALL, WorldSize.MEDIUM, WorldSize.LARGE)) {
            val (w, _) = generate(7, size)
            assertEquals(size.countries, w.countries().size)
            assertEquals(size.countries * size.regionsPerCountry, w.regions().size)
            assertTrue(w.cities().size >= w.regions().size)
            assertTrue(w.cities().size <= size.countries * size.regionsPerCountry * size.citiesPerRegion)
        }
    }

    @Test fun everyReferenceResolves() {
        val (w, _) = generate(11)
        for (c in w.cities()) { w.region(c.region); w.population(c.id) }
        for (r in w.regions()) w.country(r.country)
        for (country in w.countries()) assertTrue(country.capital?.let { w.cityOrNull(it) } != null)
        for (s in w.network.all()) { w.city(s.a); w.city(s.b) }
        for (d in w.deposits.all()) w.region(d.region)
        for (p in w.land.all()) w.region(p.region)
    }

    @Test fun worldIsFullyConnectedByRoad() {
        for (seed in 1L..8L) {
            val (w, _) = generate(seed, WorldSize.LARGE)
            assertTrue("seed $seed", w.network.connected(w.cities().map { it.id }))
        }
    }

    @Test fun everyCountryHasACapitalAndUniqueNames() {
        val (w, _) = generate(5, WorldSize.LARGE)
        assertTrue(w.countries().all { it.capital != null })
        assertEquals(w.cities().size, w.cities().map { it.name }.toSet().size)
        assertEquals(w.countries().size, w.countries().map { it.name }.toSet().size)
    }

    @Test fun citiesStayInsideTheMap() {
        val (w, _) = generate(19, WorldSize.LARGE)
        assertTrue(w.cities().all { WorldMap.inBounds(it.location) })
        assertTrue(w.land.all().all { WorldMap.inBounds(it.location) })
    }

    @Test fun depositsAndParcelsExistForEveryRegion() {
        val (w, _) = generate(23)
        for (r in w.regions()) {
            assertTrue("deposits in ${r.name}", w.deposits.inRegion(r.id).isNotEmpty())
            assertTrue("parcels in ${r.name}", w.land.inRegion(r.id).isNotEmpty())
        }
    }

    @Test fun populationsAreConsistent() {
        val (w, _) = generate(31)
        for (p in w.populations()) {
            assertTrue(p.total > 10_000)
            assertEquals(1_000_000L, p.lowShare + p.middleShare + p.highShare)
            assertTrue(p.employed <= p.laborForce)
        }
    }

    @Test fun routesExistBetweenAnyTwoCitiesInASmallWorld() {
        val (w, _) = generate(3, WorldSize.SMALL)
        val cities = w.cities()
        for (a in cities) for (b in cities) assertTrue(w.network.route(a.id, b.id, 0) != null)
    }
}
