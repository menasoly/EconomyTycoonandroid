package app.many.economytycoon.banking

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate

/** The latest stress test of a bank, kept in the save. */
data class StressRecord(
    val tick: Long,
    val scenarioId: String,
    val startCet1: Rate,
    val minCet1: Rate,
    val endCet1: Rate,
    val shortfall: Money,
    val stressedLcr: Rate,
    val passed: Boolean,
) {
    fun hashInto(h: CanonicalHasher) {
        h.putLong(tick).putString(scenarioId).putRate(startCet1).putRate(minCet1).putRate(endCet1).putMoney(shortfall).putRate(stressedLcr).putBoolean(passed)
    }

    companion object {
        fun of(tick: Long, r: BankStressResult) =
            StressRecord(tick, r.scenario.id, r.startCet1Ratio, r.minCet1Ratio, r.endCet1Ratio, r.capitalShortfall, r.stressedLcr, r.passed)
    }
}

/** What the overnight market of a country did on its latest day. */
data class MarketStatus(val rate: Rate, val volume: Money, val scarcityPpm: Long, val frozen: Boolean) {
    fun hashInto(h: CanonicalHasher) { h.putRate(rate).putMoney(volume).putLong(scarcityPpm).putBoolean(frozen) }
}

/**
 * Everything the banking sector keeps: the banks, the guarantee funds, the state of the overnight markets, the
 * supervisor's stress tests and the suggestions the player has set aside. Mutation belongs to [BankingService] and the
 * engines it runs; everyone else reads.
 */
class BankingBook {
    internal val banks = java.util.TreeMap<BankId, Bank>()
    internal val funds = java.util.TreeMap<CountryId, GuaranteeFund>()
    internal val systemic = java.util.TreeMap<CountryId, Long>()
    internal val markets = java.util.TreeMap<CountryId, MarketStatus>()
    internal val stress = java.util.TreeMap<BankId, StressRecord>()
    internal val hiddenAdvice = java.util.TreeMap<String, Long>()
    internal var nextBankId: Long = 1L
    internal var lastStressTest: Long = 0L

    fun ids(): List<BankId> = banks.keys.toList()

    internal fun fundOf(country: CountryId): GuaranteeFund = funds.getOrPut(country) { GuaranteeFund(country) }

    fun systemicStress(country: CountryId): Long = systemic[country] ?: 0L

    fun market(country: CountryId): MarketStatus? = markets[country]

    internal fun operating(country: CountryId): List<Bank> = banks.values.filter { it.country == country && it.status.isOperating }

    internal fun countries(): List<CountryId> = banks.values.map { it.country }.distinct().sorted()

    internal fun clear() {
        banks.clear(); funds.clear(); systemic.clear(); markets.clear(); stress.clear(); hiddenAdvice.clear(); nextBankId = 1L; lastStressTest = 0L
    }

    fun hashInto(h: CanonicalHasher) {
        h.section("banking")
        h.putLong(nextBankId).putLong(lastStressTest)
        h.putInt(banks.size)
        for (b in banks.values) BankHashing.hash(b, h)
        h.putInt(funds.size)
        for (f in funds.values) f.hashInto(h)
        h.putInt(systemic.size)
        for ((c, v) in systemic) h.putLong(c.value).putLong(v)
        h.putInt(markets.size)
        for ((c, m) in markets) { h.putLong(c.value); m.hashInto(h) }
        h.putInt(stress.size)
        for ((id, r) in stress) { h.putLong(id.value); r.hashInto(h) }
        h.putInt(hiddenAdvice.size)
        for ((code, until) in hiddenAdvice) h.putString(code).putLong(until)
    }
}

internal object BankHashing {
    fun hash(b: Bank, h: CanonicalHasher) {
        h.putLong(b.id.value).putString(b.name).putLong(b.country.value).putInt(b.operator.ordinal).putLong(b.owner?.value ?: -1L).putLong(b.foundedAt.value)
        h.putBoolean(b.strategy != null); b.strategy?.hashInto(h)
        h.putInt(b.status.ordinal).putLong(b.statusSince.value)
        h.putMoney(b.reserves).putMoney(b.interbankLent).putMoney(b.interbankBorrowed).putMoney(b.centralBankMarginal).putMoney(b.centralBankEmergency)
        h.putMoney(b.fixedAssets).putMoney(b.intangibles).putMoney(b.shareCapital).putMoney(b.retained).putMoney(b.oci)
        h.putMoney(b.additionalTier1).putMoney(b.tier2).putMoney(b.wholesale).putLong(b.wholesaleRatePpm).putMoney(b.allowance).putMoney(b.taxLossCarry)
        h.putInt(b.loans.size)
        for ((k, p) in b.loans) h.putInt(k.sector.ordinal).putInt(k.rating.ordinal).putInt(k.stage.ordinal).putMoney(p.principal).putLong(p.ratePpm)
        h.putInt(b.deposits.size)
        for ((k, p) in b.deposits) h.putInt(k.depositor.ordinal).putInt(k.product.ordinal).putMoney(p.balance).putLong(p.ratePpm)
        h.putInt(b.securities.size)
        for ((k, s) in b.securities) h.putInt(k.ordinal).putMoney(s.value).putLong(s.yieldPpm).putInt(s.durationMonths)
        b.policy.hashInto(h)
        h.putLong(b.confidencePpm).putInt(b.runStage.ordinal).putLong(b.rumourPpm).putLong(b.supportPpm).putInt(b.panicDays).putMoney(b.lastOutflow)
        h.putInt(b.rating.ordinal).putInt(b.ratingScore).putInt(b.ratingPendingDirection).putInt(b.ratingPendingCount)
        h.putInt(b.rung.ordinal).putLong(b.rungSince.value).putInt(b.cleanMonths).putLong(b.restorationDeadline).putString(b.interventionReason)
        h.putLong(b.lastDividend?.value ?: -1L).putMoney(b.levyPaid).putLong(b.lastMonthClosed).putInt(b.monthsSincePlan)
        h.putMoney(b.periodInterbankIncome).putMoney(b.periodInterbankCost).putMoney(b.periodTrading)
        h.putInt(b.historyList.size)
        for (r in b.historyList) {
            h.putLong(r.tick).putMoney(r.loanInterest).putMoney(r.securityIncome).putMoney(r.interbankIncome).putMoney(r.depositInterest)
                .putMoney(r.wholesaleInterest).putMoney(r.centralBankInterest).putMoney(r.interbankCost).putMoney(r.subordinatedInterest)
                .putMoney(r.fees).putMoney(r.tradingResult).putMoney(r.operatingCost).putMoney(r.provisionCharge).putMoney(r.tax)
                .putMoney(r.averageEarningAssets).putMoney(r.averageLoans).putMoney(r.averageEquity).putMoney(r.averageDeposits)
        }
    }
}
