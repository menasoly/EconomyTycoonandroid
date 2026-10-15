package app.many.economytycoon.finance

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId

/** The five classes of the fixed chart of accounts. Entries are signed: debit positive, credit negative. */
enum class AccountClass(val debitNormal: Boolean) {
    ASSET(true),
    LIABILITY(false),
    EQUITY(false),
    REVENUE(false),
    EXPENSE(true);

    val isBalanceSheet get() = this == ASSET || this == LIABILITY || this == EQUITY
}

/**
 * Chart of accounts. Every owner gets an instance of each chart entry on demand. [contra] marks
 * accounts that carry the opposite sign of their class, such as accumulated depreciation.
 */
enum class Chart(val code: Int, val label: String, val cls: AccountClass, val contra: Boolean = false) {
    CASH(1000, "Cash", AccountClass.ASSET),
    RECEIVABLES(1100, "Receivables", AccountClass.ASSET),
    INVENTORY(1200, "Inventory", AccountClass.ASSET),
    PREPAID(1300, "Prepaid expenses", AccountClass.ASSET),
    FIXED_ASSETS(1500, "Fixed assets at cost", AccountClass.ASSET),
    ACCUMULATED_DEPRECIATION(1590, "Accumulated depreciation", AccountClass.ASSET, contra = true),
    INTANGIBLES(1700, "Intangible assets", AccountClass.ASSET),
    INVESTMENTS(1800, "Investments", AccountClass.ASSET),
    LOANS_RECEIVABLE(1900, "Loans receivable", AccountClass.ASSET),

    PAYABLES(2000, "Accounts payable", AccountClass.LIABILITY),
    WAGES_PAYABLE(2100, "Wages payable", AccountClass.LIABILITY),
    TAX_PAYABLE(2200, "Taxes payable", AccountClass.LIABILITY),
    ACCRUED_INTEREST(2300, "Accrued interest", AccountClass.LIABILITY),
    DEFERRED_REVENUE(2400, "Deferred revenue", AccountClass.LIABILITY),
    DEBT_SHORT(2500, "Short-term debt", AccountClass.LIABILITY),
    DEBT_LONG(2600, "Long-term debt", AccountClass.LIABILITY),
    DEPOSITS(2700, "Customer deposits", AccountClass.LIABILITY),

    SHARE_CAPITAL(3000, "Share capital", AccountClass.EQUITY),
    RETAINED_EARNINGS(3100, "Retained earnings", AccountClass.EQUITY),
    DIVIDENDS(3200, "Dividends declared", AccountClass.EQUITY, contra = true),
    TREASURY_SHARES(3300, "Treasury shares", AccountClass.EQUITY, contra = true),

    REVENUE_SALES(4000, "Sales revenue", AccountClass.REVENUE),
    REVENUE_SERVICES(4100, "Service revenue", AccountClass.REVENUE),
    REVENUE_OTHER(4900, "Other income", AccountClass.REVENUE),
    INTEREST_INCOME(4950, "Interest income", AccountClass.REVENUE),

    COGS(5000, "Cost of goods sold", AccountClass.EXPENSE),
    WAGES_EXPENSE(5100, "Wages", AccountClass.EXPENSE),
    ENERGY_EXPENSE(5200, "Energy", AccountClass.EXPENSE),
    RENT_EXPENSE(5300, "Rent and upkeep", AccountClass.EXPENSE),
    MAINTENANCE_EXPENSE(5400, "Maintenance", AccountClass.EXPENSE),
    TRANSPORT_EXPENSE(5500, "Transport", AccountClass.EXPENSE),
    MARKETING_EXPENSE(5600, "Marketing", AccountClass.EXPENSE),
    RESEARCH_EXPENSE(5700, "Research and development", AccountClass.EXPENSE),
    WASTE_EXPENSE(5800, "Waste disposal", AccountClass.EXPENSE),
    DEPRECIATION_EXPENSE(5900, "Depreciation", AccountClass.EXPENSE),
    INTEREST_EXPENSE(6000, "Interest", AccountClass.EXPENSE),
    TAX_EXPENSE(6100, "Income tax", AccountClass.EXPENSE),
    OTHER_EXPENSE(6900, "Other expenses", AccountClass.EXPENSE);

    /** +1 when the signed (debit-positive) balance reads naturally, -1 when it must be negated. */
    val naturalSign: Int get() = if (cls.debitNormal != contra) 1 else -1
}

enum class OwnerKind { COMPANY, GOVERNMENT, BANK, HOUSEHOLDS, SYSTEM }

/** Anything that can hold accounts. Ordered so iteration is deterministic. */
data class LedgerOwner(val kind: OwnerKind, val id: Long) : Comparable<LedgerOwner> {
    override fun compareTo(other: LedgerOwner): Int {
        val k = kind.compareTo(other.kind)
        return if (k != 0) k else id.compareTo(other.id)
    }

    override fun toString() = "${kind.name.lowercase()}:$id"

    companion object {
        fun company(id: CompanyId) = LedgerOwner(OwnerKind.COMPANY, id.value)
        fun government(id: CountryId) = LedgerOwner(OwnerKind.GOVERNMENT, id.value)
        fun bank(id: Long = 0L) = LedgerOwner(OwnerKind.BANK, id)
        fun households(region: Long = 0L) = LedgerOwner(OwnerKind.HOUSEHOLDS, region)
        val SYSTEM = LedgerOwner(OwnerKind.SYSTEM, 0L)
    }
}

/** Reference to one account instance: an owner and a chart entry. */
data class AccountRef(val owner: LedgerOwner, val chart: Chart) : Comparable<AccountRef> {
    override fun compareTo(other: AccountRef): Int {
        val o = owner.compareTo(other.owner)
        return if (o != 0) o else chart.code.compareTo(other.chart.code)
    }

    override fun toString() = "$owner/${chart.label}"
}

/** Why a transaction was posted. Used for audit, filtering and statements. */
enum class ReasonCode {
    OPENING, CAPITAL, SALE, PURCHASE, WAGES, TAX, LOAN_DRAW, LOAN_REPAYMENT, LOAN_INTEREST, DIVIDEND, DEPRECIATION,
    TRANSFER, STIPEND, ARREARS, LIQUIDATION, ADJUSTMENT, ISSUANCE, BUYBACK, FEE, SUBSIDY, RENT, ENERGY, TRANSPORT,
    INVENTORY, CLOSING, MINT, INSURANCE
}
