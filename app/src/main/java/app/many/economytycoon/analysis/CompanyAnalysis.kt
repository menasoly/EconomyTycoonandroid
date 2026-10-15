package app.many.economytycoon.analysis

import app.many.economytycoon.core.Money
import app.many.economytycoon.finance.BalanceSheet
import app.many.economytycoon.finance.CashFlowStatement
import app.many.economytycoon.finance.IncomeStatement
import app.many.economytycoon.finance.Ratios

/** The books a company's analysis is drawn from: one period of results and the position at its end. */
class AnalysisInput(
    val income: IncomeStatement,
    val balance: BalanceSheet,
    val cashFlow: CashFlowStatement,
    val periodDays: Long,
    /** Net worth sampled at even intervals, oldest first, for the trend and forecast. */
    val netWorthHistory: List<Money> = emptyList(),
    val historyStepDays: Long = 5L,
    val asOfDay: Long = 0L,
    val yearDays: Long = 360L,
)

/** Everything the analysis screen shows for one company. */
class CompanyAnalysis(
    val readings: List<Reading>,
    val overall: Grade?,
    val weakSpots: List<Reading>,
    val efficiency: Efficiency,
    val dupont: DuPont?,
    val costs: CostStructure,
    val runway: Runway,
    val outlook: Forecast?,
    val values: Map<Measure, Long>,
) {
    companion object {
        fun of(input: AnalysisInput): CompanyAnalysis {
            val i = input.income
            val b = input.balance
            val ratios = Ratios.of(i, b, periodsPerYear = input.yearDays / input.periodDays.coerceAtLeast(1L))
            val eff = Efficiency.of(i, b, input.periodDays, input.yearDays)
            val values = LinkedHashMap<Measure, Long>()
            fun put(m: Measure, v: Long?) { if (v != null) values[m] = v }
            val hasSales = i.revenue.raw > 0L
            val hasDebts = b.currentLiabilities.raw > 0L
            put(Measure.CURRENT, if (hasDebts) ratios.currentRatio.ppm else null)
            put(Measure.CASH, if (hasDebts) ratios.cashRatio.ppm else null)
            put(Measure.DEBT_TO_EQUITY, if (b.totalEquity.raw > 0L) ratios.debtToEquity.ppm else null)
            put(Measure.INTEREST_COVER, if (i.interestExpense.raw > 0L) ratios.interestCoverage.ppm else null)
            put(Measure.GROSS_MARGIN, if (hasSales) ratios.grossMargin.ppm else null)
            put(Measure.NET_MARGIN, if (hasSales) ratios.netMargin.ppm else null)
            put(Measure.RETURN_ON_EQUITY, if (b.totalEquity.raw > 0L && hasSales) ratios.returnOnEquity.ppm else null)
            put(Measure.RETURN_ON_ASSETS, if (b.totalAssets.raw > 0L && hasSales) ratios.returnOnAssets.ppm else null)
            put(Measure.EQUITY_RATIO, if (b.totalAssets.raw > 0L) ratios.equityRatio.ppm else null)
            put(Measure.CASH_CYCLE, eff.cashCycleDays)
            put(Measure.ASSET_TURNOVER, eff.assetTurnoverPpm)
            val readings = values.mapNotNull { (m, v) -> Grader.read(m, v) }
            val history = input.netWorthHistory
            val runway = Runway.of(b.cash, input.cashFlow.netChange, input.periodDays)
            return CompanyAnalysis(
                readings = readings,
                overall = Grader.overall(readings),
                weakSpots = Grader.weakest(readings),
                efficiency = eff,
                dupont = DuPont.of(i, b, input.periodDays, input.yearDays),
                costs = CostStructure.of(i),
                runway = runway,
                outlook = if (history.size >= Forecast.MIN_POINTS) Forecast.of(history, input.historyStepDays, input.asOfDay) else null,
                values = values,
            )
        }
    }
}
