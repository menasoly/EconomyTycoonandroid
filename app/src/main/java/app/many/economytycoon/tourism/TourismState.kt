package app.many.economytycoon.tourism

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Hashable
import app.many.economytycoon.core.StateHash

/**
 * Everything the tourism module saves: destinations and origin markets, properties, attractions, operators and their
 * allotments, intermediaries, shocks and the id counters. Only [TourismService] changes it; everything else reads.
 */
class TourismState : Hashable {
    internal val destinationMap = java.util.TreeMap<DestinationId, Destination>()
    internal val originMap = java.util.TreeMap<OriginId, OriginMarket>()
    internal val propertyMap = java.util.TreeMap<PropertyId, Property>()
    internal val attractionMap = java.util.TreeMap<AttractionId, Attraction>()
    internal val operatorMap = java.util.TreeMap<OperatorId, TourOperator>()
    internal val allotmentMap = java.util.TreeMap<AllotmentId, Allotment>()
    internal val intermediaryMap = java.util.TreeMap<IntermediaryId, Intermediary>()
    internal val shockMap = java.util.TreeMap<ShockId, Shock>()
    val counters = TourismCounters()

    /** Last day advanced, or -1 before the first. */
    var lastTick: Long = -1L
        internal set

    /** Month index (tick / 30) last settled, or -1. */
    var lastSettledMonth: Long = -1L
        internal set

    val destinations: List<Destination> get() = destinationMap.values.toList()
    val origins: List<OriginMarket> get() = originMap.values.toList()
    val properties: List<Property> get() = propertyMap.values.toList()
    val attractions: List<Attraction> get() = attractionMap.values.toList()
    val operators: List<TourOperator> get() = operatorMap.values.toList()
    val allotments: List<Allotment> get() = allotmentMap.values.toList()
    val intermediaries: List<Intermediary> get() = intermediaryMap.values.toList()
    val shocks: List<Shock> get() = shockMap.values.toList()

    fun destination(id: DestinationId): Destination? = destinationMap[id]
    fun origin(id: OriginId): OriginMarket? = originMap[id]
    fun property(id: PropertyId): Property? = propertyMap[id]
    fun attraction(id: AttractionId): Attraction? = attractionMap[id]
    fun operator(id: OperatorId): TourOperator? = operatorMap[id]
    fun allotment(id: AllotmentId): Allotment? = allotmentMap[id]
    fun intermediary(id: IntermediaryId): Intermediary? = intermediaryMap[id]
    fun shock(id: ShockId): Shock? = shockMap[id]

    fun destinationOfCity(city: app.many.economytycoon.core.CityId): Destination? = destinationMap.values.firstOrNull { it.city == city }

    fun propertiesIn(d: DestinationId): List<Property> = propertyMap.values.filter { it.destination == d }
    fun openPropertiesIn(d: DestinationId): List<Property> = propertyMap.values.filter { it.destination == d && it.isOpen }
    fun attractionsIn(d: DestinationId): List<Attraction> = attractionMap.values.filter { it.destination == d }
    fun propertiesOf(company: CompanyId): List<Property> = propertyMap.values.filter { it.owner == company }
    fun attractionsOf(company: CompanyId): List<Attraction> = attractionMap.values.filter { it.owner == company }
    fun operatorsOf(company: CompanyId): List<TourOperator> = operatorMap.values.filter { it.owner == company }
    fun intermediariesOf(company: CompanyId): List<Intermediary> = intermediaryMap.values.filter { it.owner == company }
    fun allotmentsOfProperty(p: PropertyId): List<Allotment> = allotmentMap.values.filter { it.property == p }
    fun allotmentsOfOperator(o: OperatorId): List<Allotment> = allotmentMap.values.filter { it.operator == o }
    fun liveAllotmentsOfProperty(p: PropertyId): List<Allotment> = allotmentMap.values.filter { it.property == p && it.status == AllotmentStatus.ACTIVE }
    fun intermediariesOfChannel(channel: Channel): List<Intermediary> = intermediaryMap.values.filter { it.channel == channel }
    fun activeShocks(tick: Long): List<Shock> = shockMap.values.filter { it.activeAt(app.many.economytycoon.core.Tick(tick)) }

    val isEmpty: Boolean get() = destinationMap.isEmpty()

    internal fun clear() {
        destinationMap.clear(); originMap.clear(); propertyMap.clear(); attractionMap.clear()
        operatorMap.clear(); allotmentMap.clear(); intermediaryMap.clear(); shockMap.clear()
        counters.reset()
        lastTick = -1L; lastSettledMonth = -1L
    }

    override fun hashInto(hasher: CanonicalHasher) {
        hasher.section("tourism")
        hasher.putLong(lastTick).putLong(lastSettledMonth)
        val c = counters
        hasher.putLong(c.nextDestination).putLong(c.nextOrigin).putLong(c.nextProperty).putLong(c.nextAttraction)
        hasher.putLong(c.nextOperator).putLong(c.nextAllotment).putLong(c.nextIntermediary).putLong(c.nextShock)
        hasher.putInt(destinationMap.size); for (d in destinationMap.values) d.hashInto(hasher)
        hasher.putInt(originMap.size); for (o in originMap.values) o.hashInto(hasher)
        hasher.putInt(propertyMap.size); for (p in propertyMap.values) p.hashInto(hasher)
        hasher.putInt(attractionMap.size); for (a in attractionMap.values) a.hashInto(hasher)
        hasher.putInt(operatorMap.size); for (o in operatorMap.values) o.hashInto(hasher)
        hasher.putInt(allotmentMap.size); for (a in allotmentMap.values) a.hashInto(hasher)
        hasher.putInt(intermediaryMap.size); for (i in intermediaryMap.values) i.hashInto(hasher)
        hasher.putInt(shockMap.size); for (s in shockMap.values) s.hashInto(hasher)
    }

    /** The canonical hash of this section on its own. */
    fun stateHash(): StateHash = CanonicalHasher().also { hashInto(it) }.finish()
}
