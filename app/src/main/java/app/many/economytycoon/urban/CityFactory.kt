package app.many.economytycoon.urban

import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/** Lays out a new city plan: zones around a centre, an existing building stock sized to the population, roads and a bus line. */
object CityFactory {
    private val AGE_SHARES = arrayOf(
        intArrayOf(50, 100, 250, 300, 300), // centre
        intArrayOf(100, 200, 300, 250, 150), // inner ring
        intArrayOf(250, 300, 250, 150, 50), // outer districts
    )
    private val BAND_CONDITION = longArrayOf(900_000L, 800_000L, 650_000L, 500_000L, 380_000L)

    /** Distance of a district from the middle of the district grid, in rings. */
    fun ring(index: Int, side: Int): Int {
        val mid = (side - 1) / 2
        return maxOf(Math.abs(index % side - mid), Math.abs(index / side - mid))
    }

    private fun compass(index: Int, side: Int): String {
        val mid = (side - 1) / 2
        val dx = index % side - mid
        val dy = index / side - mid
        if (dx == 0 && dy == 0) return "Old Town"
        val ns = if (dy < 0) "North" else if (dy > 0) "South" else ""
        val ew = if (dx < 0) "West" else if (dx > 0) "East" else ""
        return listOf(ns, ew).filter { it.isNotEmpty() }.joinToString("-")
    }

    private fun zoneFor(ring: Int, random: DeterministicRandom): ZoneUse {
        if (ring == 0) return ZoneUse.COMMERCIAL
        val roll = random.nextInt(100)
        return when {
            roll < 45 -> ZoneUse.RESIDENTIAL
            roll < 62 -> ZoneUse.MIXED
            roll < 76 -> ZoneUse.INDUSTRIAL
            roll < 88 -> ZoneUse.GREEN
            else -> ZoneUse.CIVIC
        }
    }

    /**
     * [layout] gives the zone of every district when the caller knows it (for instance from the land of the real city);
     * it must have one entry per district. Without it the zones are drawn from the layout stream.
     */
    fun create(state: UrbanState, city: CityId, country: CountryId, name: String, inputs: CityInputs, layout: List<ZoneUse>? = null): CityPlan {
        val params = state.params
        val random = state.stream("layout", city)
        val side = params.districtsPerSide
        val count = params.districtCount
        val zones = (if (layout != null && layout.size == count) layout else (0 until count).map { zoneFor(ring(it, side), random) }).toMutableList()
        // Every city needs somewhere to live; larger ones also get some industry and a park.
        if (zones.none { it == ZoneUse.RESIDENTIAL || it == ZoneUse.MIXED }) zones[count - 1] = ZoneUse.RESIDENTIAL
        if (count >= 4) {
            val outer = (0 until count).sortedByDescending { ring(it, side) * 100 + it }
            if (zones.none { it == ZoneUse.INDUSTRIAL }) zones[outer.first { zones[it] != ZoneUse.RESIDENTIAL || ring(it, side) > 0 }] = ZoneUse.INDUSTRIAL
            if (zones.none { it == ZoneUse.GREEN }) zones[outer.last { ring(it, side) > 0 && zones[it] != ZoneUse.INDUSTRIAL }] = ZoneUse.GREEN
        }
        val geometry = CityGeometry.forLayout(inputs.population, zones, params)
        val districts = (0 until count).map { i ->
            val zone = zones[i]
            val rules = DensityRules.defaultsFor(zone)
            val label = compass(i, side)
            val dname = if (ring(i, side) == 0) label else "$label ${i + 1}"
            District(state.ids.district(), city, i, dname, zone, rules, geometry.districtAreaSqm).also { it.heritage = ring(i, side) == 0 && side > 1 }
        }
        seedStock(districts, geometry, side, inputs, params, random)
        val council = Council.seeded(params.councilSize, count, state.stream("council", city))
        val plan = CityPlan(city, country, name, geometry, districts, council)
        plan.inputs = inputs
        plan.lastMonthly = Tick(0)
        plan.budget.restore(
            TaxRates(), Money.units(20) * inputs.population, Money.ZERO, plan.budget.annualBaseRatePpm, Money.ZERO, 0,
            plan.budget.levelsList(), plan.budget.qualityList(),
        )
        buildNetwork(plan, state)
        return plan
    }

    private fun seedStock(districts: List<District>, geometry: CityGeometry, side: Int, inputs: CityInputs, params: UrbanParams, random: DeterministicRandom) {
        val fill = Fill(side)
        val wanted = inputs.population * 108L / 100L
        var capacity = 0L
        val areas = LongArray(districts.size)
        for (d in districts) {
            val far = d.zone.defaultFarMilli * fill.factorPermille(d.index) / 1000L
            areas[d.index] = if (d.zone == ZoneUse.GREEN) d.landSqm * 20L / 1000L else mulDiv(d.landSqm, far, 1000L)
            capacity += Zoning.capacity(d.zone, areas[d.index], params)
        }
        val scale = if (capacity <= 0L) Scale.PPM else mulDiv(wanted.coerceAtLeast(1L), Scale.PPM, capacity)
        for (d in districts) {
            if (d.zone == ZoneUse.GREEN) continue
            areas[d.index] = (mulDiv(areas[d.index], scale, Scale.PPM)).coerceAtMost(d.rules.maxFloorArea(d.landSqm) * 95L / 100L)
        }
        // Shops, factories and offices are sized so that the jobs roughly match the people who want to work.
        val workers = mulDiv(inputs.population, params.participationPpm, Scale.PPM)
        val adjustable = districts.filter { it.zone == ZoneUse.COMMERCIAL || it.zone == ZoneUse.INDUSTRIAL || it.zone == ZoneUse.CIVIC }
        val adjustableJobs = adjustable.sumOf { Zoning.jobs(it.zone, areas[it.index], params) }
        val otherJobs = districts.sumOf { Zoning.jobs(it.zone, areas[it.index], params) } - adjustableJobs
        if (adjustableJobs > 0L) {
            val f = mulDiv((workers * 105L / 100L - otherJobs).coerceAtLeast(workers / 10L), Scale.PPM, adjustableJobs)
            for (d in adjustable) areas[d.index] = mulDiv(areas[d.index], f, Scale.PPM).coerceAtMost(d.rules.maxFloorArea(d.landSqm) * 95L / 100L)
        }
        for (d in districts) {
            val area = areas[d.index]
            val shares = AGE_SHARES[ring(d.index, side).coerceAtMost(2)]
            val bands = LongArray(BuildingStock.BANDS) { area * shares[it] / 1000L }
            bands[BuildingStock.BANDS - 1] += area - bands.sum()
            val conds = LongArray(BuildingStock.BANDS) { (BAND_CONDITION[it] + random.nextInRange(-40_000L, 40_000L)).coerceIn(100_000L, 980_000L) }
            d.stock.restore(bands, conds, LongArray(BuildingStock.BANDS))
            d.lowIncomeSharePpm = inputs.lowIncomeSharePpm
            d.rentPerSqm = Housing.anchorRent(inputs.averageWage, params)
        }
    }

    /** Density of the starting city: fullest in the centre, thinning toward the edge. */
    private class Fill(private val side: Int) {
        fun factorPermille(index: Int): Long = when (ring(index, side)) {
            0 -> 700L
            1 -> 550L
            else -> 400L
        }
    }

    private fun buildNetwork(plan: CityPlan, state: UrbanState) {
        val g = plan.geometry
        val side = g.districtsPerSide
        for (a in 0 until g.districtCount) for (b in g.neighbours(a)) {
            if (b < a) continue
            val central = ring(a, side) == 0 || ring(b, side) == 0
            plan.network.addRoad(state.ids.link(), a, b, if (central) RoadClass.ARTERIAL else RoadClass.LOCAL, 2)
        }
        if (side >= 2) {
            val row = (side - 1) / 2
            val stops = (0 until side).map { row * side + it }
            val line = plan.network.planLine(state.ids.line(), "Crosstown bus", TransitMode.BUS, stops, 600, Money.units(2), false).getOrNull()
            line?.status = LineStatus.ACTIVE
        }
    }
}
