package app.many.economytycoon.tourism

/** Identifier of a tourist destination (one per city that draws visitors). */
@JvmInline value class DestinationId(val value: Long) : Comparable<DestinationId> {
    override fun compareTo(other: DestinationId) = value.compareTo(other.value)
    override fun toString() = "Destination#$value"
}

/** Identifier of an origin market, a place visitors travel from. */
@JvmInline value class OriginId(val value: Long) : Comparable<OriginId> {
    override fun compareTo(other: OriginId) = value.compareTo(other.value)
    override fun toString() = "Origin#$value"
}

/** Identifier of an accommodation property (hostel, hotel, resort, boutique). */
@JvmInline value class PropertyId(val value: Long) : Comparable<PropertyId> {
    override fun compareTo(other: PropertyId) = value.compareTo(other.value)
    override fun toString() = "Property#$value"
}

/** Identifier of a ticketed attraction or venue. */
@JvmInline value class AttractionId(val value: Long) : Comparable<AttractionId> {
    override fun compareTo(other: AttractionId) = value.compareTo(other.value)
    override fun toString() = "Attraction#$value"
}

/** Identifier of a tour operator. */
@JvmInline value class OperatorId(val value: Long) : Comparable<OperatorId> {
    override fun compareTo(other: OperatorId) = value.compareTo(other.value)
    override fun toString() = "Operator#$value"
}

/** Identifier of an allotment contract between a tour operator and a property. */
@JvmInline value class AllotmentId(val value: Long) : Comparable<AllotmentId> {
    override fun compareTo(other: AllotmentId) = value.compareTo(other.value)
    override fun toString() = "Allotment#$value"
}

/** Identifier of a travel agency or online booking platform. */
@JvmInline value class IntermediaryId(val value: Long) : Comparable<IntermediaryId> {
    override fun compareTo(other: IntermediaryId) = value.compareTo(other.value)
    override fun toString() = "Intermediary#$value"
}

/** Identifier of a shock (disease, strike, festival, ...). */
@JvmInline value class ShockId(val value: Long) : Comparable<ShockId> {
    override fun compareTo(other: ShockId) = value.compareTo(other.value)
    override fun toString() = "Shock#$value"
}

/**
 * Next-id counters of the tourism module. They live in the saved state so ids are never reused after a load and
 * adding an entity kind never shifts the ids of another.
 */
class TourismCounters(
    var nextDestination: Long = 1L,
    var nextOrigin: Long = 1L,
    var nextProperty: Long = 1L,
    var nextAttraction: Long = 1L,
    var nextOperator: Long = 1L,
    var nextAllotment: Long = 1L,
    var nextIntermediary: Long = 1L,
    var nextShock: Long = 1L,
) {
    fun destination() = DestinationId(nextDestination++)
    fun origin() = OriginId(nextOrigin++)
    fun property() = PropertyId(nextProperty++)
    fun attraction() = AttractionId(nextAttraction++)
    fun operator() = OperatorId(nextOperator++)
    fun allotment() = AllotmentId(nextAllotment++)
    fun intermediary() = IntermediaryId(nextIntermediary++)
    fun shock() = ShockId(nextShock++)

    fun reset() {
        nextDestination = 1L; nextOrigin = 1L; nextProperty = 1L; nextAttraction = 1L
        nextOperator = 1L; nextAllotment = 1L; nextIntermediary = 1L; nextShock = 1L
    }
}
