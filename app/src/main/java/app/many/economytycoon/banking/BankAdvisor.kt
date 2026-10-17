package app.many.economytycoon.banking

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.finance.CreditRating

enum class BankTopic(val label: String) {
    CAPITAL("Capital"),
    LIQUIDITY("Liquidity"),
    FUNDING("Funding"),
    LENDING("Lending"),
    EARNINGS("Earnings"),
    RISK("Risk"),
    SUPERVISION("Supervision"),
}

/** One suggestion for the manager of a bank. [priority] runs from 0 to 100; [suggestion] is an action that would help, if there is one. */
data class BankInsight(
    val code: String,
    val topic: BankTopic,
    val priority: Int,
    val title: String,
    val detail: String,
    val suggestion: BankAction? = null,
) {
    init { require(priority in 0..100) { "priority out of range" } }
}

/** What the advisor knows beyond the bank's books. */
data class AdvisorContext(
    val tick: Long = 0L,
    /** The latest stress test of this bank, if any, and when it ran. */
    val stress: BankStressResult? = null,
    val stressTick: Long? = null,
    /** How much of the covered deposits the guarantee fund holds. */
    val fundCoverage: Rate = Rate.ONE,
    val stressEveryTicks: Long = 360L,
)

fun interface BankAdvisorRule {
    fun check(v: BankView, c: AdvisorContext): List<BankInsight>
}

object BankAdvisorRules {
    private fun one(i: BankInsight?): List<BankInsight> = if (i == null) emptyList() else listOf(i)
    private fun pct(r: Rate): String = "${r.ppm / 10_000L}.${(r.ppm / 1_000L) % 10L}%"

    val supervised = BankAdvisorRule { v, _ ->
        one(if (v.bank.status == BankStatus.SUPERVISED) BankInsight("supervised", BankTopic.SUPERVISION, 90, "The supervisor has taken control",
            "New lending and distributions are restricted until capital and confidence recover. Rebuild capital and keep funding stable to be released.") else null)
    }

    val capitalBreach = BankAdvisorRule { v, _ ->
        one(if (v.capital.rung.isBreach) BankInsight("capital-breach", BankTopic.CAPITAL, 95, "Capital is below the regulatory minimum",
            "CET1 is ${pct(v.capital.cet1Ratio)} against a minimum of ${pct(v.params.capital.minCet1)}. Raise capital, stop dividends and shrink risk-weighted assets before the supervisor steps in.",
            BankAction.RaiseCapital(v.bank.id, CapitalInstrument.COMMON_EQUITY, CapitalEngine.capitalNeeded(v.bank, v.params.capital, v.bank.policy.capitalCushion))) else null)
    }

    val capitalBuffer = BankAdvisorRule { v, _ ->
        one(if (v.capital.rung == SupervisoryRung.BUFFER) BankInsight("capital-buffer", BankTopic.CAPITAL, 75, "Inside the capital buffer",
            "CET1 of ${pct(v.capital.cet1Ratio)} is below ${pct(v.capital.cet1Requirement)}. Dividends are limited to ${pct(v.capital.distributionLimit)} of profit until the buffer is rebuilt.") else null)
    }

    val thinCushion = BankAdvisorRule { v, _ ->
        val thin = v.capital.rung == SupervisoryRung.NORMAL && v.cushionHeadroom.ppm < 0L
        one(if (thin) BankInsight("thin-cushion", BankTopic.CAPITAL, 55, "Capital is thinner than your own target",
            "Headroom over the requirement is ${pct(v.capital.headroom)} but you aim for ${pct(v.bank.policy.capitalCushion)}. Retaining profit or slowing growth rebuilds it.",
            BankAction.SetCapitalPolicy(v.bank.id, v.bank.policy.capitalCushion, Rate(v.bank.policy.dividendPayout.ppm / 2L))) else null)
    }

    val idleCapital = BankAdvisorRule { v, _ ->
        val rich = v.capital.rung == SupervisoryRung.NORMAL && v.cushionHeadroom.ppm > 60_000L && v.bank.policy.growthAppetite.ppm < 600_000L
        one(if (rich) BankInsight("idle-capital", BankTopic.CAPITAL, 35, "Capital beyond what you need",
            "You hold ${pct(v.cushionHeadroom)} more than your target. Lending more, or paying a larger dividend, would raise the return on equity.",
            BankAction.SetGrowthAppetite(v.bank.id, Rate(minOf(v.bank.policy.growthAppetite.ppm + 200_000L, Rate.ONE_PPM)))) else null)
    }

    val lowLiquidity = BankAdvisorRule { v, _ ->
        val lcr = v.liquidity.lcr
        when {
            lcr < v.params.liquidity.minLcr -> listOf(BankInsight("lcr-breach", BankTopic.LIQUIDITY, 90, "Liquidity coverage is below 100%",
                "The liquid buffer covers ${pct(lcr)} of thirty-day net outflows. Hold more short government debt or stop lending until it is restored.",
                BankAction.SetFundingPolicy(v.bank.id, Rate(v.bank.policy.securitiesShare.ppm + 40_000L), v.bank.policy.wholesaleCap, v.bank.policy.targetLcr)))
            lcr < v.bank.policy.targetLcr -> listOf(BankInsight("lcr-low", BankTopic.LIQUIDITY, 60, "Liquidity coverage is under your target",
                "Coverage is ${pct(lcr)} against a target of ${pct(v.bank.policy.targetLcr)}. Raising deposit rates or holding more liquid assets would help."))
            else -> emptyList()
        }
    }

    val reserveShortfall = BankAdvisorRule { v, _ ->
        one(if (!v.liquidity.meetsReserve) BankInsight("reserve-shortfall", BankTopic.LIQUIDITY, 85, "Reserves are below the legal requirement",
            "You are ${v.liquidity.reserveShortfall.format()} short. Borrow overnight, sell securities or attract deposits.",
            BankAction.RequestEmergencyLiquidity(v.bank.id, v.liquidity.reserveShortfall)) else null)
    }

    val runRisk = BankAdvisorRule { v, _ ->
        when (v.bank.runStage) {
            RunStage.PANIC, RunStage.RUN -> listOf(BankInsight("run", BankTopic.RISK, 98, "Depositors are running",
                "Confidence has fallen to ${pct(Rate(v.bank.confidencePpm))}. Reassure the public, secure liquidity from the central bank and pay more for deposits.",
                BankAction.ReassureDepositors(v.bank.id, v.bank.totalAssets.times(Rate.basisPoints(3)))))
            RunStage.WATCHFUL -> listOf(BankInsight("run-watch", BankTopic.RISK, 70, "Depositor confidence is slipping",
                "Confidence is ${pct(Rate(v.bank.confidencePpm))}. Fix what is worrying them before it becomes a run: capital, liquidity or losses."))
            RunStage.CALM -> emptyList()
        }
    }

    val uninsuredFunding = BankAdvisorRule { v, _ ->
        var uninsured = Money.ZERO
        for ((key, pool) in v.bank.deposits) uninsured += pool.balance - pool.balance.times(v.params.deposits.insuredShare(key.depositor))
        val share = BankMath.ratio(uninsured, v.bank.depositsTotal)
        one(if (share.ppm > 550_000L) BankInsight("uninsured-funding", BankTopic.FUNDING, 50, "Most deposits are uninsured",
            "${pct(share)} of deposits are not covered by the guarantee, and they leave first in a scare. Term deposits and household accounts are steadier.") else null)
    }

    val wholesaleDependence = BankAdvisorRule { v, _ ->
        one(if (v.bank.wholesaleShare.ppm > 250_000L) BankInsight("wholesale-dependence", BankTopic.FUNDING, 55, "Too much wholesale funding",
            "${pct(v.bank.wholesaleShare)} of funding comes from markets that can close. Grow stable deposits.") else null)
    }

    val marginSqueeze = BankAdvisorRule { v, _ ->
        val m = v.margin
        val enough = v.bank.history.size >= 3
        one(if (enough && m.trailingMargin.ppm < 15_000L) BankInsight("nim-squeeze", BankTopic.EARNINGS, 60, "Net interest margin is thin",
            "The margin is ${pct(m.trailingMargin)}. Widen loan margins or pay less for deposits.",
            BankAction.SetLoanMargin(v.bank.id, v.bank.policy.loanMarginBps + 15L)) else null)
    }

    val heavyCosts = BankAdvisorRule { v, _ ->
        val last = v.bank.lastResult
        one(if (last != null && last.costToIncome.ppm > 700_000L) BankInsight("cost-income", BankTopic.EARNINGS, 40, "Costs eat most of the income",
            "Operating costs are ${pct(last.costToIncome)} of income. Growth in lending spreads fixed costs.") else null)
    }

    val badLoans = BankAdvisorRule { v, _ ->
        val npl = v.bank.nplRatio
        val out = ArrayList<BankInsight>()
        if (npl.ppm > 60_000L) out += BankInsight("npl-high", BankTopic.RISK, 80, "Many loans have gone bad",
            "${pct(npl)} of the book is impaired. Tighten standards in the worst sectors and build provisions.",
            BankAction.SetLendingStandards(v.bank.id, v.bank.policy.minRating.up(), v.bank.policy.maxSectorShare))
        if (npl.ppm > 30_000L && v.bank.coverageRatio.ppm < 600_000L) out += BankInsight("coverage-low", BankTopic.RISK, 65, "Provisions do not cover the bad loans",
            "The allowance is ${pct(v.bank.coverageRatio)} of impaired loans. Losses still to come will hit capital.")
        out
    }

    val concentration = BankAdvisorRule { v, _ ->
        val over = LoanSector.values().filter { v.bank.sectorShare(it) > v.bank.policy.maxSectorShare }
        over.map { BankInsight("concentration-${it.name.lowercase()}", BankTopic.LENDING, 50, "${it.label} are over the sector limit",
            "They make up ${pct(v.bank.sectorShare(it))} of loans against a limit of ${pct(v.bank.policy.maxSectorShare)}. One bad year in the sector would hurt badly.",
            BankAction.SetSectorAppetite(v.bank.id, it, Rate.ZERO)) }
    }

    val rateRisk = BankAdvisorRule { v, _ ->
        val per = v.margin.sensitivityPerPoint
        val cet1 = v.capital.cet1
        val big = cet1.raw > 0L && per.abs().raw * 100L > cet1.raw * 4L
        one(if (big) BankInsight("rate-risk", BankTopic.RISK, 45, if (per.isNegative) "Rising rates would hurt earnings" else "Falling rates would hurt earnings",
            "A one point move in rates changes yearly net interest income by ${per.format()}. Match the repricing of assets and funding.") else null)
    }

    val overpaying = BankAdvisorRule { v, _ ->
        val premium = DepositProduct.values().maxOf { v.bank.policy.premiumOf(it) }
        one(if (premium > 20L && v.liquidity.lcr.ppm > 2_500_000L) BankInsight("overpaying", BankTopic.FUNDING, 30, "You are paying over the odds for deposits",
            "Liquidity is ample, yet deposit rates carry a premium of $premium basis points.",
            BankAction.SetDepositPremium(v.bank.id, DepositProduct.SAVINGS, 0L)) else null)
    }

    val stress = BankAdvisorRule { v, c ->
        val result = c.stress
        val last = c.stressTick
        when {
            result != null && !result.passed -> listOf(BankInsight("stress-fail", BankTopic.RISK, 85, "The stress test showed a capital shortfall",
                "In the ${result.scenario.name.lowercase()} scenario CET1 falls to ${pct(result.minCet1Ratio)}. You would need ${result.capitalShortfall.format()} more capital to pass."))
            last == null || c.tick - last > c.stressEveryTicks -> listOf(BankInsight("stress-due", BankTopic.RISK, 25, "Run a stress test",
                "A stress test shows how much capital the bank would lose in a downturn before one arrives.",
                BankAction.RunStressTest(v.bank.id, StressScenario.ADVERSE.id)))
            else -> emptyList()
        }
    }

    val dividendWaiting = BankAdvisorRule { v, _ ->
        val d = v.dividend
        one(if (d.amount.isPositive && v.bank.owner != null) BankInsight("dividend-available", BankTopic.CAPITAL, 25, "A dividend is available",
            "You can pay ${d.amount.format()} to the owners without breaking any limit.", BankAction.PayDividend(v.bank.id, d.amount)) else null)
    }

    val weakRating = BankAdvisorRule { v, _ ->
        one(if (v.bank.rating.ordinal >= CreditRating.BB.ordinal) BankInsight("rating-weak", BankTopic.RISK, 55, "The bank's credit rating is speculative",
            "At ${v.bank.rating.label} depositors and the interbank market ask for more. Improve capital and asset quality.") else null)
    }

    val thinFund = BankAdvisorRule { v, c ->
        one(if (c.fundCoverage.ppm < 500_000L) BankInsight("guarantee-thin", BankTopic.SUPERVISION, 30, "The guarantee fund is thin",
            "It holds ${pct(c.fundCoverage)} of the covered deposits, so confidence in the guarantee is weaker and another levy may come.") else null)
    }

    val all: List<BankAdvisorRule> = listOf(
        supervised, capitalBreach, capitalBuffer, thinCushion, idleCapital, lowLiquidity, reserveShortfall, runRisk, uninsuredFunding,
        wholesaleDependence, marginSqueeze, heavyCosts, badLoans, concentration, rateRisk, overpaying, stress, dividendWaiting, weakRating, thinFund,
    )
}

/** Runs the rules over a bank and keeps track of suggestions the player has set aside for a while. */
class BankAdvisor(private val rules: List<BankAdvisorRule> = BankAdvisorRules.all) {
    private val hiddenUntil = java.util.TreeMap<String, Long>()

    fun all(view: BankView, ctx: AdvisorContext): List<BankInsight> =
        rules.flatMap { it.check(view, ctx) }.sortedWith(compareByDescending<BankInsight> { it.priority }.thenBy { it.code })

    fun advise(view: BankView, ctx: AdvisorContext, limit: Int = Int.MAX_VALUE): List<BankInsight> =
        all(view, ctx).filter { !isHidden(it.code, ctx.tick) }.take(limit)

    fun hide(code: String, tick: Long, days: Long = DEFAULT_HIDE_DAYS) { hiddenUntil[code] = tick + days.coerceAtLeast(1L) }
    fun unhide(code: String) { hiddenUntil.remove(code) }
    fun unhideAll() { hiddenUntil.clear() }
    fun isHidden(code: String, tick: Long): Boolean = (hiddenUntil[code] ?: Long.MIN_VALUE) > tick
    fun hiddenCodes(tick: Long): List<String> = hiddenUntil.filterValues { it > tick }.keys.toList()

    companion object { const val DEFAULT_HIDE_DAYS = 30L }
}
