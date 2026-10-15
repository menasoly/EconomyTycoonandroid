package app.many.economytycoon.macro

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick

data class MacroParams(
    /** Ticks between macro reviews (one month). */
    val periodTicks: Int = 30,
    val ticksPerYear: Long = 360,
    /** Inflation the central bank aims for, ppm per year. */
    val inflationTargetPpm: Long = 20_000,
    /** Real policy rate when the economy is at target, ppm. */
    val neutralRealRatePpm: Long = 15_000,
    val minPolicyRatePpm: Long = 0,
    val maxPolicyRatePpm: Long = 250_000,
    /** Largest policy rate change per review, ppm. */
    val maxRateStepPpm: Long = 5_000,
    val historyPeriods: Int = 60,
    /** Smoothing of potential output, ppm per period. */
    val trendSmoothingPpm: Long = 50_000,
)

enum class GdpKind { CONSUMPTION, INVESTMENT, GOVERNMENT, EXPORTS, IMPORTS }

enum class CyclePhase { EXPANSION, PEAK, CONTRACTION, TROUGH }

/** What the macro layer needs to read from the rest of the simulation. Implemented by the simulation adapter. */
interface MacroView {
    fun countries(): List<CountryId>
    /** Annual consumer price change, signed ppm. */
    fun inflationPpm(country: CountryId): Long
    fun unemploymentPpm(country: CountryId): Long
    fun population(country: CountryId): Long
    fun averageWage(country: CountryId): Money
}

/** One review of one country's economy. */
data class MacroSnapshot(
    val tick: Tick,
    val country: CountryId,
    val gdp: Money,
    /** Change of GDP against the same period a year earlier, signed ppm, or null before a year of data exists. */
    val growthPpm: Long?,
    val outputGapPpm: Long,
    val inflationPpm: Long,
    val unemploymentPpm: Long,
    val policyRatePpm: Long,
    val phase: CyclePhase,
    val confidencePpm: Long,
    /** Multiplier applied to household demand, ppm (1,000,000 means neutral). */
    val demandShiftPpm: Long,
    val debtToGdpPpm: Long,
    val budgetBalance: Money,
) {
    fun hashInto(h: CanonicalHasher) {
        h.putLong(tick.value).putLong(country.value).putMoney(gdp).putLong(growthPpm ?: Long.MIN_VALUE).putLong(outputGapPpm)
            .putLong(inflationPpm).putLong(unemploymentPpm).putLong(policyRatePpm).putInt(phase.ordinal)
            .putLong(confidencePpm).putLong(demandShiftPpm).putLong(debtToGdpPpm).putMoney(budgetBalance)
    }
}

/** Spending flows of one country during the open period. */
class GdpLedger {
    private val flows = java.util.EnumMap<GdpKind, Long>(GdpKind::class.java)
    private val history = ArrayList<Money>()

    fun record(kind: GdpKind, amount: Money) {
        if (amount.raw <= 0) return
        flows[kind] = (flows[kind] ?: 0L) + amount.raw
    }

    fun open(kind: GdpKind): Money = Money(flows[kind] ?: 0L)

    /** GDP of the open period: C + I + G + (X - M). */
    fun openGdp(): Money {
        val v = open(GdpKind.CONSUMPTION).raw + open(GdpKind.INVESTMENT).raw + open(GdpKind.GOVERNMENT).raw +
            open(GdpKind.EXPORTS).raw - open(GdpKind.IMPORTS).raw
        return Money(v.coerceAtLeast(0L))
    }

    fun close(): Money {
        val gdp = openGdp()
        history += gdp
        flows.clear()
        return gdp
    }

    fun past(): List<Money> = history.toList()

    /** GDP of the period [back] reviews ago, 1 being the last closed one. */
    fun ago(back: Int): Money? = history.getOrNull(history.size - back)

    internal fun trim(max: Int) { while (history.size > max) history.removeAt(0) }
    internal fun restore(open: Map<GdpKind, Long>, past: List<Money>) { flows.clear(); flows.putAll(open); history.clear(); history.addAll(past) }
    internal fun openEntries(): List<Pair<GdpKind, Long>> = flows.entries.map { it.key to it.value }

    fun hashInto(h: CanonicalHasher) {
        for (k in GdpKind.values()) h.putLong(flows[k] ?: 0L)
        h.putInt(history.size)
        for (m in history) h.putMoney(m)
    }
}
