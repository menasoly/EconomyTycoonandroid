package app.many.economytycoon.ip

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.IdAllocator

@JvmInline value class PatentId(val value: Long) : Comparable<PatentId> {
    override fun compareTo(other: PatentId) = value.compareTo(other.value)
    override fun toString() = "patent:$value"
}

@JvmInline value class LicenseId(val value: Long) : Comparable<LicenseId> {
    override fun compareTo(other: LicenseId) = value.compareTo(other.value)
    override fun toString() = "licence:$value"
}

@JvmInline value class DisputeId(val value: Long) : Comparable<DisputeId> {
    override fun compareTo(other: DisputeId) = value.compareTo(other.value)
    override fun toString() = "dispute:$value"
}

@JvmInline value class PoolId(val value: Long) : Comparable<PoolId> {
    override fun compareTo(other: PoolId) = value.compareTo(other.value)
    override fun toString() = "pool:$value"
}

@JvmInline value class SecretId(val value: Long) : Comparable<SecretId> {
    override fun compareTo(other: SecretId) = value.compareTo(other.value)
    override fun toString() = "secret:$value"
}

@JvmInline value class NpeId(val value: Long) : Comparable<NpeId> {
    override fun compareTo(other: NpeId) = value.compareTo(other.value)
    override fun toString() = "npe:$value"
}

@JvmInline value class DesignAroundId(val value: Long) : Comparable<DesignAroundId> {
    override fun compareTo(other: DesignAroundId) = value.compareTo(other.value)
    override fun toString() = "design-around:$value"
}

@JvmInline value class CompulsoryId(val value: Long) : Comparable<CompulsoryId> {
    override fun compareTo(other: CompulsoryId) = value.compareTo(other.value)
    override fun toString() = "compulsory:$value"
}

enum class IpOwnerKind { COMPANY, NPE }

/** Whoever can hold a patent: an ordinary company or a non-practising entity. Ordered for deterministic iteration. */
data class IpOwner(val kind: IpOwnerKind, val id: Long) : Comparable<IpOwner> {
    override fun compareTo(other: IpOwner): Int {
        val k = kind.compareTo(other.kind)
        return if (k != 0) k else id.compareTo(other.id)
    }

    val isCompany: Boolean get() = kind == IpOwnerKind.COMPANY
    val isNpe: Boolean get() = kind == IpOwnerKind.NPE
    fun companyOrNull(): CompanyId? = if (isCompany) CompanyId(id) else null
    fun npeOrNull(): NpeId? = if (isNpe) NpeId(id) else null

    override fun toString() = "${kind.name.lowercase()}:$id"

    companion object {
        fun company(id: CompanyId) = IpOwner(IpOwnerKind.COMPANY, id.value)
        fun npe(id: NpeId) = IpOwner(IpOwnerKind.NPE, id.value)
    }
}

/**
 * Id counters for the module. The shared allocator set belongs to core, so this module keeps its own counters,
 * one per entity kind, and saves them with its section.
 */
class IpIds {
    private val byKind = java.util.TreeMap<String, IdAllocator>()

    private fun of(kind: String) = byKind.getOrPut(kind) { IdAllocator() }

    fun patent() = PatentId(of("patent").allocate())
    fun license() = LicenseId(of("license").allocate())
    fun dispute() = DisputeId(of("dispute").allocate())
    fun pool() = PoolId(of("pool").allocate())
    fun secret() = SecretId(of("secret").allocate())
    fun npe() = NpeId(of("npe").allocate())
    fun designAround() = DesignAroundId(of("design_around").allocate())
    fun compulsory() = CompulsoryId(of("compulsory").allocate())
    /** Sequence number that keeps settlement keys unique. */
    fun charge(): Long = of("charge").allocate()

    fun snapshot(): Map<String, Long> = byKind.mapValues { it.value.peek() }

    fun restore(counters: Map<String, Long>) {
        byKind.clear()
        for ((kind, next) in counters.toSortedMap()) byKind[kind] = IdAllocator().also { it.restore(next) }
    }
}
