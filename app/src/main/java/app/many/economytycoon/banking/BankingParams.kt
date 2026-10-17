package app.many.economytycoon.banking

import app.many.economytycoon.core.Rate
import app.many.economytycoon.finance.CreditRating

/**
 * Capital rules, in the shape of the Basel framework. Ratios are of risk-weighted assets unless the name says
 * otherwise. All values are parts per million.
 */
data class CapitalRules(
    val minCet1: Rate = Rate.basisPoints(450),
    val minTier1: Rate = Rate.percent(6),
    val minTotal: Rate = Rate.percent(8),
    val conservationBuffer: Rate = Rate.basisPoints(250),
    /** Set by the supervisor with the credit cycle; zero when credit is not running hot. */
    val countercyclicalBuffer: Rate = Rate.ZERO,
    /** Extra buffer for banks holding more than [systemicAssets] of assets. */
    val systemicBuffer: Rate = Rate.percent(1),
    val systemicAssetsUnits: Long = 50_000_000L,
    /** Tier 1 capital over total exposure, not risk weighted. */
    val minLeverage: Rate = Rate.percent(3),
    /** Below this CET1 ratio a bank is severely undercapitalised. */
    val severeCet1: Rate = Rate.percent(3),
    /** Below this CET1 ratio, or with negative tangible equity, a bank is critically undercapitalised. */
    val criticalCet1: Rate = Rate.basisPoints(150),
    /** Months a bank must stay clear of a rung before the supervisor lets it back down one. */
    val releaseMonths: Int = 2,
    /** Operational risk charge as a share of the last twelve months' gross income, already scaled to risk weight. */
    val operationalRiskWeight: Rate = Rate.percent(188),
    /** Months of the capital restoration plan the supervisor allows before escalating. */
    val restorationMonths: Int = 6,
) {
    init {
        require(minCet1 <= minTier1 && minTier1 <= minTotal) { "capital minimums must be ordered" }
        require(criticalCet1 < severeCet1 && severeCet1 < minCet1) { "ladder thresholds must be ordered" }
        require(releaseMonths >= 1 && restorationMonths >= 1) { "months must be positive" }
    }
}

/** Liquidity rules: coverage ratio, stable funding, reserve requirement and run-off assumptions. */
data class LiquidityRules(
    val minLcr: Rate = Rate.percent(100),
    val minNsfr: Rate = Rate.percent(100),
    /** Reserve requirement on current accounts and on other deposits. */
    val reserveRatioDemand: Rate = Rate.percent(3),
    val reserveRatioOther: Rate = Rate.percent(1),
    /** Haircut applied to level 2 assets (corporate bonds) when counted as liquid. */
    val level2Haircut: Rate = Rate.percent(15),
    /** Level 2 assets may be at most this share of the liquid buffer. */
    val level2Cap: Rate = Rate.percent(40),
    /** Share of wholesale funding that falls due within thirty days. */
    val wholesaleMaturingShare: Rate = Rate.percent(10),
    /** Share of performing loans that pay back within thirty days. */
    val loanInflowShare: Rate = Rate.percent(4),
    /** Inflows count for at most this share of outflows. */
    val inflowCap: Rate = Rate.percent(75),
    /** Weight of loan inflows. */
    val inflowFactor: Rate = Rate.percent(50),
    /** Share of reservable deposits a bank keeps above the requirement as a working margin. */
    val reserveMargin: Rate = Rate.percent(20),
) {
    /** Thirty-day run-off assumed for a book of deposits. */
    fun runoff(depositor: Depositor, product: DepositProduct, insured: Boolean): Rate = when (depositor) {
        Depositor.HOUSEHOLD -> when (product) {
            DepositProduct.DEMAND -> if (insured) Rate.percent(3) else Rate.percent(10)
            DepositProduct.SAVINGS -> if (insured) Rate.percent(5) else Rate.percent(10)
            DepositProduct.TERM -> if (insured) Rate.percent(2) else Rate.percent(6)
        }
        Depositor.CORPORATE -> when (product) {
            DepositProduct.DEMAND -> if (insured) Rate.percent(5) else Rate.percent(25)
            DepositProduct.SAVINGS -> if (insured) Rate.percent(10) else Rate.percent(40)
            DepositProduct.TERM -> if (insured) Rate.percent(5) else Rate.percent(20)
        }
    }

    /** Share of a book that counts as stable funding. */
    fun stableFactor(depositor: Depositor, product: DepositProduct): Rate = when (depositor) {
        Depositor.HOUSEHOLD -> when (product) {
            DepositProduct.DEMAND -> Rate.percent(90)
            DepositProduct.SAVINGS -> Rate.percent(90)
            DepositProduct.TERM -> Rate.percent(100)
        }
        Depositor.CORPORATE -> when (product) {
            DepositProduct.DEMAND -> Rate.percent(50)
            DepositProduct.SAVINGS -> Rate.percent(50)
            DepositProduct.TERM -> Rate.percent(70)
        }
    }
}

/** How deposit rates follow the policy rate and how customers move between banks. */
data class DepositParams(
    /** Share of a policy rate change passed through to each product (the deposit beta). */
    val betaDemand: Rate = Rate.percent(8),
    val betaSavings: Rate = Rate.percent(45),
    val betaTerm: Rate = Rate.percent(70),
    /** Companies shop around more than households do. */
    val corporateBetaBonus: Rate = Rate.percent(15),
    /** What a product pays below the beta-scaled policy rate when no bank competes on price. */
    val discountDemand: Rate = Rate.ZERO,
    val discountSavings: Rate = Rate.basisPoints(40),
    val discountTerm: Rate = Rate.basisPoints(10),
    /** Share of a book that is up for grabs each month. */
    val churnDemand: Rate = Rate.percent(1),
    val churnSavings: Rate = Rate.percent(6),
    val churnTerm: Rate = Rate.percent(2),
    /** Extra pull of a bank per basis point of rate premium, in points of attractiveness (1,000 = neutral weight). */
    val pullPerBasisPoint: Long = 25L,
    val neutralWeight: Long = 1_000L,
    val minWeight: Long = 50L,
    /** Share of insured deposits by depositor kind. */
    val insuredHousehold: Rate = Rate.percent(88),
    val insuredCorporate: Rate = Rate.percent(30),
    /** Largest premium a bank may pay over the market, in basis points. */
    val maxPremiumBps: Long = 300L,
    /** Annual account fee on current accounts, share of balance. */
    val demandFee: Rate = Rate.basisPoints(25),
) {
    fun beta(product: DepositProduct): Rate = when (product) {
        DepositProduct.DEMAND -> betaDemand
        DepositProduct.SAVINGS -> betaSavings
        DepositProduct.TERM -> betaTerm
    }

    fun discount(product: DepositProduct): Rate = when (product) {
        DepositProduct.DEMAND -> discountDemand
        DepositProduct.SAVINGS -> discountSavings
        DepositProduct.TERM -> discountTerm
    }

    fun churn(product: DepositProduct): Rate = when (product) {
        DepositProduct.DEMAND -> churnDemand
        DepositProduct.SAVINGS -> churnSavings
        DepositProduct.TERM -> churnTerm
    }

    fun insuredShare(depositor: Depositor): Rate = if (depositor == Depositor.HOUSEHOLD) insuredHousehold else insuredCorporate
}

/** Loan pricing, underwriting and the economics of lending. */
data class LendingParams(
    /** Extra rate per year of average life, in ppm. */
    val termPremiumPerYear: Rate = Rate.basisPoints(25),
    val maxTermPremium: Rate = Rate.basisPoints(150),
    /** Liquidity premium per point of liquidity coverage short of the target, and for each point of wholesale reliance. */
    val liquidityPremiumPerShortfall: Rate = Rate.basisPoints(60),
    val liquidityPremiumPerWholesale: Rate = Rate.basisPoints(40),
    val maxLiquidityPremium: Rate = Rate.basisPoints(200),
    val originationFee: Rate = Rate.basisPoints(40),
    /** Operating cost per year as a share of assets. */
    val opexRate: Rate = Rate.basisPoints(290),
    val taxRate: Rate = Rate.percent(22),
    /** Return on allocated capital a loan must earn to be worth making. */
    val hurdleReturn: Rate = Rate.percent(9),
    /** Capital a loan ties up per unit of risk-weighted exposure when judging returns. */
    val allocatedCapitalRatio: Rate = Rate.percent(11),
    /** Share of new loans that leaves the bank as cash or payments to others. */
    val leakage: Rate = Rate.percent(12),
    /** How strongly borrowers favour a cheaper bank, in points of weight per basis point cheaper. */
    val pullPerBasisPoint: Long = 20L,
    val neutralWeight: Long = 1_000L,
    val minWeight: Long = 25L,
    /** Share of a bank's lending capacity it will commit in one month. */
    val monthlyGrowthCap: Rate = Rate.percent(4),
    /** Largest share of a bank's CET1 capital that one sector's expected loss may be allowed to eat in a year. */
    val maxLossToCapital: Rate = Rate.percent(30),
    /** Smallest block a lender will bother with. */
    val minTicketUnits: Long = 100L,
    /** Noise on realised migrations, as a share of the expected figure. */
    val migrationNoise: Rate = Rate.percent(12),
)

/** Bank runs: how confidence moves and how much leaves when it breaks. */
data class RunParams(
    val calmThreshold: Rate = Rate.percent(70),
    val runThreshold: Rate = Rate.percent(45),
    val panicThreshold: Rate = Rate.percent(25),
    /** Margin above a threshold needed before the stage improves again. */
    val hysteresis: Rate = Rate.percent(5),
    /** Share of the gap to the target that confidence closes per day when falling and when recovering. */
    val fallSpeed: Rate = Rate.percent(40),
    val recoverSpeed: Rate = Rate.percent(4),
    val rumourDecay: Rate = Rate.percent(12),
    val supportDecay: Rate = Rate.percent(6),
    /** Confidence cost of each point by which CET1 falls short of the requirement plus buffer. */
    val capitalWeight: Long = 10_000_000L,
    val lcrWeight: Long = 900_000L,
    val lossWeight: Long = 4_000_000L,
    val ratingPenaltyPerNotch: Rate = Rate.percent(4),
    val systemicWeight: Rate = Rate.percent(30),
    /** Share of uninsured and insured deposits that leave on the worst day. */
    val maxDailyUninsured: Rate = Rate.percent(15),
    val maxDailyInsured: Rate = Rate.percent(1),
    /** Confidence support from a funded guarantee scheme at full coverage. */
    val guaranteeSupport: Rate = Rate.percent(8),
    /** Fire-sale discount on securities sold into a run. */
    val fireSaleDiscount: Rate = Rate.percent(4),
    /** Days of panic after which the supervisor steps in unless liquidity is secured. */
    val panicDaysToIntervene: Int = 5,
)

/** Central bank facilities. */
data class FacilityParams(
    /** Half-width of the rate corridor around the policy rate. */
    val corridorHalfWidth: Rate = Rate.basisPoints(50),
    /** Extra on top of the ceiling for emergency liquidity assistance. */
    val emergencyPenalty: Rate = Rate.basisPoints(150),
    val haircutGovernmentShort: Rate = Rate.percent(2),
    val haircutGovernmentLong: Rate = Rate.percent(8),
    val haircutCorporate: Rate = Rate.percent(25),
    /** Haircut on pledged performing loans. */
    val haircutLoans: Rate = Rate.percent(35),
    /** Share of pledgeable performing loans the central bank accepts. */
    val loanCollateralShare: Rate = Rate.percent(60),
    /** Emergency assistance is only lent to banks whose CET1 ratio is above this. */
    val emergencyMinCet1: Rate = Rate.percent(2),
    /** Counterparties below this rating are not lent to on the interbank market. */
    val interbankMinRating: CreditRating = CreditRating.BB,
    /** Share of own CET1 capital a lender may have out to one borrower, here simplified to the whole market. */
    val interbankLimitToCapital: Rate = Rate.percent(150),
)

/** The guarantee scheme, resolution and the supervisor's powers. */
data class ResolutionParams(
    /** Fund target as a share of covered deposits. */
    val fundTarget: Rate = Rate.percent(1),
    /** Annual levy as a share of covered deposits, before the risk factor. */
    val levyRate: Rate = Rate.basisPoints(12),
    /** Largest extra levy in one year as a share of covered deposits. */
    val extraordinaryCap: Rate = Rate.basisPoints(50),
    /** Days between levies. */
    val levyEvery: Int = 90,
    /** Share of a failed bank's good loans an acquirer pays for, in a purchase-and-assumption deal. */
    val purchasePriceShare: Rate = Rate.percent(92),
    /** Smallest CET1 headroom, in points above requirement plus buffer, an acquirer must keep after the deal. */
    val acquirerHeadroom: Rate = Rate.percent(1),
    /** Months a bank stays in supervision after intervention before it is released if it recovered. */
    val supervisionMonths: Int = 6,
    /** Fraction of the asset book recovered on liquidation, before sector losses. */
    val fireSaleRecovery: Rate = Rate.percent(85),
    /** Cost of running a resolution, share of assets. */
    val resolutionCost: Rate = Rate.basisPoints(30),
    /** Risk factor on levies by rating: AAA..CCC, in ppm of the base. */
    val riskFactorBest: Rate = Rate.percent(60),
    val riskFactorWorst: Rate = Rate.percent(200),
)

/** Stress testing. */
data class StressParams(
    val horizonMonths: Int = 12,
    /** Hurdle CET1 ratio a bank must stay above in the scenario to pass. */
    val hurdle: Rate = Rate.percent(5),
    /** Months between supervisory stress tests. */
    val everyMonths: Int = 12,
) {
    init { require(horizonMonths in 3..60 && horizonMonths % 3 == 0) { "horizon must be a multiple of three months, at most five years" } }
}

/** Bank rating weights, thresholds and hysteresis. */
data class RatingParams(
    val confirmations: Int = 2,
    val base: Int = 500,
)

/** Everything tunable about the banking sector. */
data class BankingParams(
    val capital: CapitalRules = CapitalRules(),
    val liquidity: LiquidityRules = LiquidityRules(),
    val deposits: DepositParams = DepositParams(),
    val lending: LendingParams = LendingParams(),
    val runs: RunParams = RunParams(),
    val facilities: FacilityParams = FacilityParams(),
    val resolution: ResolutionParams = ResolutionParams(),
    val stress: StressParams = StressParams(),
    val rating: RatingParams = RatingParams(),
    /** Banks a country is expected to have before the model warns about concentration. */
    val maxBanksPerCountry: Int = 12,
    val maxPlayerBanks: Int = 3,
    val minFoundingCapitalUnits: Long = 500_000L,
    /** Discount on securities sold in an orderly way, as opposed to a fire sale. */
    val saleDiscount: Rate = Rate.basisPoints(10),
    /** Confidence bought per unit of assets spent on reassurance, in ppm of confidence per ppm of assets. */
    val reassuranceEfficiency: Long = 50L,
    val maxSupportPpm: Long = 250_000L,
    /** Share of the capital instruments a bank may have outstanding, as a share of risk-weighted assets. */
    val maxInstrumentShare: Rate = Rate.percent(3),
    /** Months of capital the AI may raise at once, as a share of what it needs. */
    val wholesaleSpread: Rate = Rate.basisPoints(30),
    val historyMonths: Int = 24,
    val eventLog: Int = 80,
)
