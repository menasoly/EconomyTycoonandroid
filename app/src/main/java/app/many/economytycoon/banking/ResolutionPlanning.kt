package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.mulDiv

enum class ResolutionTrigger(val label: String) {
    INSOLVENT("Liabilities exceed assets"),
    CAPITAL_COLLAPSE("Capital critically low"),
    LIQUIDITY_FAILURE("Could not meet withdrawals"),
    PANIC_UNRESOLVED("Run not contained"),
    RESTORATION_FAILED("Capital restoration plan failed"),
}

enum class ResolutionTool(val label: String) {
    RECAPITALISE("Bail-in and recapitalisation"),
    MERGER("Merger into a stronger bank"),
    LIQUIDATION("Liquidation and payout"),
}

/** Who is paid in what order when a bank is wound up. */
enum class ClaimRank(val label: String) {
    CENTRAL_BANK("Central bank (secured)"),
    INSURED_DEPOSITS("Insured deposits (guarantee fund)"),
    UNINSURED_DEPOSITS("Uninsured deposits"),
    SENIOR_WHOLESALE("Wholesale and interbank creditors"),
    SUBORDINATED("Subordinated debt"),
    ADDITIONAL_TIER1("Additional tier 1"),
    SHAREHOLDERS("Shareholders"),
}

data class BankClaim(val rank: ClaimRank, val amount: Money)

data class BankDistribution(val rank: ClaimRank, val claim: Money, val paid: Money) {
    val loss: Money get() = claim - paid
    val recovery: Rate get() = if (claim.isZero) Rate.ONE else Rate.ratio(paid.raw, claim.raw)
}

/** Pays creditors from the proceeds in strict order of rank. */
object BankWaterfall {
    fun distribute(proceeds: Money, claims: List<BankClaim>): List<BankDistribution> {
        require(proceeds.raw >= 0L) { "proceeds cannot be negative" }
        var left = proceeds
        val out = ArrayList<BankDistribution>()
        for (rank in ClaimRank.values()) {
            val claim = Money.sum(claims.filter { it.rank == rank }.map { it.amount })
            val paid = Money.min(left, claim)
            left -= paid
            out.add(BankDistribution(rank, claim, paid))
        }
        return out
    }

    /** The claims on a bank: its liabilities by rank, and the shareholders' residual claim if there is one. */
    fun claimsOf(s: BankSnapshot, deposits: DepositParams): List<BankClaim> {
        val covered = GuaranteeScheme.coveredDeposits(s, deposits)
        return listOf(
            BankClaim(ClaimRank.CENTRAL_BANK, s.centralBankDebt),
            BankClaim(ClaimRank.INSURED_DEPOSITS, covered),
            BankClaim(ClaimRank.UNINSURED_DEPOSITS, s.depositsTotal - covered),
            BankClaim(ClaimRank.SENIOR_WHOLESALE, s.wholesale + s.interbankBorrowed),
            BankClaim(ClaimRank.SUBORDINATED, s.tier2),
            BankClaim(ClaimRank.ADDITIONAL_TIER1, s.additionalTier1),
        )
    }
}

/** What the assets of a failing bank are worth under two assumptions. */
data class AssetValuation(
    /** What the loans and securities fetch if the bank keeps going and is sold as a whole. */
    val goingConcern: Money,
    /** What a break-up sale raises. */
    val liquidation: Money,
)

data class ResolutionPlan(
    val bank: BankId,
    val trigger: ResolutionTrigger,
    val tool: ResolutionTool,
    val acquirer: BankId?,
    /** What the plan is expected to cost the guarantee fund. */
    val estimatedFundCost: Money,
    /** New capital needed from the owners or the state (recapitalisation). */
    val injection: Money,
    val rationale: String,
)

/** What a resolution did, for reports and news. */
data class ResolutionOutcome(
    val bank: BankId,
    val tool: ResolutionTool,
    val trigger: ResolutionTrigger,
    val tick: Long,
    val acquirer: BankId?,
    val fundCost: Money,
    val insuredPaid: Money,
    val uninsuredLoss: Money,
    val bailedIn: Money,
    val injection: Money,
    val price: Money,
)

/** Decides when a bank has failed and how to deal with it, at the least cost to the guarantee fund. */
class ResolutionPlanner(private val params: BankingParams) {
    private val p get() = params.resolution

    private fun goingConcernFactor(stage: LoanStage, lgd: Rate): Long = when (stage) {
        LoanStage.PERFORMING -> 970_000L
        LoanStage.WATCH -> 880_000L
        LoanStage.IMPAIRED -> Rate.ONE_PPM - lgd.ppm
    }

    private fun liquidationFactor(stage: LoanStage, lgd: Rate): Long = when (stage) {
        LoanStage.PERFORMING -> 930_000L
        LoanStage.WATCH -> 800_000L
        LoanStage.IMPAIRED -> Rate.ONE_PPM - lgd.ppm
    }

    fun valuation(s: BankSnapshot, collateralIndexPpm: Long): AssetValuation {
        var going = s.reserves.coerceAtLeast(Money.ZERO) + s.securitiesValue() + s.interbankLent + s.fixedAssets
        var sale = s.reserves.coerceAtLeast(Money.ZERO) + s.securitiesValue().times(Rate.ONE - params.runs.fireSaleDiscount) +
            s.interbankLent + s.fixedAssets.times(Rate.percent(50))
        for ((key, pool) in s.loans) {
            val lgd = RiskTables.lgd(key.sector, collateralIndexPpm)
            going += pool.principal.times(Rate(goingConcernFactor(key.stage, lgd)))
            sale += pool.principal.times(Rate(liquidationFactor(key.stage, lgd))).times(p.fireSaleRecovery)
        }
        return AssetValuation(going, sale)
    }

    /** The reason a bank must be resolved now, or null. */
    fun trigger(s: BankSnapshot, capital: CapitalPosition, liquidityFailed: Boolean, monthsInBreach: Int): ResolutionTrigger? = when {
        !s.isOperating -> null
        s.bookEquity.raw <= 0L || s.cet1Capital.raw <= 0L -> ResolutionTrigger.INSOLVENT
        liquidityFailed -> ResolutionTrigger.LIQUIDITY_FAILURE
        capital.cet1Ratio < params.capital.criticalCet1 -> ResolutionTrigger.CAPITAL_COLLAPSE
        s.runStage == RunStage.PANIC && monthsInBreach > 0 -> ResolutionTrigger.PANIC_UNRESOLVED
        capital.rung.isBreach && monthsInBreach >= params.capital.restorationMonths -> ResolutionTrigger.RESTORATION_FAILED
        else -> null
    }

    /** Cost to the fund of liquidating: insured deposits not covered by what the sale raises after the central bank is paid. */
    fun liquidationCost(s: BankSnapshot, collateralIndexPpm: Long): Money {
        val value = valuation(s, collateralIndexPpm)
        val claims = BankWaterfall.claimsOf(s, params.deposits)
        val dist = BankWaterfall.distribute(value.liquidation, claims)
        val insured = dist.first { it.rank == ClaimRank.INSURED_DEPOSITS }
        return insured.loss + s.totalAssets.times(p.resolutionCost)
    }

    /** Support a merger needs: liabilities beyond the going-concern assets once the capital instruments have absorbed losses. */
    fun mergerCost(s: BankSnapshot, collateralIndexPpm: Long): Money {
        val value = valuation(s, collateralIndexPpm)
        val liabilities = s.totalLiabilities - s.additionalTier1 - s.tier2
        return (liabilities - value.goingConcern).coerceAtLeast(Money.ZERO)
    }

    /** Whether a bank is strong enough to take another over: it must keep headroom over its requirement after the deal. */
    fun canAbsorb(acquirer: BankSnapshot, target: BankSnapshot, collateralIndexPpm: Long): Boolean {
        if (!acquirer.isOperating || acquirer.rung != SupervisoryRung.NORMAL || acquirer.runStage != RunStage.CALM) return false
        val before = CapitalEngine.position(acquirer, params.capital)
        val targetRwa = CapitalEngine.rwa(target, params.capital).total
        val rwaAfter = before.rwa.total + targetRwa
        if (rwaAfter.raw <= 0L) return true
        val needed = rwaAfter.times(before.cet1Requirement + p.acquirerHeadroom)
        return before.cet1 >= needed && acquirer.reserves > target.totalAssets.times(Rate.percent(1))
    }

    /** Picks the acquirer: the best capitalised eligible bank, ties by id. */
    fun pickAcquirer(target: BankSnapshot, candidates: List<BankSnapshot>, collateralIndexPpm: Long): BankSnapshot? =
        candidates.filter { it.id != target.id && it.country == target.country && canAbsorb(it, target, collateralIndexPpm) }
            .maxWithOrNull(compareBy<BankSnapshot> { CapitalEngine.position(it, params.capital).cet1Ratio }.thenByDescending { it.id })

    fun plan(
        target: BankSnapshot, trigger: ResolutionTrigger, candidates: List<BankSnapshot>, collateralIndexPpm: Long,
    ): ResolutionPlan {
        val absorbing = target.additionalTier1 + target.tier2
        val value = valuation(target, collateralIndexPpm)
        val liabilitiesSenior = target.totalLiabilities - absorbing
        // Recapitalisation is possible when the capital instruments can soak up the hole and the bank can pay what is due.
        val hole = (liabilitiesSenior - value.goingConcern).coerceAtLeast(Money.ZERO)
        val salvageable = hole <= absorbing && trigger != ResolutionTrigger.LIQUIDITY_FAILURE && trigger != ResolutionTrigger.INSOLVENT
        if (salvageable) {
            val capital = CapitalEngine.position(target, params.capital)
            val goal = capital.rwa.total.times(params.capital.minCet1 + params.capital.conservationBuffer)
            val injection = (goal - target.cet1Capital - absorbing).coerceAtLeast(Money.ZERO)
            return ResolutionPlan(target.id, trigger, ResolutionTool.RECAPITALISE, null, Money.ZERO, injection,
                "Losses fit inside the capital instruments; convert them and ask the owners for fresh capital.")
        }
        val liquidation = liquidationCost(target, collateralIndexPpm)
        val acquirer = pickAcquirer(target, candidates, collateralIndexPpm)
        if (acquirer != null) {
            val merger = mergerCost(target, collateralIndexPpm)
            // A failure that would hit uninsured depositors hard is also avoided when a merger costs no more.
            if (merger <= liquidation) {
                return ResolutionPlan(target.id, trigger, ResolutionTool.MERGER, acquirer.id, merger, Money.ZERO,
                    "A merger into ${acquirer.name} costs the guarantee fund less than a liquidation and protects every depositor.")
            }
        }
        return ResolutionPlan(target.id, trigger, ResolutionTool.LIQUIDATION, null, liquidation, Money.ZERO,
            if (acquirer == null) "No bank is strong enough to take the business over; the bank is wound up and insured depositors are paid."
            else "A merger would cost the guarantee fund more than a payout; the bank is wound up and insured depositors are paid.")
    }
}
