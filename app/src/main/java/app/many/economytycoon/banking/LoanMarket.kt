package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.finance.CreditRating

/** A loan block a bank wrote this month. */
data class Origination(val bank: BankId, val sector: LoanSector, val rating: CreditRating, val amount: Money, val ratePpm: Long, val fee: Money)

/**
 * The market for new loans in one country. Borrowers of each sector and rating come with a monthly demand; every bank
 * decides whether and how much to lend at what price, and the demand is shared out among the banks that would lend, those
 * who quote lower rates winning more. What nobody will lend is not lent.
 */
class LoanMarket(private val params: BankingParams) {
    private val p get() = params.lending

    /** The pull of a quote relative to the average quote: cheaper wins more. */
    fun pull(ratePpm: Long, averagePpm: Long): Long {
        val cheaperBps = (averagePpm - ratePpm) / 100L
        return (p.neutralWeight + p.pullPerBasisPoint * cheaperBps).coerceIn(p.minWeight, p.neutralWeight * 5L)
    }

    /**
     * Shares out one block of demand. [decisions] are the approvals of the banks, in bank order. Returns the amount each
     * approved bank gets, never more than it approved.
     */
    fun allocate(block: Money, decisions: List<Pair<BankId, LoanDecision.Approved>>): Map<BankId, Money> {
        if (decisions.isEmpty() || block.raw <= 0L) return emptyMap()
        val average = decisions.sumOf { it.second.quote.total.ppm } / decisions.size
        val weights = LongArray(decisions.size) { pull(decisions[it].second.quote.total.ppm, average) }
        val caps = Array(decisions.size) { decisions[it].second.amount }
        val parts = BankMath.splitCapped(block, weights, caps)
        val out = java.util.TreeMap<BankId, Money>()
        for (i in decisions.indices) if (parts[i].isPositive) out[decisions[i].first] = parts[i]
        return out
    }

    /** Which deposit book a borrower's new balance lands in. */
    fun borrowerBook(sector: LoanSector): DepositKey = when (sector) {
        LoanSector.MORTGAGE, LoanSector.CONSUMER -> DepositKey(Depositor.HOUSEHOLD, DepositProduct.DEMAND)
        else -> DepositKey(Depositor.CORPORATE, DepositProduct.DEMAND)
    }

    /**
     * Books a loan: the pool grows, the borrower's account is credited with what stays in the bank, the rest leaves as
     * payments to others, and the fee is paid. The books stay in balance.
     */
    fun book(bank: Bank, sector: LoanSector, rating: CreditRating, amount: Money, ratePpm: Long): Origination {
        val leak = amount.times(p.leakage)
        val fee = amount.times(p.originationFee)
        bank.addLoans(PoolKey(sector, rating, LoanStage.PERFORMING), amount, ratePpm)
        bank.addDeposits(borrowerBook(sector), amount - leak)
        bank.reserves -= leak
        bank.reserves += fee
        bank.retained += fee
        return Origination(bank.id, sector, rating, amount, ratePpm, fee)
    }

    /**
     * Runs the month's lending for a country. Each sector in turn: split demand by rating, ask every bank, share out. Bank
     * contexts are rebuilt per sector so that capital and liquidity used up by one sector limit the next.
     */
    fun run(banks: List<Bank>, inputs: BankingInputs): List<Origination> {
        val live = banks.filter { it.status.isOperating }.sortedBy { it.id }
        if (live.isEmpty()) return emptyList()
        val country = live.first().country
        val usedTotal = java.util.TreeMap<BankId, Money>()
        val usedSector = java.util.TreeMap<Pair<BankId, LoanSector>, Money>(compareBy<Pair<BankId, LoanSector>>({ it.first }, { it.second }))
        val out = ArrayList<Origination>()
        for (sector in LoanSector.values()) {
            val demand = inputs.loanDemand(country, sector)
            if (demand.raw <= 0L) continue
            val mix = RiskTables.applicantMixUnder(sector, inputs.defaultMultiplier(country, sector))
            val blocks = BankMath.split(demand, mix)
            val contexts = live.associate { it.id to LendingContext(inputs, params, it.snapshot()) }.toMutableMap()
            for ((r, rating) in RiskTables.LENDABLE.withIndex()) {
                val block = blocks[r]
                if (block < Money.units(p.minTicketUnits)) continue
                val application = LoanApplication(sector, rating, block)
                val approved = ArrayList<Pair<BankId, LoanDecision.Approved>>()
                for (bank in live) {
                    val ctx = contexts.getValue(bank.id)
                    val decision = Underwriter.evaluate(ctx, application, usedSector[bank.id to sector] ?: Money.ZERO, usedTotal[bank.id] ?: Money.ZERO)
                    if (decision is LoanDecision.Approved) approved.add(bank.id to decision)
                }
                val shares = allocate(block, approved)
                for ((id, amount) in shares) {
                    val bank = live.first { it.id == id }
                    val quote = approved.first { it.first == id }.second.quote
                    out += book(bank, sector, rating, amount, quote.total.ppm)
                    usedTotal[id] = (usedTotal[id] ?: Money.ZERO) + amount
                    usedSector[id to sector] = (usedSector[id to sector] ?: Money.ZERO) + amount
                    contexts[id] = LendingContext(inputs, params, bank.snapshot())
                }
            }
        }
        return out
    }

}
