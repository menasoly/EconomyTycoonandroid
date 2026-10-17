package app.many.economytycoon.urban

import app.many.economytycoon.core.IdAllocator

/** A district of a city: a block of grid cells that is zoned, built and governed as one unit. */
@JvmInline value class DistrictId(val value: Long) : Comparable<DistrictId> {
    override fun compareTo(other: DistrictId) = value.compareTo(other.value)
    override fun toString() = "District#$value"
}

/** A rezoning or variance petition before the city council. */
@JvmInline value class PetitionId(val value: Long) : Comparable<PetitionId> {
    override fun compareTo(other: PetitionId) = value.compareTo(other.value)
    override fun toString() = "Petition#$value"
}

/** A municipal capital project such as a metro line, a school or a park. */
@JvmInline value class CapitalProjectId(val value: Long) : Comparable<CapitalProjectId> {
    override fun compareTo(other: CapitalProjectId) = value.compareTo(other.value)
    override fun toString() = "CapitalProject#$value"
}

/** A public-private concession: a company builds or runs something for the city under agreed payments. */
@JvmInline value class ConcessionId(val value: Long) : Comparable<ConcessionId> {
    override fun compareTo(other: ConcessionId) = value.compareTo(other.value)
    override fun toString() = "Concession#$value"
}

/** A public transport line (bus, metro or rail). */
@JvmInline value class TransitLineId(val value: Long) : Comparable<TransitLineId> {
    override fun compareTo(other: TransitLineId) = value.compareTo(other.value)
    override fun toString() = "TransitLine#$value"
}

/** A road or rail link between two districts. */
@JvmInline value class LinkId(val value: Long) : Comparable<LinkId> {
    override fun compareTo(other: LinkId) = value.compareTo(other.value)
    override fun toString() = "Link#$value"
}

/** An urban event such as a boom, a flood or a protest wave. */
@JvmInline value class UrbanEventId(val value: Long) : Comparable<UrbanEventId> {
    override fun compareTo(other: UrbanEventId) = value.compareTo(other.value)
    override fun toString() = "UrbanEvent#$value"
}

/**
 * Allocators for the urban entity kinds. Each kind has its own counter so adding a kind never shifts the ids of another,
 * and the counters are saved with the state.
 */
class UrbanIds {
    private val districts = IdAllocator()
    private val petitions = IdAllocator()
    private val projects = IdAllocator()
    private val concessions = IdAllocator()
    private val lines = IdAllocator()
    private val links = IdAllocator()
    private val events = IdAllocator()

    fun district() = DistrictId(districts.allocate())
    fun petition() = PetitionId(petitions.allocate())
    fun project() = CapitalProjectId(projects.allocate())
    fun concession() = ConcessionId(concessions.allocate())
    fun line() = TransitLineId(lines.allocate())
    fun link() = LinkId(links.allocate())
    fun event() = UrbanEventId(events.allocate())

    /** Next values in a fixed order: districts, petitions, projects, concessions, lines, links, events. */
    fun snapshot(): LongArray = longArrayOf(
        districts.peek(), petitions.peek(), projects.peek(), concessions.peek(), lines.peek(), links.peek(), events.peek(),
    )

    fun restore(values: LongArray) {
        require(values.size == 7) { "expected 7 id counters" }
        districts.restore(values[0]); petitions.restore(values[1]); projects.restore(values[2])
        concessions.restore(values[3]); lines.restore(values[4]); links.restore(values[5]); events.restore(values[6])
    }
}
