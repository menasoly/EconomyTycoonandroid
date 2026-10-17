package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.mulDiv
import app.many.economytycoon.finance.CreditRating

/**
 * Competition for deposits. A product's market rate follows the policy rate by its beta; each bank offers a premium
 * over it; customers drift towards banks that pay more and are trusted, and new money is shared out the same way.
 */
object DepositMarket {
    /** Share of new household and company money going to each product. */
    private fun productShare(depositor: Depositor, product: DepositProduct): Long = when (depositor) {
        Depositor.HOUSEHOLD -> when (product) { DepositProduct.DEMAND -> 500_000L; DepositProduct.SAVINGS -> 350_000L; DepositProduct.TERM -> 150_000L }
        Depositor.CORPORATE -> when (product) { DepositProduct.DEMAND -> 700_000L; DepositProduct.SAVINGS -> 100_000L; DepositProduct.TERM -> 200_000L }
    }

    /** What a product pays when no bank competes on price. Never negative. */
    fun marketRate(depositor: Depositor, product: DepositProduct, policy: Rate, p: DepositParams): Rate {
        val beta = p.beta(product).ppm + if (depositor == Depositor.CORPORATE) p.corporateBetaBonus.ppm else 0L
        val passed = mulDiv(policy.ppm.coerceAtLeast(0L), beta.coerceAtMost(Rate.ONE_PPM), Rate.ONE_PPM)
        return Rate((passed - p.discount(product).ppm).coerceAtLeast(0L))
    }

    /** What a bank offers: the market rate plus its premium, within the caps. Never negative. */
    fun offeredRate(market: Rate, premiumBps: Long, p: DepositParams, capped: Boolean): Rate {
        val premium = premiumBps.coerceIn(-p.maxPremiumBps, p.maxPremiumBps)
        val effective = if (capped) minOf(premium, 0L) else premium
        return Rate((market.ppm + effective * 100L).coerceAtLeast(0L))
    }

    private fun ratingFactor(r: CreditRating): Long = when (r) {
        CreditRating.AAA -> 1_150_000L
        CreditRating.AA -> 1_100_000L
        CreditRating.A -> 1_050_000L
        CreditRating.BBB -> 1_000_000L
        CreditRating.BB -> 850_000L
        CreditRating.B -> 650_000L
        CreditRating.CCC -> 450_000L
        CreditRating.D -> 200_000L
    }

    /** How strongly a bank draws customers: zero for banks that cannot take deposits, otherwise price and trust. */
    fun weight(bank: BankSnapshot, product: DepositProduct, p: DepositParams): Long {
        if (!bank.isOperating || bank.runStage >= RunStage.RUN) return 0L
        val pullBps = bank.policy.premiumOf(product).coerceIn(-p.maxPremiumBps, p.maxPremiumBps)
        val priced = (p.neutralWeight + p.pullPerBasisPoint * (if (bank.rung >= SupervisoryRung.SEVERE) minOf(pullBps, 0L) else pullBps)).coerceAtLeast(p.minWeight)
        val trusted = mulDiv(priced, bank.confidencePpm.coerceIn(0L, Rate.ONE_PPM), Rate.ONE_PPM)
        return mulDiv(trusted, ratingFactor(bank.rating), Rate.ONE_PPM).let { if (it <= 0L) 1L else it }
    }

    /** Sets the rate paid on every deposit book of a bank for the coming month. */
    fun reprice(bank: Bank, policy: Rate, p: DepositParams) {
        val capped = bank.rung >= SupervisoryRung.SEVERE
        for (depositor in Depositor.values()) for (product in DepositProduct.values()) {
            val market = marketRate(depositor, product, policy, p)
            bank.setDepositRate(DepositKey(depositor, product), offeredRate(market, bank.policy.premiumOf(product), p, capped).ppm)
        }
    }

    /**
     * Customers shop around: a share of each book leaves its bank and is shared out again by pull. Banks that are
     * distrusted lose a larger share. Returns the money that moved.
     */
    fun churn(banks: List<Bank>, depositor: Depositor, product: DepositProduct, p: DepositParams): Money {
        val live = banks.filter { it.status.isOperating }
        if (live.size < 2) return Money.ZERO
        val key = DepositKey(depositor, product)
        val snaps = live.map { it.snapshot() }
        val weights = LongArray(live.size) { weight(snaps[it], product, p) }
        if (weights.all { it == 0L }) return Money.ZERO
        var pooled = Money.ZERO
        for (b in live) {
            val distrust = Rate.ONE_PPM - b.confidencePpm
            val rate = Rate((p.churn(product).ppm * (Rate.ONE_PPM + 2L * distrust) / Rate.ONE_PPM).coerceAtMost(Rate.ONE_PPM))
            val moved = b.removeDeposits(key, b.depositPool(key).balance.times(rate))
            b.reserves -= moved
            pooled += moved
        }
        val shares = BankMath.split(pooled, weights)
        for (i in live.indices) {
            live[i].addDeposits(key, shares[i])
            live[i].reserves += shares[i]
        }
        return pooled
    }

    /**
     * New money, or a draw-down when [flow] is negative, for one kind of depositor. New money is shared out by pull and
     * split across products; a draw-down takes from every bank in proportion to its balances. Returns the amount that
     * actually moved, signed.
     */
    fun applyFlow(banks: List<Bank>, depositor: Depositor, flow: Money, p: DepositParams): Money {
        val live = banks.filter { it.status.isOperating }
        if (live.isEmpty() || flow.isZero) return Money.ZERO
        return if (flow.isPositive) inflow(live, depositor, flow, p) else -outflow(live, depositor, -flow)
    }

    private fun inflow(live: List<Bank>, depositor: Depositor, amount: Money, p: DepositParams): Money {
        var placed = Money.ZERO
        val products = DepositProduct.values()
        val perProduct = BankMath.split(amount, LongArray(products.size) { productShare(depositor, products[it]) })
        for ((i, product) in products.withIndex()) {
            val snaps = live.map { it.snapshot() }
            val weights = LongArray(live.size) { weight(snaps[it], product, p) }
            if (weights.all { it == 0L }) continue
            val shares = BankMath.split(perProduct[i], weights)
            for (j in live.indices) {
                live[j].addDeposits(DepositKey(depositor, product), shares[j])
                live[j].reserves += shares[j]
                placed += shares[j]
            }
        }
        return placed
    }

    private fun outflow(live: List<Bank>, depositor: Depositor, amount: Money): Money {
        val keys = DepositProduct.values().map { DepositKey(depositor, it) }
        val cells = ArrayList<Pair<Bank, DepositKey>>()
        for (b in live) for (k in keys) if (b.depositPool(k).balance.isPositive) cells.add(b to k)
        if (cells.isEmpty()) return Money.ZERO
        val total = Money.sum(cells.map { it.first.depositPool(it.second).balance })
        val take = Money.min(amount, total)
        val shares = BankMath.split(take, LongArray(cells.size) { cells[it].first.depositPool(cells[it].second).balance.raw })
        var removed = Money.ZERO
        for ((i, cell) in cells.withIndex()) {
            val got = cell.first.removeDeposits(cell.second, shares[i])
            cell.first.reserves -= got
            removed += got
        }
        return removed
    }

    /** The deposit beta a bank actually shows: the change in what it pays divided by the change in the policy rate. */
    fun realisedBeta(rateBefore: Rate, rateAfter: Rate, policyBefore: Rate, policyAfter: Rate): Rate? {
        val dPolicy = policyAfter.ppm - policyBefore.ppm
        if (dPolicy == 0L) return null
        return Rate(mulDiv(rateAfter.ppm - rateBefore.ppm, Rate.ONE_PPM, dPolicy))
    }
}
