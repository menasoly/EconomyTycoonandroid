package app.many.economytycoon.talent

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.workforce.CohortKey

/**
 * A named person in a key role. Most workers are anonymous members of a cohort; executives and managers are tracked
 * one by one because the game needs to hire, develop, keep and lose them as individuals. Each person is also counted
 * in their cohort's head count, in the company-level cohort of their role.
 */
class Person(
    val id: PersonId,
    company: CompanyId,
    val name: String,
    role: RoleId,
    region: RegionId,
    hiredAt: Tick,
) {
    var company: CompanyId = company
        internal set
    var role: RoleId = role
        internal set
    var region: RegionId = region
        internal set
    var hiredAt: Tick = hiredAt
        internal set
    var ageYears: Int = 40
        internal set
    var wage: Money = Money.ZERO
        internal set
    var loyaltyPpm: Long = 600_000L
        internal set
    /** The highest level of leadership this person could grow into, ppm. */
    var potentialPpm: Long = 700_000L
        internal set
    internal val levels = java.util.TreeMap<SkillId, Long>()
    var position: PositionId? = null
        internal set
    var retiredAt: Tick? = null
        internal set

    val cohort: CohortKey get() = CohortKey(company, FacilityId(0L), role, region)
    val isActive: Boolean get() = retiredAt == null
    fun level(skill: SkillId): Long = levels[skill] ?: 0L
    fun levelsView(): Map<SkillId, Long> = java.util.TreeMap(levels)

    internal fun restore(age: Int, wage: Money, loyalty: Long, potential: Long, position: PositionId?, retiredAt: Tick?, levels: Map<SkillId, Long>) {
        ageYears = age; this.wage = wage; loyaltyPpm = loyalty; potentialPpm = potential; this.position = position; this.retiredAt = retiredAt
        this.levels.clear(); this.levels.putAll(levels)
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(company.value).putString(name).putString(role.key).putLong(region.value).putLong(hiredAt.value)
        h.putInt(ageYears).putMoney(wage).putLong(loyaltyPpm).putLong(potentialPpm).putLong(position?.value ?: -1L).putLong(retiredAt?.value ?: -1L)
        h.putInt(levels.size); for ((s, v) in levels) h.putString(s.key).putLong(v)
    }
}

/** A seat that matters: someone must hold it, and the company suffers while it is empty or badly filled. */
class KeyPosition(
    val id: PositionId,
    val company: CompanyId,
    val role: RoleId,
    val region: RegionId,
    val title: String,
) {
    var holder: PersonId? = null
        internal set
    var vacantSince: Tick? = null
        internal set
    /** A successor who was not fully ready needs time to settle; until then output is lower. */
    var settlingUntil: Tick? = null
        internal set
    var settlingPpm: Long = 0L
        internal set
    /** Search for an outside hire ends here, if one is under way. */
    var searchUntil: Tick? = null
        internal set
    var searchSpent: Money = Money.ZERO
        internal set

    val isVacant: Boolean get() = holder == null

    internal fun restore(holder: PersonId?, vacantSince: Tick?, settlingUntil: Tick?, settlingPpm: Long, searchUntil: Tick?, searchSpent: Money) {
        this.holder = holder; this.vacantSince = vacantSince; this.settlingUntil = settlingUntil; this.settlingPpm = settlingPpm
        this.searchUntil = searchUntil; this.searchSpent = searchSpent
    }

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(company.value).putString(role.key).putLong(region.value).putString(title)
        h.putLong(holder?.value ?: -1L).putLong(vacantSince?.value ?: -1L).putLong(settlingUntil?.value ?: -1L).putLong(settlingPpm)
            .putLong(searchUntil?.value ?: -1L).putMoney(searchSpent)
    }
}

/** How a successor is being prepared. [paceMultiplierPpm] scales monthly readiness growth. */
enum class DevelopmentKind(val label: String, val costPerDayUnits: Long, val paceMultiplierPpm: Long) {
    NONE("No development", 0L, 300_000L),
    MENTORING("Mentoring by the incumbent", 6L, 1_000_000L),
    STRETCH("Stretch assignments", 14L, 1_600_000L),
    EXECUTIVE_COURSE("Executive course", 30L, 2_200_000L),
}

/** A person named as able to take over a position, and how ready they are, 0..1,000,000. */
class Successor(val person: PersonId, var readinessPpm: Long, var development: DevelopmentKind, val namedAt: Tick) {
    internal fun hashInto(h: CanonicalHasher) {
        h.putLong(person.value).putLong(readinessPpm).putInt(development.ordinal).putLong(namedAt.value)
    }
}
