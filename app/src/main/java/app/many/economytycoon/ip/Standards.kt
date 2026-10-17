package app.many.economytycoon.ip

import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules

/**
 * Declaring a patent essential to an industry standard. A declaration brings the patent a value premium and, because
 * standards bodies insist on it, a pledge to license on fair terms. The pledge cannot be taken back.
 */
class StandardsService(private val state: IpState, val params: IpParams = IpParams()) {
    private val minBreadthPpm get() = PoolService.MIN_ESSENTIAL_BREADTH_PPM

    fun declare(patent: PatentId, by: IpOwner, pledgeFrand: Boolean): Outcome<Patent> {
        val p = state.patentMap[patent] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown patent")
        if (p.owner != by) return Outcome.reject(Rules.NOT_OWNER, "only the owner can declare a patent essential")
        if (!p.isLive) return Outcome.reject(Rules.WRONG_STATE, "nothing in force to declare")
        if (p.standardEssential) return Outcome.reject(Rules.DUPLICATE, "already declared essential")
        if (!pledgeFrand) return Outcome.reject(Rules.REGULATION, "an essential patent must carry a FRAND pledge")
        if (p.grantedBreadthPpm() < minBreadthPpm) return Outcome.reject(Rules.INVALID_ARGUMENT, "the claims are too narrow to be essential")
        p.standardEssential = true
        p.frandPledged = true
        return Outcome.ok(p)
    }

    /** Pledges FRAND on a patent that was not declared; useful before joining a pool. Irrevocable. */
    fun pledge(patent: PatentId, by: IpOwner): Outcome<Patent> {
        val p = state.patentMap[patent] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown patent")
        if (p.owner != by) return Outcome.reject(Rules.NOT_OWNER, "only the owner can pledge")
        if (p.frandPledged) return Outcome.reject(Rules.DUPLICATE, "already pledged")
        p.frandPledged = true
        return Outcome.ok(p)
    }

    fun essentialIn(field: TechField): List<Patent> = state.patentMap.values.filter { it.standardEssential && it.field == field && it.isLive }
}
