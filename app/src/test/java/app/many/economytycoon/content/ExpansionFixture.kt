package app.many.economytycoon.content

import app.many.economytycoon.content.audit.AuditReport
import app.many.economytycoon.content.audit.ChainCost
import app.many.economytycoon.content.audit.ContentAudit
import app.many.economytycoon.content.audit.FacilityPayback
import app.many.economytycoon.content.audit.GoodFlows
import app.many.economytycoon.content.audit.Payback
import app.many.economytycoon.content.audit.RecipeEconomics
import app.many.economytycoon.content.audit.RecipeEconomy
import app.many.economytycoon.core.GoodId

/** Everything the expansion tests share, computed once per test JVM from [ExpandedContent]. */
object ExpansionFixture {
    /** Goods that no recipe makes and that the expansion may use as bought-in inputs. */
    val allowedExogenous: Set<String> = setOf("water", "packaging", "coal", "foam", "carbon_black")

    val expansion: List<ContentPack> get() = ExpandedContent.expansionPacks
    val repo: ContentRepository get() = ExpandedContent.repository
    val flows: GoodFlows by lazy { GoodFlows(repo) }
    val economics: List<RecipeEconomy> by lazy { RecipeEconomics.all(repo) }
    val economyById: Map<String, RecipeEconomy> by lazy { economics.associateBy { it.recipe.id.key } }
    val paybacks: List<Payback> by lazy { FacilityPayback.all(repo, economics) }
    val paybackById: Map<String, Payback> by lazy { paybacks.associateBy { it.facility.id.key } }
    val chain: ChainCost by lazy { ChainCost(repo) }
    val audit: AuditReport by lazy { ContentAudit.run(repo) }

    fun pack(packId: String): ContentPack = ExpandedContent.expansionPack(packId)

    fun goodKeys(pack: ContentPack): Set<String> = pack.goods.map { it.id.key }.toSet()
    fun recipeKeys(pack: ContentPack): Set<String> = pack.recipes.map { it.id.key }.toSet()
    fun facilityKeys(pack: ContentPack): Set<String> = pack.facilityTypes.map { it.id.key }.toSet()

    val allExpansionGoods: Set<String> by lazy { expansion.flatMap { goodKeys(it) }.toSet() }
    val allExpansionRecipes: Set<String> by lazy { expansion.flatMap { recipeKeys(it) }.toSet() }
    val allExpansionFacilities: Set<String> by lazy { expansion.flatMap { facilityKeys(it) }.toSet() }

    fun economicsOf(pack: ContentPack): List<RecipeEconomy> = pack.recipes.mapNotNull { economyById[it.id.key] }
    fun paybacksOf(pack: ContentPack): List<Payback> = pack.facilityTypes.mapNotNull { paybackById[it.id.key] }

    /** Audit findings whose subject is something the pack defines. */
    fun findingsOf(pack: ContentPack): List<String> {
        val goods = goodKeys(pack); val recipes = recipeKeys(pack); val facilities = facilityKeys(pack)
        return audit.findings.filter { f ->
            f.subject in goods || f.subject in facilities || f.subject in recipes || recipes.any { f.subject.endsWith(it) }
        }.map { it.toString() }
    }

    /** Raw-resource goods of the repository: nothing makes them, or a mine or farm extracts them. */
    fun sourceGoods(): Set<GoodId> = repo.allGoods.filter { flows.producersOf(it.id).isEmpty() || flows.isExtracted(it.id) }.map { it.id }.toSet()
}
