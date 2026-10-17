package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.finance.CreditRating

/** Identifies a commercial bank. Allocated by [BankingService]; never reused. */
@JvmInline
value class BankId(val value: Long) : Comparable<BankId> {
    override fun compareTo(other: BankId) = value.compareTo(other.value)
    override fun toString() = "Bank#$value"
}

/** Who makes the decisions of a bank. */
enum class BankOperator { PLAYER, AI }

/** Where a bank stands in its life. Only [ACTIVE] and [SUPERVISED] banks take part in the markets. */
enum class BankStatus(val label: String) {
    ACTIVE("Active"),
    SUPERVISED("Under supervision"),
    IN_RESOLUTION("In resolution"),
    MERGED("Merged into another bank"),
    LIQUIDATED("Liquidated");

    val isOperating: Boolean get() = this == ACTIVE || this == SUPERVISED
}

/** Kinds of lending a bank can do. Each has its own risk, collateral and run-off profile. */
enum class LoanSector(val label: String) {
    MORTGAGE("Home mortgages"),
    CONSUMER("Consumer credit"),
    SMALL_BUSINESS("Small business"),
    CORPORATE("Corporate"),
    COMMERCIAL_PROPERTY("Commercial property"),
    TRADE_FINANCE("Trade finance"),
}

/** Credit stages in the expected-loss model: performing, watch-listed (significant deterioration) and impaired. */
enum class LoanStage(val label: String) {
    PERFORMING("Stage 1"),
    WATCH("Stage 2"),
    IMPAIRED("Stage 3"),
}

enum class Depositor(val label: String) { HOUSEHOLD("Households"), CORPORATE("Companies") }

enum class DepositProduct(val label: String) {
    DEMAND("Current accounts"),
    SAVINGS("Savings"),
    TERM("Term deposits"),
}

enum class SecurityKind(val label: String) {
    GOVERNMENT_SHORT("Short government bills"),
    GOVERNMENT_LONG("Long government bonds"),
    CORPORATE_BONDS("Corporate bonds"),
}

/** The ladder the supervisor climbs as a bank's capital erodes. A higher ordinal is more serious. */
enum class SupervisoryRung(val label: String) {
    NORMAL("Normal"),
    BUFFER("Inside the capital buffer"),
    RESTRICTED("Below the requirement"),
    SEVERE("Severely undercapitalised"),
    CRITICAL("Critically undercapitalised");

    val isBreach: Boolean get() = this >= RESTRICTED
}

/** How worried depositors are. */
enum class RunStage(val label: String) { CALM("Calm"), WATCHFUL("Watchful"), RUN("Run"), PANIC("Panic") }

/** Key of a pool of loans of one sector, original rating and stage. */
data class PoolKey(val sector: LoanSector, val rating: CreditRating, val stage: LoanStage) : Comparable<PoolKey> {
    override fun compareTo(other: PoolKey): Int {
        val a = sector.compareTo(other.sector)
        if (a != 0) return a
        val b = rating.compareTo(other.rating)
        return if (b != 0) b else stage.compareTo(other.stage)
    }
}

/** A pool of loans of the same sector, rating and stage: balance and balance-weighted rate. */
data class LoanPool(val principal: Money, val ratePpm: Long) {
    init { require(principal.raw >= 0L) { "pool balance cannot be negative" } }

    companion object {
        val EMPTY = LoanPool(Money.ZERO, 0L)
    }
}

/** Key of a deposit book. */
data class DepositKey(val depositor: Depositor, val product: DepositProduct) : Comparable<DepositKey> {
    override fun compareTo(other: DepositKey): Int {
        val a = depositor.compareTo(other.depositor)
        return if (a != 0) a else product.compareTo(other.product)
    }
}

/** Balance of one deposit book and the rate paid on it. */
data class DepositPool(val balance: Money, val ratePpm: Long) {
    init { require(balance.raw >= 0L) { "deposits cannot be negative" } }
}

/** A holding of securities at fair value, with the yield it earns and its modified duration in months. */
data class SecurityHolding(val value: Money, val yieldPpm: Long, val durationMonths: Int) {
    init { require(value.raw >= 0L && durationMonths >= 0) { "bad security holding" } }
}
