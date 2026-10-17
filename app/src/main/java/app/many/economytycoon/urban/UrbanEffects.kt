package app.many.economytycoon.urban

import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/** Applies the lasting consequences of finished projects, enacted petitions and urban events to a plan. */
internal object UrbanEffects {
    fun projectCompleted(plan: CityPlan, p: CapitalProject, params: UrbanParams) {
        val d = p.district?.let { plan.districts.getOrNull(it) }
        when (p.kind) {
            ProjectKind.TRANSIT_LINE -> p.line?.let { plan.network.line(it)?.status = LineStatus.ACTIVE }
            ProjectKind.ROAD_UPGRADE -> p.road?.let { id ->
                val r = plan.network.road(id) ?: return@let
                val next = when (r.roadClass) {
                    RoadClass.LOCAL -> RoadClass.ARTERIAL
                    RoadClass.ARTERIAL -> RoadClass.HIGHWAY
                    RoadClass.HIGHWAY -> RoadClass.HIGHWAY
                }
                plan.network.upgradeRoad(id, next, if (next == r.roadClass) (r.lanes + 1).coerceAtMost(8) else r.lanes)
            }
            ProjectKind.PARK -> d?.let { it.amenityBonus = (it.amenityBonus + 150).coerceAtMost(500) }
            ProjectKind.SCHOOL -> {
                d?.let { it.amenityBonus = (it.amenityBonus + 60).coerceAtMost(500) }
                plan.budget.boostQuality(Service.EDUCATION, 80)
            }
            ProjectKind.HOSPITAL -> {
                d?.let { it.amenityBonus = (it.amenityBonus + 60).coerceAtMost(500) }
                plan.budget.boostQuality(Service.HEALTH, 80)
            }
            ProjectKind.CIVIC_CENTRE -> d?.let {
                it.amenityBonus = (it.amenityBonus + 100).coerceAtMost(500)
                it.stock.addNew(p.size.coerceAtLeast(0L))
            }
            ProjectKind.HOUSING_PROGRAM -> d?.let { it.stock.addNew(p.size.coerceAtLeast(0L), 900_000L) }
            ProjectKind.UTILITIES -> {
                plan.budget.boostQuality(Service.SANITATION, 60)
                d?.let { it.amenityBonus = (it.amenityBonus + 40).coerceAtMost(500) }
            }
        }
    }

    fun petitionEnacted(plan: CityPlan, p: Petition, now: Tick) {
        val d = plan.districts[p.district]
        if (p.kind != PetitionKind.VARIANCE) d.zone = p.toZone
        d.rules = if (p.kind == PetitionKind.VARIANCE) {
            DensityRules(maxOf(p.requestedFarMilli, d.rules.maxFarMilli), maxOf(p.requestedFloors, d.rules.maxFloors), d.rules.lotCoveragePpm)
        } else {
            DensityRules(p.requestedFarMilli, p.requestedFloors, d.rules.lotCoveragePpm)
        }
        d.lastRezonedAt = now
    }

    /** Immediate effects of an event that has just begun. */
    fun eventStarted(plan: CityPlan, e: UrbanEvent) {
        val sanitation = plan.budget.quality(Service.SANITATION).toLong()
        val fire = plan.budget.quality(Service.FIRE).toLong()
        when (e.kind) {
            UrbanEventKind.FLOOD -> {
                val share = mulDiv(60_000L + e.magnitudePermille * 190L, 1000L - sanitation / 2L, 1000L)
                e.damageSqm = plan.districts[e.district].stock.damage(share)
            }
            UrbanEventKind.EARTHQUAKE -> {
                val share = 10_000L + e.magnitudePermille * 200L
                e.damageSqm = plan.districts.sumOf { it.stock.damage(share) }
            }
            UrbanEventKind.FIRE -> {
                val share = mulDiv(20_000L + e.magnitudePermille * 60L, 1000L - fire * 7L / 10L, 1000L)
                e.damageSqm = plan.districts[e.district].stock.damage(share)
            }
            UrbanEventKind.FACTORY_CLOSURE -> {
                val d = plan.districts[e.district]
                e.damageSqm = d.stock.demolish(mulDiv(d.floorArea, 250L + e.magnitudePermille * 250L / 1000L, 1000L), sparePrewar = false)
            }
            UrbanEventKind.TRANSIT_STRIKE -> for (id in e.lines) {
                val l = plan.network.line(id)
                if (l != null && l.status == LineStatus.ACTIVE) l.status = LineStatus.SUSPENDED
            }
            UrbanEventKind.GENTRIFICATION_BACKLASH -> {
                plan.council.moodAgainstGrowth = (plan.council.moodAgainstGrowth + e.magnitudePermille / 5).coerceAtMost(1000)
                val d = plan.districts[e.district]
                d.tenantProtectionPermille = (d.tenantProtectionPermille + 150).coerceAtMost(1000)
            }
            UrbanEventKind.GROWTH_BOOM -> {}
        }
    }

    /** Undoes the temporary parts of an event that has run its course. */
    fun eventEnded(plan: CityPlan, e: UrbanEvent) {
        e.ended = true
        if (e.kind == UrbanEventKind.TRANSIT_STRIKE) for (id in e.lines) {
            val l = plan.network.line(id)
            if (l != null && l.status == LineStatus.SUSPENDED) l.status = LineStatus.ACTIVE
        }
    }
}
