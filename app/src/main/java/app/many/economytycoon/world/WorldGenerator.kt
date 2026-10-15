package app.many.economytycoon.world

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.content.TransportMode
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.DepositId
import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.ParcelId
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.SegmentId

/**
 * Builds a complete world from a seed. Everything is a pure function of (seed, size, content): the same
 * inputs always produce the same countries, cities, deposits, network and parcels.
 */
class WorldGenerator(private val content: ContentRepository, private val ids: IdAllocators) {

    fun generate(params: WorldParams): WorldState {
        val random = RandomProvider(params.seed)
        val world = WorldState(params)
        val usedNames = HashSet<String>()
        val geo = random.stream("worldgen.geography")
        val countryCount = params.size.countries
        val centers = placeCenters(geo, countryCount * params.size.regionsPerCountry)

        // Countries own consecutive groups of region centers, grouped by proximity.
        val grouped = groupCenters(centers, countryCount, params.size.regionsPerCountry)
        val allCities = ArrayList<City>()
        for ((ci, group) in grouped.withIndex()) {
            val crnd = random.stream("worldgen.country", ci.toLong())
            val countryId = CountryId(ids.country().value)
            val countryName = Names.country(crnd, usedNames)
            val regionIds = ArrayList<RegionId>()
            var capital: CityId? = null
            for ((ri, center) in group.withIndex()) {
                val rrnd = random.stream("worldgen.region", countryId.value * 100 + ri)
                val regionId = ids.region()
                regionIds.add(regionId)
                val terrain = pickTerrain(rrnd, center)
                val coastal = center.x < 120 || center.x > WorldMap.SIZE - 120 || center.y < 120 || center.y > WorldMap.SIZE - 120 || rrnd.chance(app.many.economytycoon.core.Rate.percent(18))
                val region = Region(
                    id = regionId, country = countryId, name = Names.region(rrnd, countryName, ri), center = center, terrain = terrain,
                    climate = pickClimate(rrnd, center), area = 9_000L + rrnd.nextLong(9_000L),
                    fertilityPpm = (terrain.farmlandPpm * (800_000L + rrnd.nextLong(400_000L)) / 1_000_000L).coerceIn(50_000L, 1_000_000L),
                    coastal = coastal,
                )
                world.addRegion(region)
                val cityCount = params.size.citiesPerRegion
                val locs = placeCities(rrnd, center, cityCount)
                for ((k, loc) in locs.withIndex()) {
                    val cid = ids.city()
                    val big = k == 0
                    val pop = if (big) 140_000L + rrnd.nextLong(500_000L) else 25_000L + rrnd.nextLong(110_000L)
                    val isCap = capital == null && big
                    val city = City(
                        id = cid, region = regionId, name = Names.city(rrnd, usedNames), location = loc, coastal = coastal && (k == 0 || rrnd.nextBoolean()),
                        foundedPopulation = pop, hasPort = false, hasAirport = false, isCapital = isCap,
                    )
                    val withFacilities = city.copy(hasPort = city.coastal && pop > 60_000L, hasAirport = pop > 200_000L || isCap)
                    if (isCap) capital = cid
                    world.addCity(withFacilities, populationFor(cid, pop, rrnd), pop / 2)
                    allCities.add(withFacilities)
                }
            }
            val trng = random.stream("worldgen.policy", countryId.value)
            world.addCountry(Country(
                id = countryId, name = countryName, capital = capital,
                corporateTaxPpm = 150_000L + trng.nextLong(150_000L), salesTaxPpm = 40_000L + trng.nextLong(120_000L),
                payrollTaxPpm = 60_000L + trng.nextLong(100_000L), minimumWagePerTick = 35L + trng.nextLong(25L),
                environmentalStrictnessPpm = 200_000L + trng.nextLong(600_000L), tariffPpm = 20_000L + trng.nextLong(120_000L),
                stabilityPpm = 700_000L + trng.nextLong(280_000L),
            ))
        }
        world.finishConstruction()
        buildNetwork(world, random.stream("worldgen.network"))
        placeDeposits(world, random)
        placeParcels(world, random)
        return world
    }

    // ----- layout ---------------------------------------------------------------------------

    private fun placeCenters(rnd: DeterministicRandom, count: Int): List<Point> {
        val minSpacing = (WorldMap.SIZE * 7 / 10) / Math.sqrt(count.toDouble()).toInt().coerceAtLeast(1) / 2 + 40
        val out = ArrayList<Point>()
        var attempts = 0
        var spacing = minSpacing
        while (out.size < count) {
            val p = Point(80 + rnd.nextInt(WorldMap.SIZE - 160), 80 + rnd.nextInt(WorldMap.SIZE - 160))
            if (out.all { it.distanceTo(p) >= spacing }) out.add(p)
            if (++attempts % 400 == 0) spacing = (spacing * 9 / 10).coerceAtLeast(30)
        }
        return out
    }

    /** Splits region centers into country groups of equal size by greedy nearest assignment. */
    private fun groupCenters(centers: List<Point>, countries: Int, perCountry: Int): List<List<Point>> {
        if (countries == 1) return listOf(centers.sorted())
        val remaining = centers.sorted().toMutableList()
        val groups = ArrayList<List<Point>>()
        repeat(countries) {
            val seed = remaining.removeAt(0)
            val group = arrayListOf(seed)
            while (group.size < perCountry && remaining.isNotEmpty()) {
                val next = remaining.minWithOrNull(compareBy<Point>({ group.minOf { g -> g.squaredDistanceTo(it) } }, { it }))!!
                remaining.remove(next)
                group.add(next)
            }
            groups.add(group)
        }
        return groups
    }

    private fun placeCities(rnd: DeterministicRandom, center: Point, count: Int): List<Point> {
        val out = ArrayList<Point>()
        out.add(center)
        var guard = 0
        while (out.size < count && guard++ < 500) {
            val angle = rnd.nextInt(360)
            val radius = 25 + rnd.nextInt(55)
            val rad = Math.toRadians(angle.toDouble())
            val p = Point((center.x + Math.round(StrictMath.cos(rad) * radius)).toInt().coerceIn(10, WorldMap.SIZE - 10), (center.y + Math.round(StrictMath.sin(rad) * radius)).toInt().coerceIn(10, WorldMap.SIZE - 10))
            if (out.all { it.distanceTo(p) >= 18 }) out.add(p)
        }
        return out
    }

    private fun pickTerrain(rnd: DeterministicRandom, p: Point): Terrain {
        val roll = rnd.nextInt(100)
        val edge = p.x < 150 || p.x > 850 || p.y < 150 || p.y > 850
        return when {
            edge && roll < 25 -> Terrain.COAST
            roll < 38 -> Terrain.PLAINS
            roll < 56 -> Terrain.HILLS
            roll < 66 -> Terrain.MOUNTAINS
            roll < 74 -> Terrain.DESERT
            roll < 88 -> Terrain.FOREST
            roll < 94 -> Terrain.WETLAND
            else -> Terrain.PLAINS
        }
    }

    private fun pickClimate(rnd: DeterministicRandom, p: Point): ClimateZone {
        val lat = p.y // 0 north (cold) .. 1000 south (hot)
        val roll = rnd.nextInt(100)
        return when {
            lat < 220 -> if (roll < 70) ClimateZone.COLD else ClimateZone.TEMPERATE
            lat < 450 -> if (roll < 70) ClimateZone.TEMPERATE else ClimateZone.MEDITERRANEAN
            lat < 700 -> if (roll < 40) ClimateZone.MEDITERRANEAN else if (roll < 70) ClimateZone.ARID else ClimateZone.TEMPERATE
            else -> if (roll < 55) ClimateZone.TROPICAL else ClimateZone.ARID
        }
    }

    private fun populationFor(city: CityId, total: Long, rnd: DeterministicRandom): PopulationState {
        val children = total * (180L + rnd.nextLong(40L)) / 1000L
        val seniors = total * (110L + rnd.nextLong(60L)) / 1000L
        val p = PopulationState(city, children, total - children - seniors, seniors)
        p.employed = (p.laborForce * 930L) / 1000L
        return p
    }

    // ----- network --------------------------------------------------------------------------

    private fun buildNetwork(world: WorldState, rnd: DeterministicRandom) {
        val cities = world.cities()
        val linked = HashSet<Long>()
        fun key(a: CityId, b: CityId) = minOf(a.value, b.value) * 100_000L + maxOf(a.value, b.value)
        fun link(a: City, b: City, mode: TransportMode, capacity: Long) {
            if (!linked.add(key(a.id, b.id) * 10 + mode.ordinal)) return
            val len = a.location.distanceTo(b.location).coerceAtLeast(1)
            world.network.add(Segment(SegmentId(ids.segment().value), a.id, b.id, mode, len, Quantity.units(capacity)))
        }
        // Roads: every city links to its three nearest neighbors.
        for (c in cities) {
            val nearest = cities.filter { it.id != c.id }.sortedWith(compareBy({ it.location.squaredDistanceTo(c.location) }, { it.id })).take(3)
            for (n in nearest) link(c, n, TransportMode.ROAD, 4_000)
        }
        ensureConnected(world, ::link)
        // Rail between larger cities that are reasonably close.
        val large = cities.filter { world.population(it.id).total > 120_000L }
        for (c in large) {
            val partners = large.filter { it.id != c.id }.sortedWith(compareBy({ it.location.squaredDistanceTo(c.location) }, { it.id })).take(2)
            for (n in partners) if (n.location.distanceTo(c.location) < 400) link(c, n, TransportMode.RAIL, 6_000)
        }
        // Sea lanes between ports, and air links between airports.
        val ports = cities.filter { it.hasPort }
        for (c in ports) {
            val partners = ports.filter { it.id != c.id }.sortedWith(compareBy({ it.location.squaredDistanceTo(c.location) }, { it.id })).take(2)
            for (n in partners) link(c, n, TransportMode.SEA, 15_000)
        }
        val airports = cities.filter { it.hasAirport }
        for (c in airports) {
            val partners = airports.filter { it.id != c.id }.sortedWith(compareBy({ it.location.squaredDistanceTo(c.location) }, { it.id })).take(3)
            for (n in partners) link(c, n, TransportMode.AIR, 800)
        }
        rnd.nextLong() // keep the stream position independent of layout counts
    }

    private fun ensureConnected(world: WorldState, link: (City, City, TransportMode, Long) -> Unit) {
        val cities = world.cities()
        while (!world.network.connected(cities.map { it.id })) {
            // Find the closest pair in different components and join them.
            val seen = HashSet<CityId>()
            val stack = ArrayDeque<CityId>()
            stack.add(cities.first().id); seen.add(cities.first().id)
            while (stack.isNotEmpty()) { val c = stack.removeLast(); for (n in world.network.neighbors(c)) if (seen.add(n)) stack.add(n) }
            var best: Triple<Long, City, City>? = null
            for (a in cities.filter { it.id in seen }) for (b in cities.filter { it.id !in seen }) {
                val d = a.location.squaredDistanceTo(b.location)
                if (best == null || d < best.first) best = Triple(d, a, b)
            }
            val (_, a, b) = best ?: return
            link(a, b, TransportMode.ROAD, 3_000)
        }
    }

    // ----- resources and land ---------------------------------------------------------------

    private fun placeDeposits(world: WorldState, random: RandomProvider) {
        val extractable = content.allFacilityTypes.filter { it.extracts != null }
        val goods = extractable.mapNotNull { it.extracts }.distinct().sorted()
        for (region in world.regions()) {
            val rnd = random.stream("worldgen.deposits", region.id.value)
            fun place(good: GoodId) {
                val def = content.goodOrNull(good) ?: return
                val renewable = good.key in RENEWABLES
                val cheap = def.referencePrice.perUnit.raw <= 500_000L
                val reserve = Quantity.units(if (renewable) 400_000L + rnd.nextLong(600_000L) else 60_000L + rnd.nextLong(240_000L) * (if (cheap) 2 else 1))
                val loc = Point((region.center.x + rnd.nextInt(80) - 40).coerceIn(5, WorldMap.SIZE - 5), (region.center.y + rnd.nextInt(80) - 40).coerceIn(5, WorldMap.SIZE - 5))
                val d = Deposit(
                    id = DepositId(ids.deposit().value), region = region.id, location = loc, good = good,
                    kind = if (renewable) DepositKind.RENEWABLE else DepositKind.FINITE, initialReserve = reserve,
                    gradePpm = 600_000L + rnd.nextLong(800_000L), regrowthPpm = if (renewable) 400L else 0L,
                )
                if (rnd.chance(app.many.economytycoon.core.Rate.percent(35))) d.surveyed = true
                world.deposits.add(d)
            }
            var placed = 0
            for (good in goods) {
                if (rnd.nextInt(100) < affinity(good, region.terrain)) { place(good); placed++ }
            }
            // Every region has at least one deposit: its best-matching resource if every roll missed.
            if (placed == 0) goods.maxByOrNull { affinity(it, region.terrain) }?.let { place(it) }
        }
    }

    private fun affinity(good: GoodId, terrain: Terrain): Int {
        val h = (good.key.hashCode() * 31 + terrain.ordinal * 17) and 0x7FFFFFFF
        val base = h % 60 + 15
        val bonus = when {
            good.key.contains("oil") || good.key.contains("gas") -> if (terrain == Terrain.DESERT || terrain == Terrain.COAST) 30 else 0
            good.key.contains("coal") || good.key.contains("iron") || good.key.contains("copper") -> if (terrain == Terrain.MOUNTAINS || terrain == Terrain.HILLS) 30 else 0
            good.key.contains("logs") || good.key.contains("timber") -> if (terrain == Terrain.FOREST) 50 else 0
            good.key.contains("water") -> if (terrain == Terrain.WETLAND || terrain == Terrain.COAST) 25 else 0
            else -> 0
        }
        return (base + bonus).coerceAtMost(95)
    }

    private fun placeParcels(world: WorldState, random: RandomProvider) {
        for (city in world.cities()) {
            val rnd = random.stream("worldgen.parcels", city.id.value)
            val pop = world.population(city.id).total
            val count = (8 + pop / 25_000L).toInt().coerceAtMost(40)
            val region = world.region(city.region)
            val zones = listOf(ZoneType.INDUSTRIAL, ZoneType.COMMERCIAL, ZoneType.RESIDENTIAL, ZoneType.MIXED, ZoneType.AGRICULTURAL, ZoneType.INDUSTRIAL)
            for (i in 0 until count) {
                val angle = Math.toRadians(rnd.nextInt(360).toDouble())
                val radius = 2 + rnd.nextInt(38)
                val loc = Point((city.location.x + Math.round(StrictMath.cos(angle) * radius)).toInt().coerceIn(0, WorldMap.SIZE - 1), (city.location.y + Math.round(StrictMath.sin(angle) * radius)).toInt().coerceIn(0, WorldMap.SIZE - 1))
                val zone = if (radius < 10) listOf(ZoneType.COMMERCIAL, ZoneType.MIXED, ZoneType.RESIDENTIAL)[rnd.nextInt(3)]
                else if (radius > 30) listOf(ZoneType.AGRICULTURAL, ZoneType.INDUSTRIAL, ZoneType.PROTECTED)[rnd.nextInt(3)]
                else zones[rnd.nextInt(zones.size)]
                val area = (zoneArea(zone) * (700 + rnd.nextInt(700)) / 1000)
                world.land.add(Parcel(ParcelId(ids.parcel().value), region.id, city.id, loc, zone, area, region.terrain))
            }
        }
        for (region in world.regions()) {
            val rnd = random.stream("worldgen.mining", region.id.value)
            val n = 2 + rnd.nextInt(3)
            for (i in 0 until n) {
                val loc = Point((region.center.x + rnd.nextInt(90) - 45).coerceIn(0, WorldMap.SIZE - 1), (region.center.y + rnd.nextInt(90) - 45).coerceIn(0, WorldMap.SIZE - 1))
                world.land.add(Parcel(ParcelId(ids.parcel().value), region.id, null, loc, if (i == 0) ZoneType.MINING else ZoneType.AGRICULTURAL, 300 + rnd.nextInt(600), region.terrain))
            }
        }
    }

    private fun zoneArea(zone: ZoneType): Int = when (zone) {
        ZoneType.INDUSTRIAL -> 160
        ZoneType.COMMERCIAL -> 40
        ZoneType.RESIDENTIAL -> 70
        ZoneType.MIXED -> 60
        ZoneType.AGRICULTURAL -> 400
        ZoneType.MINING -> 300
        ZoneType.PROTECTED -> 200
    }

    private companion object {
        val RENEWABLES = setOf("logs", "water", "fish", "wild_game", "sunlight", "wind", "geothermal")
    }
}
