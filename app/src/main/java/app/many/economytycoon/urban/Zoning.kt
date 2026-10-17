package app.many.economytycoon.urban

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.mulDiv

/**
 * What a district may be used for. The permille shares say how the floor area of a district in that zone is split
 * between housing, shops and offices, industry and civic uses (they always sum to 1000). Emissions are permille
 * points per fully built cell; [amenity] is what the zone adds to the neighbourhood.
 */
enum class ZoneUse(
    val label: String,
    val residentialShare: Int,
    val commercialShare: Int,
    val industrialShare: Int,
    val civicShare: Int,
    val defaultFarMilli: Int,
    val defaultFloors: Int,
    val landValueFactorPpm: Long,
    val pollution: Int,
    val noise: Int,
    val crimeExposure: Int,
    val amenity: Int,
) {
    RESIDENTIAL("Residential", 920, 80, 0, 0, 2_400, 8, 1_000_000L, 20, 60, 150, 120),
    COMMERCIAL("Commercial", 40, 940, 20, 0, 5_000, 25, 1_600_000L, 90, 220, 380, 260),
    INDUSTRIAL("Industrial", 0, 100, 900, 0, 1_500, 5, 650_000L, 700, 600, 260, 0),
    MIXED("Mixed use", 500, 420, 30, 50, 3_500, 14, 1_300_000L, 60, 180, 280, 300),
    GREEN("Green space", 0, 100, 0, 900, 100, 1, 300_000L, 0, 10, 100, 700),
    CIVIC("Civic", 0, 0, 0, 1000, 2_000, 6, 500_000L, 10, 90, 120, 450);

    init {
        check(residentialShare + commercialShare + industrialShare + civicShare == 1000) { "$name shares must sum to 1000" }
    }

    val isBuiltUp: Boolean get() = this != GREEN
}

/** Limits on how much may be built on the land of a district. */
data class DensityRules(
    /** Floor-area ratio ceiling in thousandths: 2,400 means 2.4 square metres of floor per square metre of land. */
    val maxFarMilli: Int,
    val maxFloors: Int,
    /** Largest share of the land a building may cover, ppm. */
    val lotCoveragePpm: Long = 600_000L,
) {
    init {
        require(maxFarMilli >= 0 && maxFloors >= 1) { "density limits must be positive" }
        require(lotCoveragePpm in 1..Scale.PPM) { "lot coverage must be a share" }
    }

    /** Most floor area the land can carry under the floor-area ratio. */
    fun maxFloorArea(landSqm: Long): Long = mulDiv(landSqm, maxFarMilli.toLong(), 1000L)

    /** Floor-area ratio actually reached by [floorArea] on [landSqm], in thousandths. */
    fun farOf(floorArea: Long, landSqm: Long): Int = if (landSqm <= 0L) 0 else mulDiv(floorArea, 1000L, landSqm).toInt()

    /** Most floor area the height limit and lot coverage allow on [landSqm]. */
    fun maxFloorAreaByHeight(landSqm: Long): Long = mulDiv(mulDiv(landSqm, lotCoveragePpm, Scale.PPM), maxFloors.toLong(), 1L)

    /** The ratio is also capped by what the height limit and lot coverage allow, whichever is lower. */
    fun effectiveFarMilli(): Int {
        val byHeight = mulDiv(lotCoveragePpm, maxFloors.toLong() * 1000L, Scale.PPM).toInt()
        return minOf(maxFarMilli, byHeight)
    }

    companion object {
        fun defaultsFor(zone: ZoneUse) = DensityRules(zone.defaultFarMilli, zone.defaultFloors)
    }
}

/** Rules about neighbouring uses and about changing a district's zone. */
object Zoning {
    /**
     * How well two uses sit next to each other, permille from -500 (bad neighbours) to +500 (good ones). Symmetric.
     */
    fun compatibility(a: ZoneUse, b: ZoneUse): Int = COMPAT[a.ordinal][b.ordinal]

    private val COMPAT: Array<IntArray> = arrayOf(
        //               RES   COM   IND   MIX   GRN   CIV
        intArrayOf(  100,  120, -450,  150,  300,  200), // RESIDENTIAL
        intArrayOf(  120,  200, -150,  200,  100,  150), // COMMERCIAL
        intArrayOf( -450, -150,  250, -200, -250, -300), // INDUSTRIAL
        intArrayOf(  150,  200, -200,  150,  250,  200), // MIXED
        intArrayOf(  300,  100, -250,  250,  100,  200), // GREEN
        intArrayOf(  200,  150, -300,  200,  200,  100), // CIVIC
    )

    /**
     * How hard a change of use is to push through, permille. Losing green space or putting homes next to a factory
     * is hard; adding mixed use to a residential area is easy.
     */
    fun rezoneDifficulty(from: ZoneUse, to: ZoneUse): Int {
        if (from == to) return 0
        var d = when (to) {
            ZoneUse.MIXED -> 120
            ZoneUse.RESIDENTIAL -> 200
            ZoneUse.COMMERCIAL -> 260
            ZoneUse.CIVIC -> 150
            ZoneUse.INDUSTRIAL -> 450
            ZoneUse.GREEN -> 100
        }
        if (from == ZoneUse.GREEN) d += 350
        if (from == ZoneUse.INDUSTRIAL && to == ZoneUse.RESIDENTIAL) d += 100
        if (from == ZoneUse.CIVIC) d += 200
        return d.coerceIn(0, 1000)
    }

    /** True if the change is possible at all: civic land can only be given up for green space or other civic uses. */
    fun allowed(from: ZoneUse, to: ZoneUse): Boolean = from != to && !(from == ZoneUse.CIVIC && to == ZoneUse.INDUSTRIAL)

    /** Cost of studies, notices and (for former industrial land) soil remediation for a change on [landSqm] of land. */
    fun rezoneCost(landSqm: Long, from: ZoneUse, to: ZoneUse, params: UrbanParams): Money {
        val studies = Money.cents(300L + rezoneDifficulty(from, to).toLong()) * (landSqm / 100L).coerceAtLeast(1L)
        val remediation = if (from == ZoneUse.INDUSTRIAL) Money.units(landSqm * 3L / 2L) else Money.ZERO
        return params.petitionFee + studies + remediation
    }

    /** Share of the district's floor area that the zone devotes to residents, workers in shops, factories or civic uses. */
    fun residentialArea(zone: ZoneUse, floorArea: Long): Long = mulDiv(floorArea, zone.residentialShare.toLong(), 1000L)
    fun commercialArea(zone: ZoneUse, floorArea: Long): Long = mulDiv(floorArea, zone.commercialShare.toLong(), 1000L)
    fun industrialArea(zone: ZoneUse, floorArea: Long): Long = mulDiv(floorArea, zone.industrialShare.toLong(), 1000L)
    fun civicArea(zone: ZoneUse, floorArea: Long): Long = mulDiv(floorArea, zone.civicShare.toLong(), 1000L)

    /** Jobs a district offers for its floor area. */
    fun jobs(zone: ZoneUse, floorArea: Long, params: UrbanParams): Long =
        commercialArea(zone, floorArea) / params.sqmPerCommercialJob +
            industrialArea(zone, floorArea) / params.sqmPerIndustrialJob +
            civicArea(zone, floorArea) / params.sqmPerCivicJob

    /** People the housing of a district holds. */
    fun capacity(zone: ZoneUse, floorArea: Long, params: UrbanParams): Long =
        residentialArea(zone, floorArea) / params.sqmPerResident
}
