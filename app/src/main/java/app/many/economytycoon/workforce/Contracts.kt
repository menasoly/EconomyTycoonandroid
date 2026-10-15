package app.many.economytycoon.workforce

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.Tick

/** Terms agreed with a group of employees. Expiry triggers a negotiation. */
data class LaborContract(
    val company: CompanyId,
    /** Role the contract covers, or null for all roles of the company. */
    val role: RoleId?,
    val wage: Money,
    val start: Tick,
    val end: Tick,
    /** Days of wage paid per year of tenure when someone is let go. */
    val severanceDaysPerYear: Int = 15,
    val collective: Boolean = false,
)

enum class NegotiationOutcome { AGREED_RAISE, AGREED_FLAT, STRIKE }

data class NegotiationResult(val outcome: NegotiationOutcome, val newWage: Money, val strikeTicks: Int)

object Negotiation {
    /**
     * Resolves a contract renewal. Unhappy workers who are underpaid strike; a good reputation and decent
     * pay make a deal likely. The roll comes from the workforce random stream.
     */
    fun resolve(
        rnd: DeterministicRandom, currentWage: Money, marketWage: Money, offeredRaise: Rate,
        moralePpm: Long, reputationPpm: Long, collective: Boolean,
    ): NegotiationResult {
        val offered = currentWage + currentWage.times(offeredRaise)
        val gapPpm = if (marketWage.raw == 0L) 0L else ((marketWage.raw - offered.raw) * 1_000_000L / marketWage.raw)
        // Strike chance: base 4%, plus underpayment, plus low morale, minus reputation, doubled for collective bargaining.
        var chance = 40_000L + gapPpm.coerceAtLeast(0L) * 2 + (600_000L - moralePpm).coerceAtLeast(0L) / 2 - reputationPpm / 20L
        if (collective) chance *= 2
        chance = chance.coerceIn(5_000L, 900_000L)
        if (rnd.nextLong(1_000_000L) < chance) {
            val ticks = 6 + rnd.nextInt(20) + (if (collective) 8 else 0)
            return NegotiationResult(NegotiationOutcome.STRIKE, currentWage, ticks)
        }
        // Agreement: workers get the offer, but never less than a share of the market gap.
        val floor = currentWage + Money(((marketWage.raw - currentWage.raw).coerceAtLeast(0L)) / 3)
        val wage = Money.max(offered, floor)
        return if (wage > currentWage) NegotiationResult(NegotiationOutcome.AGREED_RAISE, wage, 0)
        else NegotiationResult(NegotiationOutcome.AGREED_FLAT, currentWage, 0)
    }

    /** Severance owed for letting [count] workers go with the given average tenure. */
    fun severance(wage: Money, count: Long, tenureTicks: Long, daysPerYear: Int): Money {
        val years = (tenureTicks / 360L).coerceAtLeast(0L)
        val days = (years * daysPerYear).coerceAtLeast(3L).coerceAtMost(180L)
        return wage * (days * count)
    }
}
