package app.many.economytycoon.marketing

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick

/**
 * Things that go wrong in public. Each takes a share of awareness and loyalty (scaled by how severe the event is)
 * and leaves a lingering distrust that makes new advertising work less well until it fades.
 */
enum class DamageKind(val label: String, val awarenessHitPpm: Long, val loyaltyHitPpm: Long, val taintPpm: Long) {
    RECALL("Product recall", 350_000L, 300_000L, 500_000L),
    SAFETY_INCIDENT("Safety incident", 450_000L, 450_000L, 700_000L),
    SCANDAL("Corporate scandal", 300_000L, 550_000L, 800_000L),
    LABOUR_DISPUTE("Labour dispute", 150_000L, 200_000L, 300_000L),
    DATA_BREACH("Data breach", 200_000L, 400_000L, 600_000L),
    MISLEADING_CLAIM("Misleading advertising", 250_000L, 350_000L, 650_000L);

    companion object {
        fun byOrdinal(i: Int): DamageKind? = values().getOrNull(i)
    }
}

/** A record of one blow to a brand. A null [good] means the whole company; a null [region] means everywhere. */
data class DamageRecord(
    val id: DamageId,
    val company: CompanyId,
    val good: GoodId?,
    val region: RegionId?,
    val kind: DamageKind,
    val severityPpm: Long,
    val tick: Tick,
    /** Awareness the event actually removed, averaged over every brand it touched. */
    val awarenessLostPpm: Long,
) {
    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putLong(company.value).putString(good?.key ?: "").putBoolean(good != null).putLong(region?.value ?: -1L)
            .putInt(kind.ordinal).putLong(severityPpm).putLong(tick.value).putLong(awarenessLostPpm)
    }
}

object BrandDamage {
    /** Applies a blow to one stock and returns the awareness it removed. */
    fun hit(stock: BrandStock, kind: DamageKind, severityPpm: Long): Long {
        val sev = Ppm.clamp(severityPpm)
        val before = stock.awarenessPpm
        val awareness = before - Ppm.mul(Ppm.mul(before, kind.awarenessHitPpm), sev)
        val loyalty = stock.loyaltyPpm - Ppm.mul(Ppm.mul(stock.loyaltyPpm, kind.loyaltyHitPpm), sev)
        stock.set(awareness, loyalty.coerceAtMost(awareness))
        val taint = stock.taintPpm + Ppm.mul(Ppm.mul(Ppm.ONE - stock.taintPpm, kind.taintPpm), sev)
        stock.setTaint(taint)
        return before - stock.awarenessPpm
    }

    /** Days until the taint of a blow has faded below [thresholdPpm], at the given recovery rate. */
    fun daysToRecover(kind: DamageKind, severityPpm: Long, params: MarketingParams, thresholdPpm: Long = 50_000L): Int {
        var taint = Ppm.mul(kind.taintPpm, Ppm.clamp(severityPpm))
        var days = 0
        while (taint > thresholdPpm && days < MAX_RECOVERY_DAYS) {
            taint = Ppm.shrink(taint, params.taintRecoveryPpm)
            days++
        }
        return days
    }

    /** Spend needed to win back what a blow took, as days of the campaign's usual budget: heavier damage costs more. */
    fun repairDays(kind: DamageKind, severityPpm: Long): Int = 3 + (Ppm.mul(kind.awarenessHitPpm, Ppm.clamp(severityPpm)) / 20_000L).toInt()

    private const val MAX_RECOVERY_DAYS = 2_000
}
