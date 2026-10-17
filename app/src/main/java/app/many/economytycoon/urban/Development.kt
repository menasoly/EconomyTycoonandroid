package app.many.economytycoon.urban

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/** What the developers did in a district in one month. */
data class DevelopmentResult(val district: DistrictId, val started: Long, val completed: Long, val demolished: Long)

/**
 * Private development: developers add floor area where the value of a new building clearly exceeds what it costs to
 * build on land of that price, up to the density limit; old, worn buildings are pulled down when the land is worth more
 * than the building and there is no headroom left.
 */
object Development {
    /** Relative rent per square metre of each kind of space, against housing. */
    private const val COMMERCIAL_RENT_PPM = 1_600_000L
    private const val INDUSTRIAL_RENT_PPM = 550_000L

    /** Rent of the average square metre of floor area in the zone, as a multiple of the housing rent. */
    fun useRentFactorPpm(zone: ZoneUse): Long {
        val res = zone.residentialShare.toLong()
        val com = zone.commercialShare.toLong()
        val ind = zone.industrialShare.toLong()
        return (res * Scale.PPM + com * COMMERCIAL_RENT_PPM + ind * INDUSTRIAL_RENT_PPM) / 1000L
    }

    /** Taller buildings cost more per square metre: 3 percent for every floor above four, at most doubling. */
    fun heightPremiumPpm(floors: Int): Long = (Scale.PPM + 30_000L * (floors - 4).coerceAtLeast(0)).coerceAtMost(2_000_000L)

    /** Value of a finished square metre in the district, from the rent it can earn. */
    fun valuePerSqm(d: District, params: UrbanParams): Money {
        val monthly = mulDiv(d.rentPerSqm.raw, useRentFactorPpm(d.zone), Scale.PPM)
        return Money(mulDiv(monthly * Scale.MONTHS_PER_YEAR, Scale.PPM, params.capRatePpm))
    }

    /** Cost of a finished square metre: construction plus the land it needs. */
    fun costPerSqm(d: District, params: UrbanParams): Money {
        val floors = d.rules.maxFloors.coerceAtMost(d.rules.effectiveFarMilli() / 150 + 1)
        val build = mulDiv(params.buildCostPerSqm.raw, heightPremiumPpm(floors), Scale.PPM)
        val far = d.rules.effectiveFarMilli().coerceAtLeast(300).toLong()
        val land = mulDiv(d.landValuePerSqm.raw, 1000L, far)
        return Money(build + land)
    }

    /** Profit of building a new square metre as a share of its cost, ppm (negative when it loses money). */
    fun marginPpm(d: District, params: UrbanParams): Long {
        val cost = costPerSqm(d, params).raw
        if (cost <= 0L) return 0L
        return mulDiv(valuePerSqm(d, params).raw - cost, Scale.PPM, cost)
    }

    /** Months a project takes to build, from its height. */
    fun buildMonths(d: District): Int = 8 + d.rules.maxFloors.coerceAtMost(40) / 3

    /**
     * Floor area developers start this month. [boomPpm] raises appetite during a boom; tenant protection and the lack of
     * a positive margin hold building back. Public uses (green space, civic) are never built by developers.
     */
    fun monthlyStarts(d: District, params: UrbanParams, boomPpm: Long = Scale.PPM): Long {
        if (d.zone == ZoneUse.GREEN || d.zone == ZoneUse.CIVIC) return 0L
        val margin = marginPpm(d, params)
        if (margin < params.developerHurdlePpm) return 0L
        val room = d.headroom
        if (room <= 0L) return 0L
        val appetite = (mulDiv(margin - params.developerHurdlePpm, Scale.PPM, params.developerHurdlePpm)).coerceIn(100_000L, 3_000_000L)
        val ceiling = mulDiv(d.rules.maxFloorArea(d.landSqm), params.maxStartsPpm, Scale.PPM)
        var starts = mulDiv(ceiling, appetite, Scale.PPM)
        starts = mulDiv(starts, boomPpm, Scale.PPM)
        starts = mulDiv(starts, 1000L - d.tenantProtectionPermille * 3L / 10L, 1000L)
        return starts.coerceIn(0L, room)
    }

    /**
     * Area pulled down this month for redevelopment: only when the district is full, has old stock in poor condition
     * and building would be profitable.
     */
    fun monthlyDemolition(d: District, params: UrbanParams): Long {
        if (d.zone == ZoneUse.GREEN || d.zone == ZoneUse.CIVIC) return 0L
        if (d.headroom > d.maxFloorArea / 20L) return 0L
        if (d.stock.averageConditionPpm > 550_000L || d.stock.totalArea == 0L) return 0L
        if (marginPpm(d, params) < params.developerHurdlePpm * 2L) return 0L
        val share = mulDiv(d.floorArea, 6_000L, Scale.PPM) // 0.6 percent of the stock a month
        return share * (1000L - d.tenantProtectionPermille * 6L / 10L) / 1000L
    }

    /** Applies one month of development to the district and returns what happened. */
    fun step(d: District, params: UrbanParams, now: Tick, boomPpm: Long = Scale.PPM, frozen: Boolean = false): DevelopmentResult {
        var completed = 0L
        if (d.pipelineArea > 0L && now >= d.pipelineReadyAt) {
            completed = d.pipelineArea
            d.stock.addNew(completed)
            d.pipelineArea = 0L
        }
        val demolished = if (d.pipelineArea == 0L && !frozen) d.stock.demolish(monthlyDemolition(d, params), sparePrewar = d.heritage) else 0L
        val started = if (frozen) 0L else monthlyStarts(d, params, boomPpm)
        if (started > 0L) {
            d.pipelineArea += started
            val ready = Tick(now.value + buildMonths(d) * Scale.MONTH_DAYS)
            if (ready > d.pipelineReadyAt) d.pipelineReadyAt = ready
        }
        return DevelopmentResult(d.id, started, completed, demolished)
    }
}
