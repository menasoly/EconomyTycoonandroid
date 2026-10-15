package app.many.economytycoon.session

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money

/** One sample of a company's standing. */
data class StatPoint(val tick: Long, val cash: Money, val netWorth: Money, val revenue: Money, val netIncome: Money, val employees: Long)

/**
 * Remembers how a company has done over time so charts have something to draw. Samples are taken every
 * [everyTicks] ticks and only the latest [capacity] per company are kept. The series is not part of a save;
 * after loading it starts again from the loaded state.
 */
class StatsRecorder(private val reports: CompanyReports, private val everyTicks: Long = 5L, private val capacity: Int = 240) {
    private val series = java.util.TreeMap<CompanyId, ArrayDeque<StatPoint>>()
    private var lastSampled = Long.MIN_VALUE

    fun sample(tick: Long, companies: List<CompanyId>) {
        if (lastSampled != Long.MIN_VALUE && tick - lastSampled < everyTicks) return
        lastSampled = tick
        for (id in companies) {
            val h = reports.headline(id) ?: continue
            val list = series.getOrPut(id) { ArrayDeque() }
            list.addLast(StatPoint(tick, h.cash, h.netWorth, h.revenue, h.netIncome, h.employees))
            while (list.size > capacity) list.removeFirst()
        }
    }

    fun of(company: CompanyId): List<StatPoint> = series[company]?.toList().orEmpty()

    fun latest(company: CompanyId): StatPoint? = series[company]?.lastOrNull()

    /** Change in net worth between the first and last sample, or null with fewer than two samples. */
    fun netWorthChange(company: CompanyId): Money? {
        val s = series[company] ?: return null
        if (s.size < 2) return null
        return s.last().netWorth - s.first().netWorth
    }

    fun clear() { series.clear(); lastSampled = Long.MIN_VALUE }
}
