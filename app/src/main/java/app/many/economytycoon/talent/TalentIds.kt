package app.many.economytycoon.talent

/** A skill in the taxonomy. Stable string key, defined by content. */
@JvmInline value class SkillId(val key: String) : Comparable<SkillId> {
    override fun compareTo(other: SkillId) = key.compareTo(other.key)
    override fun toString() = "Skill#$key"
}

@JvmInline value class InstitutionId(val value: Long) : Comparable<InstitutionId> {
    override fun compareTo(other: InstitutionId) = value.compareTo(other.value)
    override fun toString() = "Institution#$value"
}

@JvmInline value class ProgrammeId(val value: Long) : Comparable<ProgrammeId> {
    override fun compareTo(other: ProgrammeId) = value.compareTo(other.value)
    override fun toString() = "Programme#$value"
}

/** A named person: an executive or other key hire tracked one by one. */
@JvmInline value class PersonId(val value: Long) : Comparable<PersonId> {
    override fun compareTo(other: PersonId) = value.compareTo(other.value)
    override fun toString() = "Person#$value"
}

@JvmInline value class OfferId(val value: Long) : Comparable<OfferId> {
    override fun compareTo(other: OfferId) = value.compareTo(other.value)
    override fun toString() = "Offer#$value"
}

@JvmInline value class CovenantId(val value: Long) : Comparable<CovenantId> {
    override fun compareTo(other: CovenantId) = value.compareTo(other.value)
    override fun toString() = "Covenant#$value"
}

@JvmInline value class RecruitCampaignId(val value: Long) : Comparable<RecruitCampaignId> {
    override fun compareTo(other: RecruitCampaignId) = value.compareTo(other.value)
    override fun toString() = "RecruitCampaign#$value"
}

@JvmInline value class LadderId(val value: Long) : Comparable<LadderId> {
    override fun compareTo(other: LadderId) = value.compareTo(other.value)
    override fun toString() = "Ladder#$value"
}

@JvmInline value class PositionId(val value: Long) : Comparable<PositionId> {
    override fun compareTo(other: PositionId) = value.compareTo(other.value)
    override fun toString() = "Position#$value"
}

@JvmInline value class ProviderId(val value: Long) : Comparable<ProviderId> {
    override fun compareTo(other: ProviderId) = value.compareTo(other.value)
    override fun toString() = "Provider#$value"
}

@JvmInline value class CourseId(val value: Long) : Comparable<CourseId> {
    override fun compareTo(other: CourseId) = value.compareTo(other.value)
    override fun toString() = "Course#$value"
}

/**
 * Next-id counters for every entity kind in this module. Ids are never reused, and each kind counts on its own so
 * adding a kind never shifts another. The counters are saved with the state.
 */
class TalentCounters {
    private val next = java.util.TreeMap<String, Long>()

    private fun take(kind: String): Long {
        val v = next[kind] ?: 1L
        next[kind] = v + 1
        return v
    }

    fun institution() = InstitutionId(take("institution"))
    fun programme() = ProgrammeId(take("programme"))
    fun person() = PersonId(take("person"))
    fun offer() = OfferId(take("offer"))
    fun covenant() = CovenantId(take("covenant"))
    fun campaign() = RecruitCampaignId(take("campaign"))
    fun ladder() = LadderId(take("ladder"))
    fun position() = PositionId(take("position"))
    fun provider() = ProviderId(take("provider"))
    fun course() = CourseId(take("course"))

    fun peek(kind: String): Long = next[kind] ?: 1L
    fun snapshot(): Map<String, Long> = java.util.TreeMap(next)

    fun restore(counters: Map<String, Long>) {
        next.clear()
        for ((k, v) in counters) {
            require(v >= 1L) { "counter for $k must be positive" }
            next[k] = v
        }
    }

    companion object {
        val KINDS = listOf("institution", "programme", "person", "offer", "covenant", "campaign", "ladder", "position", "provider", "course")
    }
}
