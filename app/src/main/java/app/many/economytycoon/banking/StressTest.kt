package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.mulDiv

/** A set of shocks to put a bank through. Ratios are in ppm; the multiplier and index are 1,000,000 when nothing happens. */
data class StressScenario(
    val id: String,
    val name: String,
    val description: String,
    /** Parallel shift of all rates, signed. */
    val rateShock: Rate,
    /** Multiple of the normal probability of default. */
    val defaultMultiplierPpm: Long,
    /** Collateral prices against their neutral level. */
    val collateralIndexPpm: Long,
    /** Share of deposits that leaves in the first quarter, uninsured deposits leaving faster than insured ones. */
    val depositOutflow: Rate,
    /** Share of wholesale funding that cannot be rolled over. */
    val wholesaleFreeze: Rate,
) {
    init {
        require(id.isNotBlank()) { "a scenario needs an id" }
        require(defaultMultiplierPpm >= Rate.ONE_PPM / 2L) { "default multiplier too low" }
        require(collateralIndexPpm in 100_000L..2_000_000L) { "collateral index out of range" }
    }

    companion object {
        val BASELINE = StressScenario("baseline", "Baseline", "Conditions carry on as they are.", Rate.ZERO, Rate.ONE_PPM, Rate.ONE_PPM, Rate.ZERO, Rate.ZERO)
        val ADVERSE = StressScenario("adverse", "Adverse recession", "A mild recession: more defaults, softer collateral and a little deposit flight.", Rate.basisPoints(100), 1_800_000L, 850_000L, Rate.percent(3), Rate.percent(10))
        val SEVERE = StressScenario("severe", "Severe depression", "Output collapses: defaults triple, collateral loses a third of its value and funding dries up.", Rate.basisPoints(300), 3_000_000L, 650_000L, Rate.percent(8), Rate.percent(50))
        val STAGFLATION = StressScenario("stagflation", "Stagflation", "Rates jump to fight inflation while the economy stalls.", Rate.basisPoints(400), 2_200_000L, 900_000L, Rate.percent(4), Rate.percent(20))
        val PROPERTY_CRASH = StressScenario("property", "Property crash", "Property prices fall by almost half.", Rate.ZERO, 2_000_000L, 550_000L, Rate.percent(2), Rate.percent(10))
        val FUNDING_FREEZE = StressScenario("funding", "Funding freeze", "Markets stop lending and depositors take fright.", Rate.basisPoints(100), 1_300_000L, 950_000L, Rate.percent(12), Rate.percent(100))
        val RATES_DOWN = StressScenario("rates-down", "Rates collapse", "Policy rates are cut towards zero, squeezing margins.", Rate.basisPoints(-300), 1_100_000L, 1_050_000L, Rate.ZERO, Rate.ZERO)

        val ALL: List<StressScenario> = listOf(BASELINE, ADVERSE, SEVERE, STAGFLATION, PROPERTY_CRASH, FUNDING_FREEZE, RATES_DOWN)

        fun byId(id: String): StressScenario? = ALL.firstOrNull { it.id == id }
    }
}

/** One quarter of the projection. */
data class StressStep(
    val month: Int,
    val cet1: Money,
    val rwa: Money,
    val netIncome: Money,
    val creditLoss: Money,
) {
    val cet1Ratio: Rate get() = BankMath.ratio(cet1, rwa, if (cet1.isPositive) Rate.ONE else Rate.ZERO)
}

/** Where the capital went, over the whole horizon. */
data class StressLosses(
    val credit: Money,
    val securities: Money,
    val interestMargin: Money,
    val funding: Money,
    val operating: Money,
) {
    val total: Money get() = credit + securities + funding - interestMargin
}

data class BankStressResult(
    val bank: BankId,
    val name: String,
    val scenario: StressScenario,
    val startCet1Ratio: Rate,
    val endCet1Ratio: Rate,
    val minCet1Ratio: Rate,
    val troughMonth: Int,
    /** Capital needed to bring the trough back up to the hurdle. */
    val capitalShortfall: Money,
    val stressedLcr: Rate,
    val passed: Boolean,
    val losses: StressLosses,
    val steps: List<StressStep>,
) {
    /** Points of CET1 ratio lost between the start and the trough. */
    val drawdown: Rate get() = startCet1Ratio - minCet1Ratio
}

data class StressReport(
    val tick: Long,
    val scenario: StressScenario,
    val hurdle: Rate,
    val results: List<BankStressResult>,
) {
    val failures: List<BankStressResult> get() = results.filter { !it.passed }
    val systemShortfall: Money get() = Money.sum(results.map { it.capitalShortfall })
    val worst: BankStressResult? get() = results.minByOrNull { it.minCet1Ratio }
    val passRate: Rate get() = if (results.isEmpty()) Rate.ONE else Rate.ratio(results.count { it.passed }.toLong(), results.size.toLong())
}

/**
 * Projects a bank through a scenario on a static balance sheet, with no management actions and no dividends: the
 * usual supervisory stress test. The projection is pure; it reads a snapshot and changes nothing.
 */
class StressTester(private val params: BankingParams) {
    private val rampMonths = 6

    /** The monthly net interest income and running cost a bank would earn on today's books, before any shock. */
    private fun monthlyBase(s: BankSnapshot, policy: Rate): Pair<Money, Money> {
        var income = Money.ZERO
        for ((key, pool) in s.loans) if (key.stage != LoanStage.IMPAIRED) income += BankMath.monthlyInterest(pool.principal, pool.ratePpm)
        for (h in s.securities.values) income += BankMath.monthlyInterest(h.value, h.yieldPpm)
        val corridor = CentralBankFacilities.corridor(policy, params.facilities)
        val required = LiquidityEngine.requiredReserves(s, params.liquidity)
        income += BankMath.monthlyInterest((s.reserves - required).coerceAtLeast(Money.ZERO), corridor.floor.ppm)
        var cost = Money.ZERO
        for (pool in s.deposits.values) cost += BankMath.monthlyInterest(pool.balance, pool.ratePpm)
        cost += BankMath.monthlyInterest(s.wholesale, s.wholesaleRatePpm)
        cost += BankMath.monthlyInterest(s.additionalTier1 + s.tier2, policy.ppm + 300_000L / 10L)
        cost += CentralBankFacilities.monthlyInterest(s, corridor)
        val operating = BankMath.monthlyInterest(s.totalAssets, params.lending.opexRate.ppm)
        var fees = Money.ZERO
        for ((key, pool) in s.deposits) if (key.product == DepositProduct.DEMAND) fees += BankMath.monthlyInterest(pool.balance, params.deposits.demandFee.ppm)
        return (income - cost + fees) to operating
    }

    private fun securitiesLoss(s: BankSnapshot, shock: Rate): Money {
        if (shock.ppm <= 0L) return Money.ZERO
        var loss = Money.ZERO
        for (h in s.securities.values) loss += Money(mulDiv(h.value.raw, shock.ppm * h.durationMonths, Rate.ONE_PPM * 12L))
        return loss
    }

    fun run(s: BankSnapshot, scenario: StressScenario, policy: Rate, collateralNow: Long = Rate.ONE_PPM): BankStressResult {
        val capital0 = CapitalEngine.position(s, params.capital)
        val horizon = params.stress.horizonMonths
        val multiplier = { _: LoanSector -> scenario.defaultMultiplierPpm }
        val eclNow = s.allowance
        val eclStress = Provisioning.ecl(s, multiplier, scenario.collateralIndexPpm).total
        val annualStressLoss = Provisioning.annualExpectedLoss(s, multiplier, scenario.collateralIndexPpm)
        val (nimBase, opexMonthly) = monthlyBase(s, policy)
        val rateDelta = MarginAnalytics.annualNiiChange(s, scenario.rateShock, params.deposits)
        val mtm = securitiesLoss(s, scenario.rateShock)

        // Deposit flight: lost deposits are replaced at the top of the corridor, after reserves above the requirement are used.
        val corridor = CentralBankFacilities.corridor(Rate((policy.ppm + scenario.rateShock.ppm).coerceAtLeast(0L)), params.facilities)
        val insuredShare = { d: Depositor -> params.deposits.insuredShare(d) }
        var outflow = Money.ZERO
        for ((key, pool) in s.deposits) {
            val uninsured = (Rate.ONE_PPM - insuredShare(key.depositor).ppm)
            val weight = Rate.ONE_PPM / 4L + uninsured * 3L / 4L
            outflow += pool.balance.times(scenario.depositOutflow).times(Rate(weight.coerceIn(0L, Rate.ONE_PPM)))
        }
        val frozen = s.wholesale.times(scenario.wholesaleFreeze)
        val gap = outflow + frozen
        val required = LiquidityEngine.requiredReserves(s, params.liquidity)
        val usableReserves = (s.reserves - required).coerceAtLeast(Money.ZERO)
        val fromReserves = Money.min(gap, usableReserves)
        val borrowedNeed = gap - fromReserves
        val securitiesAvailable = s.securitiesValue()
        val sold = Money.min(borrowedNeed, securitiesAvailable.times(Rate.percent(40)))
        val fireSaleLoss = sold.times(params.runs.fireSaleDiscount)
        val borrowed = borrowedNeed - sold
        val fundingMonthly = BankMath.monthlyInterest(borrowed, corridor.emergency.ppm) + BankMath.monthlyInterest(sold + fromReserves, corridor.ceiling.ppm / 4L)
        val fundingCost = Money(fundingMonthly.raw * horizon.toLong())

        // Liquidity after the first quarter.
        val hqlaAfter = (LiquidityEngine.position(s, params.liquidity, insuredShare).hqla - fromReserves - sold - borrowed).coerceAtLeast(Money.ZERO)
        val netOut = LiquidityEngine.position(s, params.liquidity, insuredShare).netOutflows
        val stressedLcr = if (netOut.raw <= 0L) LiquidityEngine.UNBOUNDED else BankMath.ratio(hqlaAfter, netOut).coerceIn(Rate.ZERO, LiquidityEngine.UNBOUNDED)

        val steps = ArrayList<StressStep>()
        var cet1 = capital0.cet1 - mtm - fireSaleLoss
        var taxCarry = Money.ZERO
        var creditTotal = Money.ZERO
        var marginTotal = Money.ZERO
        var operatingTotal = Money.ZERO
        var previousEcl = eclNow
        val rwa0 = capital0.rwa.total
        steps.add(StressStep(0, cet1, rwa0, -(mtm + fireSaleLoss), Money.ZERO))
        var minRatio = steps[0].cet1Ratio
        var trough = 0
        var month = 0
        while (month < horizon) {
            month += 3
            val progress = minOf(month, rampMonths).toLong()
            val eclTarget = eclNow + Money(mulDiv((eclStress - eclNow).raw, progress, rampMonths.toLong()))
            var creditCharge = eclTarget - previousEcl
            previousEcl = eclTarget
            if (month > 12) creditCharge += Money(annualStressLoss.raw / 4L)
            val margin = Money(nimBase.raw * 3L) + Money(rateDelta.raw / 4L)
            val operating = Money(opexMonthly.raw * 3L)
            val funding = if (month <= 3) fundingCost else Money.ZERO
            val preTax = margin - operating - creditCharge - funding
            val tax = if (preTax.raw > 0L) {
                val taxable = (preTax - taxCarry).coerceAtLeast(Money.ZERO)
                taxCarry = (taxCarry - preTax).coerceAtLeast(Money.ZERO)
                taxable.times(params.lending.taxRate)
            } else { taxCarry += -preTax; Money.ZERO }
            val net = preTax - tax
            cet1 += net
            creditTotal += creditCharge
            marginTotal += margin - Money(nimBase.raw * 3L)
            operatingTotal += operating
            val inflate = Rate(mulDiv(minOf(150_000L * (scenario.defaultMultiplierPpm - Rate.ONE_PPM).coerceAtLeast(0L) / Rate.ONE_PPM, 250_000L), progress, rampMonths.toLong()))
            val rwa = rwa0 + rwa0.times(inflate)
            val step = StressStep(month, cet1, rwa, net, creditCharge)
            steps.add(step)
            if (step.cet1Ratio < minRatio) { minRatio = step.cet1Ratio; trough = month }
        }
        val troughStep = steps.first { it.month == trough }
        val target = troughStep.rwa.times(params.stress.hurdle)
        val shortfall = (target - troughStep.cet1).coerceAtLeast(Money.ZERO)
        val last = steps.last()
        val losses = StressLosses(creditTotal.coerceAtLeast(Money.ZERO), mtm + fireSaleLoss, marginTotal, fundingCost, operatingTotal)
        return BankStressResult(
            s.id, s.name, scenario, capital0.cet1Ratio, last.cet1Ratio, minRatio, trough, shortfall, stressedLcr,
            minRatio >= params.stress.hurdle && stressedLcr >= Rate.percent(50), losses, steps,
        )
    }

    fun report(tick: Long, snapshots: List<BankSnapshot>, scenario: StressScenario, policy: (BankSnapshot) -> Rate): StressReport {
        val results = snapshots.filter { it.isOperating }.sortedBy { it.id }.map { run(it, scenario, policy(it)) }
        return StressReport(tick, scenario, params.stress.hurdle, results)
    }
}
