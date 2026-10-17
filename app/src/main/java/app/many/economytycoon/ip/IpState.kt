package app.many.economytycoon.ip

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Hashable
import app.many.economytycoon.core.StateHash
import app.many.economytycoon.core.TechId

/**
 * Everything the module remembers: patents, secrets, licences, pools, disputes, scripted entities and the standing
 * instructions of each company. The services in this package are the only writers; everything else reads through
 * the accessors. [worldSeed] feeds the stateless dice so that outcomes can be reproduced from the save alone.
 */
class IpState(val worldSeed: Long) : Hashable {
    val ids = IpIds()
    val regimes = RegimeBook()
    val dice = IpDice(worldSeed)

    internal val patentMap = java.util.TreeMap<PatentId, Patent>()
    internal val secretMap = java.util.TreeMap<SecretId, TradeSecret>()
    internal val licenseMap = java.util.TreeMap<LicenseId, License>()
    internal val poolMap = java.util.TreeMap<PoolId, PatentPool>()
    internal val disputeMap = java.util.TreeMap<DisputeId, Dispute>()
    internal val npeMap = java.util.TreeMap<NpeId, Npe>()
    internal val designAroundMap = java.util.TreeMap<DesignAroundId, DesignAround>()
    internal val compulsoryMap = java.util.TreeMap<CompulsoryId, CompulsoryRequest>()
    internal val clearanceSet = java.util.TreeSet<Clearance>()
    internal val policyMap = java.util.TreeMap<CompanyId, IpPolicy>()
    /** The last year, counted from tick zero, for which a company's research credit was paid. */
    internal val creditYearMap = java.util.TreeMap<CompanyId, Long>()
    /** Forward citations per patent; derived from the cites lists, rebuilt after a load and never saved. */
    internal val forwardCites = java.util.TreeMap<PatentId, Int>()

    fun patents(): List<Patent> = patentMap.values.toList()
    fun patent(id: PatentId): Patent? = patentMap[id]
    fun secrets(): List<TradeSecret> = secretMap.values.toList()
    fun secret(id: SecretId): TradeSecret? = secretMap[id]
    fun licenses(): List<License> = licenseMap.values.toList()
    fun license(id: LicenseId): License? = licenseMap[id]
    fun pools(): List<PatentPool> = poolMap.values.toList()
    fun pool(id: PoolId): PatentPool? = poolMap[id]
    fun disputes(): List<Dispute> = disputeMap.values.toList()
    fun dispute(id: DisputeId): Dispute? = disputeMap[id]
    fun npes(): List<Npe> = npeMap.values.toList()
    fun npe(id: NpeId): Npe? = npeMap[id]
    fun designArounds(): List<DesignAround> = designAroundMap.values.toList()
    fun designAround(id: DesignAroundId): DesignAround? = designAroundMap[id]
    fun compulsoryRequests(): List<CompulsoryRequest> = compulsoryMap.values.toList()
    fun clearances(): List<Clearance> = clearanceSet.toList()
    fun hasClearance(company: CompanyId, patent: PatentId): Boolean = clearanceSet.any { it.company == company && it.patent == patent }
    fun policy(company: CompanyId): IpPolicy = policyMap[company] ?: IpPolicy()
    fun policies(): List<Pair<CompanyId, IpPolicy>> = policyMap.entries.map { it.key to it.value }
    fun forwardCitations(id: PatentId): Int = forwardCites[id] ?: 0

    fun patentsOf(owner: IpOwner): List<Patent> = patentMap.values.filter { it.owner == owner }
    fun patentsOf(company: CompanyId): List<Patent> = patentsOf(IpOwner.company(company))
    fun patentOfTech(inventor: CompanyId, tech: TechId): Patent? = patentMap.values.firstOrNull { it.inventor == inventor && it.tech == tech }
    fun secretsOf(company: CompanyId): List<TradeSecret> = secretMap.values.filter { it.owner == company }
    fun liveSecretFor(company: CompanyId, tech: TechId): TradeSecret? = secretMap.values.firstOrNull { it.owner == company && it.tech == tech && it.isLive }
    fun licensesOf(company: CompanyId): List<License> =
        licenseMap.values.filter { it.licensee == company || it.licensor == IpOwner.company(company) }
    fun licensesIn(company: CompanyId): List<License> = licenseMap.values.filter { it.licensee == company && it.isActive }
    fun licensesOut(owner: IpOwner): List<License> = licenseMap.values.filter { it.licensor == owner && it.isActive }
    fun disputesOf(owner: IpOwner): List<Dispute> = disputeMap.values.filter { it.involves(owner) }
    fun openDisputes(): List<Dispute> = disputeMap.values.filter { it.isOpen }

    internal fun rebuildIndexes() {
        forwardCites.clear()
        for (p in patentMap.values) for (c in p.cites) forwardCites[c] = (forwardCites[c] ?: 0) + 1
    }

    internal fun clearAll() {
        patentMap.clear(); secretMap.clear(); licenseMap.clear(); poolMap.clear(); disputeMap.clear(); npeMap.clear()
        designAroundMap.clear(); compulsoryMap.clear(); clearanceSet.clear(); policyMap.clear(); creditYearMap.clear(); forwardCites.clear()
        regimes.clearAll()
    }

    override fun hashInto(hasher: CanonicalHasher) {
        hasher.section("ip")
        hasher.putLong(worldSeed).putString(regimes.default.name).putInt(regimes.assigned().size)
        for ((c, k) in regimes.assigned()) hasher.putLong(c.value).putString(k.name)
        for ((kind, next) in ids.snapshot()) hasher.putString(kind).putLong(next)
        hasher.putInt(patentMap.size); for (p in patentMap.values) p.hashInto(hasher)
        hasher.putInt(secretMap.size); for (s in secretMap.values) s.hashInto(hasher)
        hasher.putInt(licenseMap.size); for (l in licenseMap.values) l.hashInto(hasher)
        hasher.putInt(poolMap.size); for (p in poolMap.values) p.hashInto(hasher)
        hasher.putInt(disputeMap.size); for (d in disputeMap.values) d.hashInto(hasher)
        hasher.putInt(npeMap.size); for (n in npeMap.values) n.hashInto(hasher)
        hasher.putInt(designAroundMap.size); for (d in designAroundMap.values) d.hashInto(hasher)
        hasher.putInt(compulsoryMap.size); for (c in compulsoryMap.values) c.hashInto(hasher)
        hasher.putInt(clearanceSet.size); for (c in clearanceSet) hasher.putLong(c.company.value).putLong(c.patent.value).putLong(c.since.value)
        hasher.putInt(policyMap.size); for ((c, p) in policyMap) { hasher.putLong(c.value); p.hashInto(hasher) }
        hasher.putInt(creditYearMap.size); for ((c, y) in creditYearMap) hasher.putLong(c.value).putLong(y)
    }

    /** The module's canonical hash on its own, for tests and replay checks. */
    fun stateHash(): StateHash = CanonicalHasher().also { hashInto(it) }.finish()
}
