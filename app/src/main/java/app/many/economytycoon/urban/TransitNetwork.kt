package app.many.economytycoon.urban

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.mulDiv

enum class RoadClass(val label: String, val capacityPerLane: Long, val speedMetersPerSec: Long) {
    LOCAL("Local street", 700L, 8L),
    ARTERIAL("Arterial", 1_000L, 11L),
    HIGHWAY("Highway", 1_900L, 24L),
}

enum class TransitMode(
    val label: String,
    val vehicleCapacity: Long,
    /** Operating cost of one vehicle-kilometre. */
    val costPerVehicleKm: Money,
    /** Cost to build one kilometre of line. */
    val buildCostPerKm: Money,
    val dwellSeconds: Long,
) {
    BUS("Bus", 70L, Money.cents(220), Money.units(450_000), 25L),
    METRO("Metro", 900L, Money.units(12), Money.units(95_000_000), 35L),
    RAIL("Rail", 1_200L, Money.units(10), Money.units(38_000_000), 50L),
}

enum class LineStatus { PLANNED, UNDER_CONSTRUCTION, ACTIVE, SUSPENDED }

/** A street link between two neighbouring districts. */
class RoadLink(
    val id: LinkId,
    val a: Int,
    val b: Int,
    val lengthMeters: Long,
    roadClass: RoadClass,
    lanes: Int,
) {
    var roadClass: RoadClass = roadClass
        internal set
    var lanes: Int = lanes
        internal set
    /** Toll charged per vehicle; set by a concession. */
    var toll: Money = Money.ZERO
        internal set
    /** Vehicles an hour in the busiest hour, from the last traffic assignment. */
    var flowVph: Long = 0L
        internal set

    val capacityVph: Long get() = lanes * roadClass.capacityPerLane
    val freeFlowSeconds: Long get() = (lengthMeters / roadClass.speedMetersPerSec).coerceAtLeast(1L)
    val volumeCapacityPpm: Long get() = Congestion.volumeCapacityPpm(flowVph, capacityVph)
    val levelOfService: LevelOfService get() = LevelOfService.of(volumeCapacityPpm)

    fun other(from: Int): Int = if (from == a) b else a
    fun connects(x: Int, y: Int): Boolean = (a == x && b == y) || (a == y && b == x)

    /** Seconds to drive the link at the current flow. */
    fun currentSeconds(): Long = Congestion.travelSeconds(freeFlowSeconds, flowVph, capacityVph)

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putInt(a).putInt(b).putLong(lengthMeters).putInt(roadClass.ordinal).putInt(lanes).putMoney(toll).putLong(flowVph)
    }
}

/** A bus, metro or rail line serving a sequence of districts. */
class TransitLine(
    val id: TransitLineId,
    val name: String,
    val mode: TransitMode,
    val stops: List<Int>,
    headwaySeconds: Int,
    fare: Money,
    /** Buses only: running in their own lane, so they do not sit in traffic. */
    val dedicatedLane: Boolean,
    val lengthMeters: Long,
) {
    init { require(stops.size >= 2) { "a line needs at least two stops" } }

    var headwaySeconds: Int = headwaySeconds
        internal set
    var fare: Money = fare
        internal set
    var status: LineStatus = LineStatus.PLANNED
        internal set
    /** Passengers an hour on the busiest segment, from the last assignment. */
    var peakLoad: Long = 0L
        internal set
    var monthlyRiders: Long = 0L
        internal set
    var lifetimeRiders: Long = 0L
        internal set

    val isActive: Boolean get() = status == LineStatus.ACTIVE
    val vehiclesPerHour: Long get() = 3600L / headwaySeconds
    val capacityPerHour: Long get() = vehiclesPerHour * mode.vehicleCapacity
    val loadRatioPpm: Long get() = Congestion.volumeCapacityPpm(peakLoad, capacityPerHour)
    val lengthKm10: Long get() = lengthMeters / 100L

    /** Cost to run the line for a month: 18 service hours a day in both directions. */
    fun monthlyOperatingCost(): Money {
        val vehicleKm = lengthMeters * vehiclesPerHour * 18L * Scale.MONTH_DAYS * 2L / 1000L
        return mode.costPerVehicleKm * vehicleKm
    }

    /** Construction cost of the line. */
    fun buildCost(): Money = Money(mulDiv(mode.buildCostPerKm.raw, lengthMeters, 1000L))

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putString(name).putInt(mode.ordinal).putInt(stops.size)
        for (s in stops) h.putInt(s)
        h.putInt(headwaySeconds).putMoney(fare).putBoolean(dedicatedLane).putLong(lengthMeters).putInt(status.ordinal)
        h.putLong(peakLoad).putLong(monthlyRiders).putLong(lifetimeRiders)
    }
}

/**
 * Roads and public transport lines of one city. Nodes are the districts. The network validates its own changes and keeps
 * links and lines in id order.
 */
class TransitNetwork(val geometry: CityGeometry) {
    private val roadMap = java.util.TreeMap<LinkId, RoadLink>()
    private val lineMap = java.util.TreeMap<TransitLineId, TransitLine>()

    fun roads(): List<RoadLink> = roadMap.values.toList()
    fun lines(): List<TransitLine> = lineMap.values.toList()
    fun road(id: LinkId): RoadLink? = roadMap[id]
    fun line(id: TransitLineId): TransitLine? = lineMap[id]
    fun activeLines(): List<TransitLine> = lineMap.values.filter { it.isActive }
    fun roadsAt(district: Int): List<RoadLink> = roadMap.values.filter { it.a == district || it.b == district }
    fun roadBetween(a: Int, b: Int): RoadLink? = roadMap.values.firstOrNull { it.connects(a, b) }
    fun linesServing(district: Int): List<TransitLine> = lineMap.values.filter { it.isActive && district in it.stops }

    /** True when every district can be reached from district 0 over roads. */
    fun roadsConnected(): Boolean {
        val n = geometry.districtCount
        val seen = BooleanArray(n)
        val stack = ArrayDeque<Int>()
        stack.add(0); seen[0] = true
        while (stack.isNotEmpty()) {
            val d = stack.removeLast()
            for (r in roadsAt(d)) { val o = r.other(d); if (!seen[o]) { seen[o] = true; stack.add(o) } }
        }
        return seen.all { it }
    }

    fun addRoad(id: LinkId, a: Int, b: Int, roadClass: RoadClass, lanes: Int): Outcome<RoadLink> {
        val n = geometry.districtCount
        if (a !in 0 until n || b !in 0 until n) return Outcome.reject(Rules.UNKNOWN_ENTITY, "no such district")
        if (a == b) return Outcome.reject(Rules.INVALID_ARGUMENT, "a road must join two districts")
        if (lanes !in 1..8) return Outcome.reject(Rules.INVALID_ARGUMENT, "a road has between 1 and 8 lanes")
        if (roadBetween(a, b) != null) return Outcome.reject(Rules.DUPLICATE, "those districts are already joined by a road")
        val link = RoadLink(id, minOf(a, b), maxOf(a, b), geometry.distanceMeters(a, b), roadClass, lanes)
        roadMap[id] = link
        return Outcome.ok(link)
    }

    fun upgradeRoad(id: LinkId, roadClass: RoadClass, lanes: Int): Outcome<RoadLink> {
        val r = roadMap[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no such road")
        if (lanes !in 1..8) return Outcome.reject(Rules.INVALID_ARGUMENT, "a road has between 1 and 8 lanes")
        if (roadClass.ordinal < r.roadClass.ordinal || lanes < r.lanes) return Outcome.reject(Rules.INVALID_ARGUMENT, "upgrades cannot shrink a road")
        r.roadClass = roadClass
        r.lanes = lanes
        return Outcome.ok(r)
    }

    fun setToll(id: LinkId, toll: Money): Outcome<RoadLink> {
        val r = roadMap[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no such road")
        if (toll.isNegative) return Outcome.reject(Rules.INVALID_ARGUMENT, "a toll cannot be negative")
        r.toll = toll
        return Outcome.ok(r)
    }

    /** Plans a line. Metro and rail need their own right of way, so they always get a dedicated lane. */
    fun planLine(
        id: TransitLineId, name: String, mode: TransitMode, stops: List<Int>, headwaySeconds: Int, fare: Money, dedicatedLane: Boolean,
    ): Outcome<TransitLine> {
        val n = geometry.districtCount
        if (name.isBlank()) return Outcome.reject(Rules.INVALID_ARGUMENT, "a line needs a name")
        if (stops.size < 2) return Outcome.reject(Rules.INVALID_ARGUMENT, "a line needs at least two stops")
        if (stops.any { it !in 0 until n }) return Outcome.reject(Rules.UNKNOWN_ENTITY, "a stop is not a district")
        if (stops.zipWithNext().any { (x, y) -> x == y }) return Outcome.reject(Rules.INVALID_ARGUMENT, "consecutive stops must differ")
        if (stops.toSet().size != stops.size) return Outcome.reject(Rules.INVALID_ARGUMENT, "a line may not visit a district twice")
        if (headwaySeconds !in MIN_HEADWAY..MAX_HEADWAY) return Outcome.reject(Rules.INVALID_ARGUMENT, "headway must be between $MIN_HEADWAY and $MAX_HEADWAY seconds")
        if (fare.isNegative) return Outcome.reject(Rules.INVALID_ARGUMENT, "a fare cannot be negative")
        if (lineMap.values.any { it.name == name }) return Outcome.reject(Rules.NAME_TAKEN, "a line with that name exists")
        var length = 0L
        for ((x, y) in stops.zipWithNext()) length += geometry.distanceMeters(x, y)
        val line = TransitLine(id, name, mode, stops.toList(), headwaySeconds, fare, dedicatedLane || mode != TransitMode.BUS, length)
        lineMap[id] = line
        return Outcome.ok(line)
    }

    fun setStatus(id: TransitLineId, status: LineStatus): Outcome<TransitLine> {
        val l = lineMap[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no such line")
        if (l.status == LineStatus.PLANNED && status == LineStatus.ACTIVE) return Outcome.reject(Rules.WRONG_STATE, "a planned line must be built first")
        l.status = status
        return Outcome.ok(l)
    }

    fun setHeadway(id: TransitLineId, headwaySeconds: Int): Outcome<TransitLine> {
        val l = lineMap[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no such line")
        if (headwaySeconds !in MIN_HEADWAY..MAX_HEADWAY) return Outcome.reject(Rules.INVALID_ARGUMENT, "headway must be between $MIN_HEADWAY and $MAX_HEADWAY seconds")
        l.headwaySeconds = headwaySeconds
        return Outcome.ok(l)
    }

    fun setFare(id: TransitLineId, fare: Money): Outcome<TransitLine> {
        val l = lineMap[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "no such line")
        if (fare.isNegative) return Outcome.reject(Rules.INVALID_ARGUMENT, "a fare cannot be negative")
        l.fare = fare
        return Outcome.ok(l)
    }

    /** Monthly cost of all active lines. */
    fun monthlyOperatingCost(): Money = Money.sum(activeLines().map { it.monthlyOperatingCost() })

    internal fun restoreRoad(r: RoadLink) { roadMap[r.id] = r }
    internal fun restoreLine(l: TransitLine) { lineMap[l.id] = l }
    internal fun clear() { roadMap.clear(); lineMap.clear() }

    fun hashInto(h: CanonicalHasher) {
        h.putInt(roadMap.size)
        for (r in roadMap.values) r.hashInto(h)
        h.putInt(lineMap.size)
        for (l in lineMap.values) l.hashInto(h)
    }

    companion object {
        const val MIN_HEADWAY = 60
        const val MAX_HEADWAY = 3_600
    }
}
