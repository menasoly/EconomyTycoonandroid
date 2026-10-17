package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate

/** The outcome of one day in the overnight market of a country. */
data class InterbankResult(
    val rate: Rate,
    /** New lending arranged today. */
    val volume: Money,
    /** Lending repaid today because the borrower no longer needed it. */
    val repaid: Money,
    val totalNeed: Money,
    val totalSupply: Money,
    /** Borrowers that could not be served, with what they still lack. They turn to the central bank. */
    val unmet: Map<BankId, Money>,
    /** True when banks needed funds and nobody was willing to lend. */
    val frozen: Boolean,
) {
    /** How scarce funds are, from 0 (plenty) to 1,000,000 (nothing on offer). */
    val scarcityPpm: Long get() = if (totalNeed.raw + totalSupply.raw <= 0L) 500_000L else BankMath.ratio(totalNeed, totalNeed + totalSupply).ppm

    companion object {
        fun idle(rate: Rate) = InterbankResult(rate, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, emptyMap(), false)
    }
}

/**
 * The overnight market between the banks of one country. A bank short of reserves borrows from banks with a surplus;
 * the rate sits inside the central bank's corridor, higher the scarcer funds are. Banks the others do not trust cannot
 * borrow and fall back on the central bank, and nobody lends while the lender itself is in a run.
 *
 * Positions are rolled daily: a bank with a surplus first repays what it owes, and total lending always equals total
 * borrowing.
 */
class InterbankMarket(private val params: BankingParams) {

    fun desiredReserves(bank: Bank): Money = LiquidityEngine.position(bank.snapshot(), params.liquidity, params.deposits::insuredShare).desiredReserves

    fun canBorrow(s: BankSnapshot, cet1: Money): Boolean =
        s.isOperating && s.rating.ordinal <= params.facilities.interbankMinRating.ordinal &&
            s.rung < SupervisoryRung.SEVERE && s.runStage < RunStage.PANIC && cet1.isPositive

    fun canLend(s: BankSnapshot): Boolean = s.isOperating && s.runStage < RunStage.RUN && !s.rung.isBreach

    fun clear(banks: List<Bank>, policy: Rate): InterbankResult {
        val live = banks.filter { it.status.isOperating }.sortedBy { it.id }
        val corridor = CentralBankFacilities.corridor(policy, params.facilities)
        if (live.size < 2) return InterbankResult.idle(corridor.policy)
        val desired = live.associate { it.id to desiredReserves(it) }

        // 1. Banks with a surplus repay what they owe.
        var repaid = Money.ZERO
        val repayments = Array(live.size) { i ->
            val gap = live[i].reserves - desired.getValue(live[i].id)
            if (gap.raw > 0L) Money.min(gap, live[i].interbankBorrowed) else Money.ZERO
        }
        val totalRepay = Money.sum(repayments.toList())
        if (totalRepay.raw > 0L) {
            val received = BankMath.splitCapped(totalRepay, LongArray(live.size) { live[it].interbankLent.raw }, Array(live.size) { live[it].interbankLent })
            val delivered = Money.sum(received.toList())
            // If the lenders cannot absorb everything (never in a consistent market) scale the repayments back.
            val scale = if (delivered == totalRepay) repayments else BankMath.split(delivered, LongArray(live.size) { repayments[it].raw })
            for (i in live.indices) {
                live[i].reserves -= scale[i]
                live[i].interbankBorrowed -= scale[i]
                live[i].reserves += received[i]
                live[i].interbankLent -= received[i]
            }
            repaid = delivered
        }

        // 2. Needs and offers.
        val snaps = live.map { it.snapshot() }
        val need = Array(live.size) { Money.ZERO }
        val unmet = java.util.TreeMap<BankId, Money>()
        val offer = Array(live.size) { Money.ZERO }
        for (i in live.indices) {
            val gap = live[i].reserves - desired.getValue(live[i].id)
            if (gap.raw < 0L) {
                if (canBorrow(snaps[i], snaps[i].cet1Capital)) need[i] = -gap else unmet[live[i].id] = -gap
            } else if (gap.raw > 0L && canLend(snaps[i])) {
                val room = snaps[i].cet1Capital.coerceAtLeast(Money.ZERO).times(params.facilities.interbankLimitToCapital) - live[i].interbankLent
                offer[i] = Money.min(gap, room.coerceAtLeast(Money.ZERO))
            }
        }
        val totalNeed = Money.sum(need.toList())
        val totalSupply = Money.sum(offer.toList())
        val volume = Money.min(totalNeed, totalSupply)
        val scarcity = if (totalNeed.raw + totalSupply.raw <= 0L) 500_000L else BankMath.ratio(totalNeed, totalNeed + totalSupply).ppm
        val frozen = totalNeed.isPositive && totalSupply.isZero
        val width = corridor.ceiling.ppm - corridor.floor.ppm
        val rate = if (frozen) corridor.ceiling else Rate(corridor.floor.ppm + width * scarcity / Rate.ONE_PPM)

        // 3. Match and settle.
        if (volume.isPositive) {
            val borrowed = BankMath.splitCapped(volume, LongArray(live.size) { need[it].raw }, need)
            val lent = BankMath.splitCapped(volume, LongArray(live.size) { offer[it].raw }, offer)
            for (i in live.indices) {
                live[i].reserves += borrowed[i]
                live[i].interbankBorrowed += borrowed[i]
                live[i].reserves -= lent[i]
                live[i].interbankLent += lent[i]
                val left = need[i] - borrowed[i]
                if (left.isPositive) unmet[live[i].id] = left
            }
        } else {
            for (i in live.indices) if (need[i].isPositive) unmet[live[i].id] = need[i]
        }

        // 4. Interest for the day on the rolled positions, paid by borrowers and shared among lenders by what they lent.
        var cost = Money.ZERO
        val costs = Array(live.size) { BankMath.dailyInterest(live[it].interbankBorrowed, rate.ppm) }
        for (i in live.indices) cost += costs[i]
        if (cost.isPositive) {
            val income = BankMath.split(cost, LongArray(live.size) { live[it].interbankLent.raw })
            for (i in live.indices) {
                if (costs[i].isPositive) {
                    live[i].reserves -= costs[i]; live[i].retained -= costs[i]; live[i].periodInterbankCost += costs[i]
                }
                if (income[i].isPositive) {
                    live[i].reserves += income[i]; live[i].retained += income[i]; live[i].periodInterbankIncome += income[i]
                }
            }
        }
        return InterbankResult(rate, volume, repaid, totalNeed, totalSupply, unmet, frozen)
    }
}
