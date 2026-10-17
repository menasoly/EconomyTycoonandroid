package app.many.economytycoon.ui.present

import app.many.economytycoon.banking.BankAction
import app.many.economytycoon.banking.BankId
import app.many.economytycoon.banking.BankSnapshot
import app.many.economytycoon.banking.BankStatus
import app.many.economytycoon.banking.BankView
import app.many.economytycoon.banking.BankingInputs
import app.many.economytycoon.banking.BankingService
import app.many.economytycoon.banking.Depositor
import app.many.economytycoon.banking.DepositProduct
import app.many.economytycoon.banking.LoanSector
import app.many.economytycoon.banking.MonthlyResult
import app.many.economytycoon.banking.RunStage
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick
import app.many.economytycoon.session.Formatting

data class LendingRow(val label: String, val balance: String, val share: String, val yield: String, val expectedLoss: String, val tone: Tone)

data class HistoryRow(val month: String, val income: String, val result: String, val roe: String, val tone: Tone)

data class DriverRow(val label: String, val effect: String, val tone: Tone)

data class PolicyRow(val label: String, val value: String, val hint: String?)

/** Everything the detail screen of one bank shows, in sections. */
data class BankDetail(
    val id: BankId,
    val name: String,
    val headline: String,
    val status: String,
    val tone: Tone,
    val tiles: List<Tile>,
    val notice: String?,
    val balanceAssets: List<Fact>,
    val balanceFunding: List<Fact>,
    val capital: List<Fact>,
    val liquidity: List<Fact>,
    val earnings: List<Fact>,
    val lending: List<LendingRow>,
    val funding: List<Fact>,
    val policy: List<PolicyRow>,
    val confidence: List<Fact>,
    val drivers: List<DriverRow>,
    val history: List<HistoryRow>,
    val canManage: Boolean,
)

object BankDetailPresenter {
    private fun fact(label: String, value: String, tone: Tone = Tone.NEUTRAL, hint: String? = null) = Fact(label, value, tone, hint)

    fun assets(s: BankSnapshot, money: (Money) -> String): List<Fact> = listOf(
        fact("Cash and reserves", money(s.reserves)),
        fact("Lent to other banks", money(s.interbankLent)),
        fact("Securities", money(s.securitiesValue())),
        fact("Loans to customers", money(s.loansGross)),
        fact("Provision for bad loans", money(-s.allowance)),
        fact("Premises and goodwill", money(s.fixedAssets + s.intangibles)),
        fact("Total assets", money(s.totalAssets), Tone.NEUTRAL),
    )

    fun fundingFacts(s: BankSnapshot, money: (Money) -> String): List<Fact> = listOf(
        fact("Customer deposits", money(s.depositsTotal)),
        fact("Wholesale funding", money(s.wholesale)),
        fact("Borrowed from other banks", money(s.interbankBorrowed)),
        fact("Central bank loans", money(s.centralBankDebt), if (s.centralBankDebt.isPositive) Tone.WARN else Tone.NEUTRAL),
        fact("Subordinated debt", money(s.additionalTier1 + s.tier2)),
        fact("Shareholders' equity", money(s.bookEquity), Tones.bySign(s.bookEquity.raw)),
    )

    fun capitalFacts(view: BankView, money: (Money) -> String): List<Fact> {
        val c = view.capital
        return listOf(
            fact("CET1 ratio", Formatting.percent(c.cet1Ratio), BankingLabels.rungTone(c.rung), "Common equity over risk-weighted assets"),
            fact("Requirement with buffers", Formatting.percent(c.cet1Requirement)),
            fact("Headroom", Formatting.signedPercentPpm(c.headroom.ppm), Tones.bySign(c.headroom.ppm)),
            fact("Tier 1 ratio", Formatting.percent(c.tier1Ratio)),
            fact("Total capital ratio", Formatting.percent(c.totalRatio)),
            fact("Leverage ratio", Formatting.percent(c.leverageRatio)),
            fact("Risk-weighted assets", money(c.rwa.total)),
            fact("Share of profit that may be paid out", Formatting.percent(c.distributionLimit), if (c.distributionLimit < Rate.ONE) Tone.WARN else Tone.NEUTRAL),
            fact("Supervisory standing", c.rung.label, BankingLabels.rungTone(c.rung)),
        )
    }

    fun liquidityFacts(view: BankView, money: (Money) -> String): List<Fact> {
        val l = view.liquidity
        return listOf(
            fact("Liquidity coverage", BankingLabels.coverage(l.lcr), BankingLabels.ratioTone(l.lcr, Rate.ONE, Rate.percent(130)), "Liquid assets over 30 days of stressed outflows"),
            fact("Stable funding", BankingLabels.coverage(l.nsfr), BankingLabels.ratioTone(l.nsfr, Rate.ONE, Rate.percent(110))),
            fact("Liquid buffer", money(l.hqla)),
            fact("Reserves held", money(l.reserves)),
            fact("Reserves required", money(l.requiredReserves), if (l.meetsReserve) Tone.NEUTRAL else Tone.BAD),
            fact("Spare reserves", money(l.excessReserves), Tones.bySign(l.excessReserves.raw)),
        )
    }

    fun earningsFacts(view: BankView): List<Fact> {
        val m = view.margin
        return listOf(
            fact("Net interest margin", Formatting.percent(m.netInterestMargin), hint = "Interest earned less interest paid, over earning assets"),
            fact("Margin, last three months", Formatting.percent(m.trailingMargin)),
            fact("Loan yield", Formatting.percent(m.loanYield)),
            fact("Cost of funding", Formatting.percent(m.fundingCost)),
            fact("Return on equity", Formatting.percent(m.returnOnEquity), Tones.bySign(m.returnOnEquity.ppm)),
            fact("Return on assets", Formatting.percent(m.returnOnAssets), Tones.bySign(m.returnOnAssets.ppm)),
            fact("Costs over income", Formatting.percent(m.costToIncome), if (m.costToIncome.ppm > 700_000L) Tone.WARN else Tone.NEUTRAL),
            fact("Cost of credit risk", Formatting.percent(m.costOfRisk)),
        )
    }

    fun lendingRows(view: BankView, money: (Money) -> String): List<LendingRow> =
        view.margin.bySector.filter { it.balance.isPositive }.map {
            LendingRow(
                it.sector.label, money(it.balance), Formatting.percent(it.share), Formatting.percent(it.yield), Formatting.percent(it.expectedLoss),
                if (it.riskAdjustedSpread.ppm <= 0L) Tone.BAD else if (it.impairedShare.ppm > 80_000L) Tone.WARN else Tone.NEUTRAL,
            )
        }

    fun fundingLines(view: BankView, money: (Money) -> String): List<Fact> =
        view.margin.funding.filter { it.balance.isPositive }.map { fact(it.label, "${money(it.balance)} at ${Formatting.percent(it.rate)}") }

    fun policyRows(s: BankSnapshot): List<PolicyRow> {
        val p = s.policy
        val rows = ArrayList<PolicyRow>()
        for (d in DepositProduct.values()) rows += PolicyRow("${d.label} rate premium", "${signedBps(p.premiumOf(d))}", "Paid on top of the market rate")
        rows += PolicyRow("Loan margin", signedBps(p.loanMarginBps), "Added to the price of every new loan")
        rows += PolicyRow("Lowest borrower rating", p.minRating.label, null)
        rows += PolicyRow("Largest sector share", Formatting.percent(p.maxSectorShare), null)
        rows += PolicyRow("Growth appetite", Formatting.percent(p.growthAppetite), "How fast the bank tries to lend")
        rows += PolicyRow("Capital cushion", Formatting.percent(p.capitalCushion), "Kept above the regulatory requirement")
        rows += PolicyRow("Dividend payout", Formatting.percent(p.dividendPayout), null)
        rows += PolicyRow("Target liquidity cover", Formatting.percent(p.targetLcr), null)
        rows += PolicyRow("Securities target", Formatting.percent(p.securitiesShare), "Share of assets")
        rows += PolicyRow("Wholesale funding cap", Formatting.percent(p.wholesaleCap), "Share of assets")
        for (sector in LoanSector.values()) rows += PolicyRow("${sector.label} appetite", Formatting.percent(p.appetiteOf(sector)), null)
        return rows
    }

    private fun signedBps(bps: Long): String = (if (bps > 0) "+" else "") + bps + " bp"

    fun historyRows(s: BankSnapshot, money: (Money) -> String, limit: Int = 6): List<HistoryRow> =
        s.history.takeLast(limit).reversed().map {
            HistoryRow(Formatting.monthYear(Tick(it.tick)), money(it.grossIncome), money(it.netIncome), Formatting.percent(it.returnOnEquity), Tones.bySign(it.netIncome.raw))
        }

    fun notice(s: BankSnapshot, view: BankView, tick: Long): String? = when {
        s.status == BankStatus.MERGED -> "This bank was merged into another."
        s.status == BankStatus.LIQUIDATED -> "This bank was wound up. Insured depositors were paid by the guarantee fund."
        s.status == BankStatus.SUPERVISED -> "The supervisor has restricted this bank: no dividends, no faster growth, no new wholesale funding."
        s.runStage >= RunStage.RUN -> "Depositors are withdrawing money. Reassure them, find liquidity or raise capital."
        view.liquidity.lcr < Rate.ONE -> "Liquidity is below the minimum. Hold more short government paper or raise deposits."
        else -> null
    }

    fun drivers(service: BankingService, id: BankId, inputs: BankingInputs): List<DriverRow> =
        service.confidence(id, inputs)?.explain()?.map {
            DriverRow(it.first, Formatting.signedPercentPpm(it.second), if (it.second < 0L) Tone.BAD else Tone.GOOD)
        } ?: emptyList()

    fun build(service: BankingService, id: BankId, inputs: BankingInputs, tick: Long, money: (Money) -> String, manageable: Boolean): BankDetail? {
        val s = service.bank(id) ?: return null
        val view = BankView(s, inputs, service.params)
        val last = s.lastResult
        val tiles = listOf(
            Tile("Assets", money(s.totalAssets)),
            Tile("CET1 ratio", Formatting.percent(view.capital.cet1Ratio), BankingLabels.rungTone(view.capital.rung)),
            Tile("Liquidity cover", BankingLabels.coverage(view.liquidity.lcr), BankingLabels.ratioTone(view.liquidity.lcr, Rate.ONE, Rate.percent(130))),
            Tile("Rating", s.rating.label, BankingLabels.ratingTone(s.rating)),
            Tile("Bad loans", Formatting.percent(s.nplRatio), if (s.nplRatio.ppm > 60_000L) Tone.BAD else if (s.nplRatio.ppm > 30_000L) Tone.WARN else Tone.GOOD),
            Tile("Monthly result", if (last == null) "n/a" else money(last.netIncome), if (last == null) Tone.MUTED else Tones.bySign(last.netIncome.raw)),
        )
        val confidence = listOf(
            fact("Depositor confidence", BankingLabels.confidence(s.confidencePpm), BankingLabels.confidenceTone(s.confidencePpm)),
            fact("Mood", s.runStage.label, BankingLabels.runTone(s.runStage)),
        )
        return BankDetail(
            id = s.id, name = s.name, headline = "${s.rating.label} · ${BankingLabels.operatorLabel(s.operator)}", status = s.status.label,
            tone = BankingLabels.statusTone(s.status), tiles = tiles, notice = notice(s, view, tick),
            balanceAssets = assets(s, money), balanceFunding = fundingFacts(s, money), capital = capitalFacts(view, money),
            liquidity = liquidityFacts(view, money), earnings = earningsFacts(view), lending = lendingRows(view, money),
            funding = fundingLines(view, money), policy = policyRows(s), confidence = confidence, drivers = drivers(service, id, inputs),
            history = historyRows(s, money), canManage = manageable && s.status.isOperating,
        )
    }
}
