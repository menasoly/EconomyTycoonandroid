package app.many.economytycoon.blueprint

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.DepositId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.production.FacilityStatus
import app.many.economytycoon.session.Formatting
import app.many.economytycoon.simulation.Economy

data class FacilityChoice(val id: FacilityId, val type: FacilityTypeId, val name: String, val typeName: String, val regionName: String, val lines: Int, val operating: Boolean)
data class RegionChoice(val id: RegionId, val name: String)
data class DepositChoice(val id: DepositId, val label: String, val owned: Boolean)

/**
 * Everything blueprints need to know about the running game, behind one small interface so the blueprint code
 * never touches the simulation directly and tests can use a plain fake.
 */
interface BlueprintHost {
    val content: ContentRepository
    val company: CompanyId

    /** The current in-game day. */
    val day: Long

    fun cash(): Money
    fun research(): ResearchView
    fun facilities(): List<FacilityChoice>
    fun view(id: FacilityId): FacilityView?
    fun regions(): List<RegionChoice>
    fun site(region: RegionId): SiteInfo
    fun deposits(region: RegionId, good: GoodId): List<DepositChoice>
    fun cityFor(region: RegionId): CityId?
    fun money(m: Money): String = Formatting.money(m)
}

/** The host for a live [Economy] and the company the player controls. */
class EconomyBlueprintHost(private val e: Economy, override val company: CompanyId, private val format: (Money) -> String = { Formatting.money(it) }) : BlueprintHost {
    override val content: ContentRepository get() = e.content
    override val day: Long get() = e.tick.value

    override fun cash(): Money = e.companies.company(company)?.let { e.finance.ledger.cash(it.owner) } ?: Money.ZERO

    override fun research(): ResearchView = ResearchView { e.research.isUnlocked(company, it) }

    override fun facilities(): List<FacilityChoice> = e.production.facilitiesOf(company).filter { it.status != FacilityStatus.DEMOLISHED }.map { f ->
        FacilityChoice(f.id, f.type, f.name.ifBlank { e.content.facilityTypeOrNull(f.type)?.name ?: f.type.key }, e.content.facilityTypeOrNull(f.type)?.name ?: f.type.key,
            e.world.regions().firstOrNull { it.id == f.region }?.name ?: "", f.lines.size, f.isOperating())
    }

    override fun view(id: FacilityId): FacilityView? {
        val f = e.production.facility(id) ?: return null
        if (f.owner != company) return null
        return FacilityViewReader.read(
            f, e.content, e.companies.policies.sellRules(company).filter { it.facility == id },
            e.companies.policies.buyRules(company).filter { it.facility == id }, e.workforce.cohortsAt(id),
        )
    }

    override fun regions(): List<RegionChoice> = e.world.regions().sortedBy { it.id }.map { RegionChoice(it.id, it.name) }

    override fun site(region: RegionId): SiteInfo {
        val r = e.world.regions().firstOrNull { it.id == region }
        val goods = e.world.deposits.inRegion(region).filter { it.owner == company && !it.exhausted }.map { it.good }.toSet()
        return SiteInfo(r, goods)
    }

    override fun deposits(region: RegionId, good: GoodId): List<DepositChoice> =
        e.world.deposits.inRegion(region).filter { it.good == good && !it.exhausted && (it.owner == null || it.owner == company) }
            .sortedWith(compareByDescending<app.many.economytycoon.world.Deposit> { it.owner == company }.thenByDescending { it.gradePpm }.thenBy { it.id })
            .map { DepositChoice(it.id, Formatting.title(it.good.key) + if (it.owner == company) " (yours)" else "", it.owner == company) }

    override fun cityFor(region: RegionId): CityId? = e.world.citiesIn(region).maxByOrNull { e.world.population(it.id).total }?.id

    override fun money(m: Money): String = format(m)
}
