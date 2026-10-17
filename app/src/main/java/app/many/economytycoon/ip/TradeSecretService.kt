package app.many.economytycoon.ip

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.core.Calendar
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.TechId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/** The arithmetic of keeping something secret. */
object SecretRules {
    /** Protection bought by [spent] in total: saturating, never above the cap. */
    fun protectionFor(p: SecretParams, spent: Money): Long {
        val s = spent.raw
        if (s <= 0L) return 0L
        val half = Money.units(p.securityHalfUnits).raw
        return mulDiv(p.maxProtectionPpm, s, s + half)
    }

    /** Chance per day that someone talks, ppm. More people who know it and less protection both raise it. */
    fun dailyLeakPpm(p: SecretParams, knowers: Int, protectionPpm: Long): Long {
        val raw = p.baseLeakPpmPerDay + p.perKnowerLeakPpmPerDay * knowers.coerceAtLeast(0)
        return mulDiv(raw, 1_000_000L - protectionPpm.coerceIn(0L, 1_000_000L), 1_000_000L)
    }

    /** Chance per day that rivals work the secret out from the product, ppm. Protection only half-hides a visible product. */
    fun dailyReverseEngineerPpm(p: SecretParams, exposurePpm: Long, protectionPpm: Long): Long {
        val raw = mulDiv(p.reverseEngineeringPpmPerDay, exposurePpm.coerceIn(0L, 1_000_000L), 1_000_000L)
        return mulDiv(raw, 1_000_000L - protectionPpm.coerceIn(0L, 1_000_000L) / 2, 1_000_000L)
    }

    fun dailyHazardPpm(p: SecretParams, knowers: Int, exposurePpm: Long, protectionPpm: Long): Long =
        (dailyLeakPpm(p, knowers, protectionPpm) + dailyReverseEngineerPpm(p, exposurePpm, protectionPpm)).coerceAtMost(1_000_000L)

    /** Probability the secret survives [days] days at a constant daily hazard, ppm. */
    fun survivalPpm(hazardPpm: Long, days: Int): Long {
        var result = 1_000_000L
        var base = (1_000_000L - hazardPpm.coerceIn(0L, 1_000_000L))
        var e = days.coerceAtLeast(0)
        while (e > 0) {
            if (e and 1 == 1) result = mulDiv(result, base, 1_000_000L)
            base = mulDiv(base, base, 1_000_000L)
            e = e shr 1
        }
        return result
    }

    /** Average days until the secret goes, at a constant hazard; very large when the hazard is zero. */
    fun expectedLifeDays(hazardPpm: Long): Long = if (hazardPpm <= 0L) Long.MAX_VALUE / 4 else 1_000_000L / hazardPpm

    /** Chance an espionage attempt gets through, ppm. [skillPpm] is the attacker's competence, 1.0 being average. */
    fun espionageSuccessPpm(p: SecretParams, protectionPpm: Long, skillPpm: Long): Long =
        mulDiv(mulDiv(p.espionageSuccessPpm, 1_000_000L - protectionPpm.coerceIn(0L, 1_000_000L), 1_000_000L), skillPpm, 1_000_000L).coerceIn(0L, 950_000L)

    /** Chance the owner notices an attempt, ppm; a failed attempt is easier to spot. */
    fun detectionPpm(p: SecretParams, protectionPpm: Long, succeeded: Boolean): Long {
        val base = p.detectionBasePpm + protectionPpm / 2
        return (if (succeeded) base else base + 200_000L).coerceIn(0L, 950_000L)
    }
}

/** What an espionage attempt came to. */
data class EspionageResult(val success: Boolean, val detected: Boolean, val evidencePpm: Long)

/** Keeps the trade secrets of every company: protecting them, losing them and having them stolen. */
class TradeSecretService(
    private val state: IpState,
    private val content: ContentRepository,
    private val holding: TechHolding,
    val params: IpParams = IpParams(),
) {
    private val sp get() = params.secrets

    fun keep(run: IpRun, company: CompanyId, tech: TechId): Outcome<TradeSecret> {
        val def = content.technologyOrNull(tech) ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown technology ${tech.key}")
        if (!holding.holds(company, tech)) return Outcome.reject(Rules.NOT_UNLOCKED, "${def.name} has not been researched")
        if (state.liveSecretFor(company, tech) != null) return Outcome.reject(Rules.DUPLICATE, "already kept secret")
        val patented = state.patentOfTech(company, tech)
        if (patented != null && patented.isLive) return Outcome.reject(Rules.DUPLICATE, "a patent on ${def.name} already makes it public")
        if (state.secretsOf(company).count { it.isLive } >= sp.maxSecretsPerCompany) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many secrets to keep")
        val s = TradeSecret(state.ids.secret(), company, tech, TechFields.of(def), run.tick)
        s.knowers = run.ctx.knowers(company, tech)
        s.exposurePpm = run.ctx.exposure(company, tech).ppm
        state.secretMap[s.id] = s
        run.out.event(IpEvent.SecretKept(s.id, company, run.tick))
        return Outcome.ok(s)
    }

    fun invest(run: IpRun, id: SecretId, by: CompanyId, amount: Money): Outcome<Long> {
        val s = state.secretMap[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown secret")
        if (s.owner != by) return Outcome.reject(Rules.NOT_OWNER, "not your secret")
        if (!s.isLive) return Outcome.reject(Rules.WRONG_STATE, "that secret is gone")
        if (amount < Money.units(sp.minInvestUnits)) return Outcome.reject(Rules.INVALID_ARGUMENT, "spend at least ${Money.units(sp.minInvestUnits).format()}")
        if (!run.wallet.tryReserve(IpOwner.company(by), amount)) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "cannot afford the security spending")
        run.out.charge(run.tick, ChargeKind.SECURITY_SPEND, IpLedger.of(by), IpLedger.professionals, amount, "secrecy measures", id.value.toString())
        s.securitySpent += amount
        s.protectionPpm = maxOf(s.protectionPpm, SecretRules.protectionFor(sp, s.securitySpent))
        return Outcome.ok(s.protectionPpm)
    }

    fun drop(run: IpRun, id: SecretId, by: CompanyId): Outcome<Unit> {
        val s = state.secretMap[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown secret")
        if (s.owner != by) return Outcome.reject(Rules.NOT_OWNER, "not your secret")
        if (!s.isLive) return Outcome.reject(Rules.WRONG_STATE, "that secret is gone")
        end(s, SecretStatus.ABANDONED, run.tick)
        return Outcome.ok(Unit)
    }

    /** Marks a secret as moved into a patent filing. */
    internal fun markPatented(id: SecretId, tick: Tick) {
        val s = state.secretMap[id] ?: return
        if (s.isLive) end(s, SecretStatus.PATENTED, tick)
    }

    private fun end(s: TradeSecret, status: SecretStatus, tick: Tick) {
        s.status = status
        s.endedAt = tick
    }

    /** The daily hazard on a secret given what is known about it now. */
    fun hazardPpm(s: TradeSecret): Long = SecretRules.dailyHazardPpm(sp, s.knowers, s.exposurePpm, s.protectionPpm)

    fun step(run: IpRun) {
        val monthly = Calendar.isMonthStart(run.tick)
        for (s in state.secretMap.values.toList()) {
            if (!s.isLive) continue
            if (!run.ctx.isActive(s.owner)) { end(s, SecretStatus.ABANDONED, run.tick); continue }
            s.knowers = run.ctx.knowers(s.owner, s.tech)
            s.exposurePpm = run.ctx.exposure(s.owner, s.tech).ppm
            if (run.ctx.independentHolders(s.tech, s.owner).isNotEmpty()) {
                end(s, SecretStatus.SHARED, run.tick)
                run.out.event(IpEvent.SecretLost(s.id, s.owner, SecretStatus.SHARED, run.tick))
                continue
            }
            val leak = SecretRules.dailyLeakPpm(sp, s.knowers, s.protectionPpm)
            if (state.dice.chance("secret.leak", Rate(leak), s.id.value, run.tick.value)) {
                end(s, SecretStatus.LEAKED, run.tick)
                run.out.event(IpEvent.SecretLost(s.id, s.owner, SecretStatus.LEAKED, run.tick))
                continue
            }
            val rev = SecretRules.dailyReverseEngineerPpm(sp, s.exposurePpm, s.protectionPpm)
            if (state.dice.chance("secret.rev", Rate(rev), s.id.value, run.tick.value)) {
                end(s, SecretStatus.REVERSE_ENGINEERED, run.tick)
                run.out.event(IpEvent.SecretLost(s.id, s.owner, SecretStatus.REVERSE_ENGINEERED, run.tick))
                continue
            }
            if (monthly && s.protectionPpm > 0L) {
                val decayed = s.protectionPpm - mulDiv(s.protectionPpm, sp.protectionDecayPerMonthPpm, 1_000_000L)
                s.protectionPpm = decayed.coerceAtLeast(0L)
            }
        }
    }

    /**
     * A rival tries to steal a secret. The attacker pays for the attempt whatever happens. [skillPpm] is the attacker's
     * competence, 1.0 being average. A success puts the attacker on the list of those who know it.
     */
    fun espionage(run: IpRun, attacker: CompanyId, id: SecretId, skillPpm: Long = 1_000_000L): Outcome<EspionageResult> {
        val s = state.secretMap[id] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown secret")
        if (!s.isLive) return Outcome.reject(Rules.WRONG_STATE, "that secret is gone")
        if (s.owner == attacker) return Outcome.reject(Rules.INVALID_ARGUMENT, "cannot spy on yourself")
        if (!run.ctx.isActive(attacker)) return Outcome.reject(Rules.WRONG_STATE, "the attacker is out of business")
        if (attacker in s.knownToSet) return Outcome.reject(Rules.DUPLICATE, "already known to the attacker")
        val cost = Money.units(sp.espionageCostUnits)
        if (!run.wallet.tryReserve(IpOwner.company(attacker), cost)) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "cannot pay for the operation")
        run.out.charge(run.tick, ChargeKind.SECURITY_SPEND, IpLedger.of(attacker), IpLedger.professionals, cost, "industrial espionage", id.value.toString())
        val success = state.dice.chance("espionage.hit", Rate(SecretRules.espionageSuccessPpm(sp, s.protectionPpm, skillPpm)), id.value, attacker.value * 100_000 + run.tick.value)
        val detected = state.dice.chance("espionage.seen", Rate(SecretRules.detectionPpm(sp, s.protectionPpm, success)), id.value, attacker.value * 100_000 + run.tick.value)
        if (success) { s.knownToSet.add(attacker); s.incidents++ }
        val evidence = if (detected) (400_000L + s.protectionPpm / 3 + if (success) 150_000L else 0L).coerceAtMost(900_000L) else 0L
        run.out.event(IpEvent.EspionageAttempt(id, attacker, success, detected, run.tick))
        return Outcome.ok(EspionageResult(success, detected, evidence))
    }

    /** Value a secret protects per year of exclusivity: the part of the company's sales in its field the secret supports. */
    fun annualAdvantage(run: IpRun, s: TradeSecret): Money =
        Money(mulDiv(run.ctx.annualSalesInField(s.owner, s.field).raw, params.valuation.baseRoyaltyPpm, 1_000_000L))
}
