package app.many.economytycoon.companies

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.IndustryId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.Holder
import app.many.economytycoon.finance.LedgerOwner

enum class CompanyKind { PLAYER, AI, STATE }

enum class CompanyStatus { ACTIVE, ADMINISTRATION, LIQUIDATING, DISSOLVED, ACQUIRED }

/** A legal entity. Money lives in the ledger under [owner]; this class holds identity and standing. */
class Company(
    val id: CompanyId,
    name: String,
    val kind: CompanyKind,
    val country: CountryId,
    val founded: Tick,
    val founder: Holder,
) {
    var name: String = name
        internal set
    var hq: CityId? = null
        internal set
    var status: CompanyStatus = CompanyStatus.ACTIVE
        internal set
    /** Personality or scripted behaviour id for AI companies; empty for players. */
    var strategy: String = ""
        internal set
    var endedAt: Tick? = null
        internal set
    val industries = java.util.TreeSet<IndustryId>()

    val owner: LedgerOwner get() = LedgerOwner.company(id)
    val holder: Holder get() = Holder.owner(owner)
    val isActive: Boolean get() = status == CompanyStatus.ACTIVE || status == CompanyStatus.ADMINISTRATION
    val isPlayer: Boolean get() = kind == CompanyKind.PLAYER

    internal fun restore(name: String, hq: CityId?, status: CompanyStatus, strategy: String, endedAt: Tick?) {
        this.name = name; this.hq = hq; this.status = status; this.strategy = strategy; this.endedAt = endedAt
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putString(name).putInt(kind.ordinal).putLong(country.value).putLong(founded.value).putString(founder.toString())
            .putLong(hq?.value ?: -1L).putInt(status.ordinal).putString(strategy).putLong(endedAt?.value ?: -1L)
        h.putInt(industries.size)
        for (i in industries) h.putString(i.key)
    }
}
