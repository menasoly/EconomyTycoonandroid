package app.many.economytycoon.ip

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick

enum class DesignAroundStatus(val label: String, val live: Boolean) {
    RUNNING("Engineering", true),
    SUCCEEDED("Worked", false),
    FAILED("Did not work", false),
    CANCELLED("Stopped", false),
}

/** An effort to change a product so it no longer falls inside a patent's claims. */
class DesignAround(
    val id: DesignAroundId,
    val company: CompanyId,
    val patent: PatentId,
    val startedAt: Tick,
    val completesAt: Tick,
    val cost: Money,
    val successPpm: Long,
    val dispute: DisputeId?,
) {
    var status: DesignAroundStatus = DesignAroundStatus.RUNNING
        internal set
    var endedAt: Tick? = null
        internal set

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(company.value).putLong(patent.value).putLong(startedAt.value).putLong(completesAt.value)
        h.putMoney(cost).putLong(successPpm).putLong(dispute?.value ?: -1L).putInt(status.ordinal).putLong(endedAt?.value ?: -1L)
    }
}

/** A product the company may sell without a licence because it engineered around a patent. */
data class Clearance(val company: CompanyId, val patent: PatentId, val since: Tick) : Comparable<Clearance> {
    override fun compareTo(other: Clearance): Int {
        val c = company.compareTo(other.company)
        return if (c != 0) c else patent.compareTo(other.patent)
    }
}

enum class CompulsoryGround(val label: String) {
    NON_WORKING("Patent not worked in the country"),
    PUBLIC_HEALTH("Public health emergency"),
    ANTI_COMPETITIVE("Refusal to license harms competition"),
    GOVERNMENT_USE("Use by the state"),
}

enum class CompulsoryStatus(val label: String, val open: Boolean) {
    PENDING("Under review", true),
    GRANTED("Granted", false),
    DENIED("Denied", false),
    WITHDRAWN("Withdrawn", false),
}

/** A request to a government to force a licence on a patent holder. */
class CompulsoryRequest(
    val id: CompulsoryId,
    val applicant: CompanyId,
    val patent: PatentId,
    val country: CountryId,
    val ground: CompulsoryGround,
    val filedAt: Tick,
    val decideAt: Tick,
) {
    var status: CompulsoryStatus = CompulsoryStatus.PENDING
        internal set
    var licenseId: LicenseId? = null
        internal set
    var reason: String = ""
        internal set

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(applicant.value).putLong(patent.value).putLong(country.value).putInt(ground.ordinal)
        h.putLong(filedAt.value).putLong(decideAt.value).putInt(status.ordinal).putLong(licenseId?.value ?: -1L).putString(reason)
    }
}

/** How a company wants its rights policed. */
enum class EnforcementStance(val label: String) {
    PASSIVE("Leave it alone"),
    LICENSE_FIRST("Offer a licence first"),
    LITIGATE("Go to court"),
}

/** A company's standing instructions for its portfolio. */
data class IpPolicy(
    val stance: EnforcementStance = EnforcementStance.LICENSE_FIRST,
    /** Share of the monthly detection effort the company pays for, ppm. */
    val monitoringPpm: Long = 400_000L,
    val defaultBreadth: BreadthChoice = BreadthChoice.STANDARD,
    val defaultCountries: List<CountryId> = emptyList(),
    val autoMaintain: Boolean = true,
) {
    init { require(monitoringPpm in 0L..1_000_000L) { "monitoring must be between 0 and 100%" } }

    fun hashInto(h: CanonicalHasher) {
        h.putInt(stance.ordinal).putLong(monitoringPpm).putInt(defaultBreadth.ordinal).putBoolean(autoMaintain)
        h.putInt(defaultCountries.size); for (c in defaultCountries) h.putLong(c.value)
    }
}
