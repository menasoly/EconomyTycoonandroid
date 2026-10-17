package app.many.economytycoon.marketing

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick

/** Records public blows to a brand and applies them to what people think of it. */
class DamageService(private val state: MarketingState) {

    /**
     * Hits every brand of [company] that matches: one good or all, one region or all. Returns the record, or a
     * rejection when the severity is not between zero and one or nothing matched.
     */
    fun record(company: CompanyId, good: GoodId?, region: RegionId?, kind: DamageKind, severityPpm: Long, tick: Tick): Outcome<Pair<DamageRecord, MarketingEvent.BrandDamaged>> {
        if (severityPpm <= 0L || severityPpm > Ppm.ONE) return Outcome.reject(Rules.INVALID_ARGUMENT, "severity must be above nothing and at most the whole")
        val targets = state.stockMap.entries.filter {
            it.key.company == company && (good == null || it.key.good == good) && (region == null || it.key.region == region)
        }
        if (targets.isEmpty()) return Outcome.reject(Rules.UNKNOWN_ENTITY, "that company has no brand there to damage")
        var lost = 0L
        for (t in targets) lost += BrandDamage.hit(t.value, kind, severityPpm)
        val record = DamageRecord(state.ids.damage(), company, good, region, kind, severityPpm, tick, lost / targets.size)
        state.damageMap[record.id] = record
        return Outcome.ok(record to MarketingEvent.BrandDamaged(record.id, company, kind, record.awarenessLostPpm, tick))
    }

    /** Taint still hanging over a company's brands, averaged, for the screen. */
    fun averageTaintPpm(company: CompanyId): Long {
        val mine = state.stocksOf(company)
        return if (mine.isEmpty()) 0L else mine.sumOf { it.second.taintPpm } / mine.size
    }
}
