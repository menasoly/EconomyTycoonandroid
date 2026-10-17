package app.many.economytycoon.ip

import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.TechId
import app.many.economytycoon.core.Tick

/** Where one national application stands. */
enum class FilingStatus(val label: String, val live: Boolean) {
    PENDING("Under examination", true),
    GRANTED("In force", true),
    REJECTED("Refused", false),
    ABANDONED("Abandoned", false),
    LAPSED("Lapsed for unpaid fees", false),
    EXPIRED("Expired", false),
    REVOKED("Revoked by a court", false),
}

/** How wide the applicant draws the claims. Wide claims reach more products but are harder to get and easier to attack. */
enum class BreadthChoice(val label: String, val breadthPpm: Long) {
    NARROW("Narrow", 300_000L),
    STANDARD("Standard", 550_000L),
    BROAD("Broad", 800_000L),
}

/** One national application of a patent family: the right exists country by country. */
class Filing(val country: CountryId, val filedAt: Tick, val examinationDue: Tick, breadthPpm: Long) {
    var status: FilingStatus = FilingStatus.PENDING
        internal set
    /** Breadth as granted; an office may narrow what was asked for. */
    var breadthPpm: Long = breadthPpm
        internal set
    var grantedAt: Tick? = null
        internal set
    var expiresAt: Tick? = null
        internal set
    var endedAt: Tick? = null
        internal set
    var feesPaid: Money = Money.ZERO
        internal set
    var feeYearsPaid: Int = 0
        internal set
    var nextFeeDue: Tick? = null
        internal set
    var graceUntil: Tick? = null
        internal set

    val isInForce: Boolean get() = status == FilingStatus.GRANTED

    fun inForceAt(tick: Tick): Boolean {
        if (status != FilingStatus.GRANTED) return false
        val exp = expiresAt ?: return true
        return tick < exp
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(country.value).putLong(filedAt.value).putLong(examinationDue.value).putLong(breadthPpm).putInt(status.ordinal)
        h.putLong(grantedAt?.value ?: -1L).putLong(expiresAt?.value ?: -1L).putLong(endedAt?.value ?: -1L)
        h.putMoney(feesPaid).putInt(feeYearsPaid).putLong(nextFeeDue?.value ?: -1L).putLong(graceUntil?.value ?: -1L)
    }
}

enum class PatentStanding(val label: String) { PENDING("Pending"), ACTIVE("In force"), ENDED("Ended") }

/**
 * An invention a company has asked for protection on. The family is filed in one or more countries; each national
 * [Filing] is examined, granted or refused on its own. The record also carries the facts later rules lean on:
 * novelty and the claim breadth, what the research cost, and whom it cites.
 */
class Patent(
    val id: PatentId,
    val inventor: CompanyId,
    val tech: TechId,
    val title: String,
    val field: TechField,
    val industry: IndustryFamily?,
    val filedAt: Tick,
    val noveltyPpm: Long,
    val claims: Int,
    val claimedBreadthPpm: Long,
    val cites: List<PatentId>,
    val researchCost: Money,
    val pharma: Boolean,
    owner: IpOwner,
) {
    var owner: IpOwner = owner
        internal set
    var standardEssential: Boolean = false
        internal set
    var frandPledged: Boolean = false
        internal set
    var autoMaintain: Boolean = true
        internal set
    var transfers: Int = 0
        internal set
    var askingPrice: Money? = null
        internal set
    var listedAt: Tick? = null
        internal set
    var totalFees: Money = Money.ZERO
        internal set
    internal val filingMap = java.util.TreeMap<CountryId, Filing>()

    val filings: List<Filing> get() = filingMap.values.toList()
    fun filing(country: CountryId): Filing? = filingMap[country]
    fun inForce(country: CountryId, tick: Tick): Boolean = filingMap[country]?.inForceAt(tick) == true
    fun countriesInForce(tick: Tick): List<CountryId> = filingMap.values.filter { it.inForceAt(tick) }.map { it.country }
    val isLive: Boolean get() = filingMap.values.any { it.status.live }
    val isPending: Boolean get() = filingMap.values.any { it.status == FilingStatus.PENDING }
    val isListed: Boolean get() = askingPrice != null

    fun standing(): PatentStanding = when {
        filingMap.values.any { it.status == FilingStatus.GRANTED } -> PatentStanding.ACTIVE
        filingMap.values.any { it.status == FilingStatus.PENDING } -> PatentStanding.PENDING
        else -> PatentStanding.ENDED
    }

    /** The widest breadth granted anywhere, ppm; zero when nothing is in force. */
    fun grantedBreadthPpm(): Long = filingMap.values.filter { it.status == FilingStatus.GRANTED }.maxOfOrNull { it.breadthPpm } ?: 0L

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(inventor.value).putString(tech.key).putString(title).putInt(field.ordinal).putInt(industry?.ordinal ?: -1)
        h.putLong(filedAt.value).putLong(noveltyPpm).putInt(claims).putLong(claimedBreadthPpm)
        h.putInt(cites.size); for (c in cites) h.putLong(c.value)
        h.putMoney(researchCost).putBoolean(pharma).putInt(owner.kind.ordinal).putLong(owner.id)
        h.putBoolean(standardEssential).putBoolean(frandPledged).putBoolean(autoMaintain).putInt(transfers)
        h.putLong(askingPrice?.raw ?: -1L).putLong(listedAt?.value ?: -1L).putMoney(totalFees)
        h.putInt(filingMap.size); for (f in filingMap.values) f.hashInto(h)
    }
}
