package app.many.economytycoon.blueprint

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId

/** A host over plain data, so controller and presenter tests need no simulation. */
class FakeHost(
    override val content: ContentRepository = BlueprintTestSupport.content,
    var cash: Money = Money.units(1_000_000),
    var tech: ResearchView = ResearchView.ALL,
    override var day: Long = 10L,
) : BlueprintHost {
    override val company = CompanyId(1)
    val views = java.util.TreeMap<FacilityId, FacilityView>()
    val deposits = HashMap<RegionId, List<DepositChoice>>()
    var siteInfo: SiteInfo = SiteInfo(null)

    fun put(v: FacilityView) { views[v.id] = v }

    override fun cash(): Money = cash
    override fun research(): ResearchView = tech
    override fun facilities(): List<FacilityChoice> =
        views.values.map { FacilityChoice(it.id, it.type, it.name, content.facilityTypeOrNull(it.type)?.name ?: it.type.key, "Region ${it.region.value}", it.lines.size, it.operating) }
    override fun view(id: FacilityId): FacilityView? = views[id]
    override fun regions(): List<RegionChoice> = listOf(RegionChoice(BlueprintTestSupport.region, "Vale"))
    override fun site(region: RegionId): SiteInfo = siteInfo
    override fun deposits(region: RegionId, good: GoodId): List<DepositChoice> = deposits[region].orEmpty()
    override fun cityFor(region: RegionId): CityId? = CityId(3)
}
