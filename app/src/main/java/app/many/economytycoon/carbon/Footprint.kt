package app.many.economytycoon.carbon

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.content.GoodCategory
import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.content.RecipeDef
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.RoundingMode
import app.many.economytycoon.core.mulDiv

/** How a release counts. */
enum class EmissionScope(val label: String) {
    /** Released on site by burning fuel or by the chemistry of the process. Regulated. */
    DIRECT("Direct"),
    /** Released by the power stations that supplied the electricity the site drew. Reported, not charged twice. */
    INDIRECT("Electricity"),
    /** Released by burning plant-based fuel. Reported as a memo item only. */
    BIOGENIC("Biogenic"),
}

/** One source of emissions at a facility: what it was, how much, and how firmly the factor behind it is known. */
data class EmissionLine(
    val kind: SourceKind,
    /** The good id of a fuel, the recipe id of a process, `grid` for electricity or the facility type for site overhead. */
    val key: String,
    val scope: EmissionScope,
    val amount: Tonnes,
    val source: FactorSource,
)

/**
 * What one facility did in one day, as plain numbers: the input of emissions accounting. The production adapter builds
 * it from the production report; tests and the AI build it by hand.
 */
data class FacilityActivity(
    val facility: FacilityId,
    val company: CompanyId,
    val country: CountryId,
    val type: FacilityTypeId,
    val family: IndustryFamily,
    val consumed: Map<GoodId, Quantity> = emptyMap(),
    val batchesByRecipe: Map<RecipeId, Long> = emptyMap(),
    val electricityUsed: Quantity = Quantity.ZERO,
    /** Electricity the facility generated today; the national grid intensity is worked out from it. */
    val electricityProduced: Quantity = Quantity.ZERO,
    /** Value of what the facility made today, at reference prices; the denominator of an emission-intensity benchmark. */
    val outputValue: Money = Money.ZERO,
    /** What the fuels and electricity it used today were worth, at reference prices. */
    val energySpend: Money = Money.ZERO,
    val operating: Boolean = true,
)

/** The emissions of one facility-day with their attribution. [avoided] is what installed abatement kept out of the air. */
class FacilityEmissions(val activity: FacilityActivity, val lines: List<EmissionLine>, val avoided: Tonnes) {
    private fun total(scope: EmissionScope): Tonnes = Tonnes.sum(lines.filter { it.scope == scope }.map { it.amount })

    val direct: Tonnes get() = total(EmissionScope.DIRECT)
    val indirect: Tonnes get() = total(EmissionScope.INDIRECT)
    val biogenic: Tonnes get() = total(EmissionScope.BIOGENIC)

    /** What a regulator counts for the facility. */
    fun of(kind: SourceKind): Tonnes = Tonnes.sum(lines.filter { it.kind == kind && it.scope == EmissionScope.DIRECT }.map { it.amount })

    companion object {
        fun none(activity: FacilityActivity) = FacilityEmissions(activity, emptyList(), Tonnes.ZERO)
    }
}

/**
 * Turns activity into emissions using an [EmissionFactorTable]. It is a pure function of its arguments, so the same
 * day of activity always yields the same lines, and every figure can be traced to a fuel, a recipe or the grid.
 */
class EmissionCalculator(val table: EmissionFactorTable, private val content: ContentRepository? = null) {

    private fun categoryOf(good: GoodId): GoodCategory? = content?.goodOrNull(good)?.category

    private fun familyOfRecipe(recipe: RecipeId): IndustryFamily? {
        val def = content?.recipeOrNull(recipe) ?: return null
        return content.allIndustries.firstOrNull { it.id == def.industry }?.family
    }

    private fun grams(quantityRaw: Long, gramsPerUnit: Long): Tonnes =
        Tonnes.ofGrams(mulDiv(quantityRaw, gramsPerUnit, Quantity.SCALE, RoundingMode.HALF_EVEN))

    /**
     * The emissions of one facility-day. [gridGramsPerUnit] is the national grid intensity if it is known, and
     * [effects] are the abatement measures installed at the facility.
     */
    fun facilityDay(activity: FacilityActivity, gridGramsPerUnit: Long? = null, effects: List<AbatementEffect> = emptyList()): FacilityEmissions {
        if (!activity.operating) return FacilityEmissions.none(activity)
        val raw = ArrayList<EmissionLine>()
        for ((good, qty) in activity.consumed) {
            if (qty.raw <= 0L) continue
            val (factor, source) = table.fuel(good, categoryOf(good))
            if (factor.gramsPerUnit == 0L) continue
            val mass = grams(qty.raw, factor.gramsPerUnit)
            if (mass.isZero) continue
            raw += EmissionLine(SourceKind.FUEL, good.key, if (factor.biogenic) EmissionScope.BIOGENIC else EmissionScope.DIRECT, mass, source)
        }
        for ((recipe, batches) in activity.batchesByRecipe) {
            if (batches <= 0L) continue
            val (perBatch, source) = table.process(recipe, familyOfRecipe(recipe))
            if (perBatch == 0L) continue
            val mass = Tonnes.ofGrams(Math.multiplyExact(perBatch, batches))
            if (mass.isZero) continue
            raw += EmissionLine(SourceKind.PROCESS, recipe.key, EmissionScope.DIRECT, mass, source)
        }
        val overhead = table.overhead(activity.type)
        if (overhead > 0L) raw += EmissionLine(SourceKind.OVERHEAD, activity.type.key, EmissionScope.DIRECT, Tonnes.ofGrams(overhead), FactorSource.TABLE)
        val gridFactor = gridGramsPerUnit ?: table.electricityGramsPerUnit
        if (activity.electricityUsed.raw > 0L && gridFactor > 0L) {
            val mass = grams(activity.electricityUsed.raw, gridFactor)
            if (!mass.isZero) raw += EmissionLine(SourceKind.ELECTRICITY, "grid", EmissionScope.INDIRECT, mass, if (gridGramsPerUnit != null) FactorSource.TABLE else FactorSource.CATEGORY_DEFAULT)
        }
        if (effects.isEmpty()) return FacilityEmissions(activity, raw, Tonnes.ZERO)
        var avoided = Tonnes.ZERO
        val reduced = raw.map { line ->
            var surviving = Rate.ONE_PPM
            for (e in effects) surviving = mulDiv(surviving, e.survivingPpm(line.kind, line.key), Rate.ONE_PPM, RoundingMode.HALF_EVEN)
            val kept = Tonnes(mulDiv(line.amount.raw, surviving, Rate.ONE_PPM, RoundingMode.HALF_EVEN))
            avoided += line.amount - kept
            line.copy(amount = kept)
        }.filter { !it.amount.isZero }
        return FacilityEmissions(activity, reduced, avoided)
    }

    /**
     * The release of one batch of [recipe] on its own: fuels burned, process chemistry and the electricity it draws at
     * the default grid intensity. Used to price a recipe and to trace embedded carbon through a supply chain.
     */
    fun recipeBatch(recipe: RecipeDef): List<EmissionLine> {
        val out = ArrayList<EmissionLine>()
        for (input in recipe.inputs) {
            val (factor, source) = table.fuel(input.good, categoryOf(input.good))
            if (factor.gramsPerUnit == 0L) continue
            val mass = grams(input.quantity.raw, factor.gramsPerUnit)
            if (!mass.isZero) out += EmissionLine(SourceKind.FUEL, input.good.key, if (factor.biogenic) EmissionScope.BIOGENIC else EmissionScope.DIRECT, mass, source)
        }
        val (perBatch, source) = table.process(recipe.id, familyOfRecipe(recipe.id))
        if (perBatch > 0L) out += EmissionLine(SourceKind.PROCESS, recipe.id.key, EmissionScope.DIRECT, Tonnes.ofGrams(perBatch), source)
        if (recipe.energyPerBatch.raw > 0L && table.electricityGramsPerUnit > 0L) {
            out += EmissionLine(SourceKind.ELECTRICITY, "grid", EmissionScope.INDIRECT, grams(recipe.energyPerBatch.raw, table.electricityGramsPerUnit), FactorSource.CATEGORY_DEFAULT)
        }
        return out
    }

    /** Direct and electricity emissions of one batch, biogenic excluded: what the batch adds to a product's footprint. */
    fun batchTotal(recipe: RecipeDef): Tonnes =
        Tonnes.sum(recipeBatch(recipe).filter { it.scope != EmissionScope.BIOGENIC }.map { it.amount })
}

/**
 * Embedded carbon per unit of every good, found by walking the recipes that make it: a batch's own release plus the
 * embedded carbon of its inputs, shared among the batch's outputs by value. A good with several recipes gets the average
 * of them; a good nothing makes carries nothing. Chains that loop back on themselves are cut at the loop.
 */
class ProductFootprints(private val content: ContentRepository, private val calculator: EmissionCalculator, private val maxDepth: Int = 14) {
    private val memo = java.util.TreeMap<GoodId, Long>()

    /** Grams of CO2 embedded in one base unit of [good], including everything upstream. */
    fun gramsPerUnit(good: GoodId): Long = compute(good, java.util.TreeSet(), 0)

    /** Embedded mass in [quantity] of [good]. */
    fun embedded(good: GoodId, quantity: Quantity): Tonnes =
        Tonnes.ofGrams(mulDiv(quantity.raw, gramsPerUnit(good), Quantity.SCALE, RoundingMode.HALF_EVEN))

    private var cuts = 0

    private fun compute(good: GoodId, visiting: MutableSet<GoodId>, depth: Int): Long {
        memo[good]?.let { return it }
        if (depth >= maxDepth || !visiting.add(good)) { cuts++; return 0L }
        val before = cuts
        val makers = content.recipesProducing(good)
        var result = 0L
        if (makers.isNotEmpty()) {
            var sum = 0L
            for (recipe in makers) sum = Math.addExact(sum, perUnitFrom(recipe, good, visiting, depth))
            result = sum / makers.size
        }
        visiting.remove(good)
        // A value computed while a loop was being cut depends on the path taken, so only clean walks are remembered.
        if (cuts == before) memo[good] = result
        return result
    }

    private fun perUnitFrom(recipe: RecipeDef, good: GoodId, visiting: MutableSet<GoodId>, depth: Int): Long {
        var batchGrams = calculator.batchTotal(recipe).raw * 1_000L
        for (input in recipe.inputs) {
            val upstream = compute(input.good, visiting, depth + 1)
            batchGrams = Math.addExact(batchGrams, mulDiv(input.quantity.raw, upstream, Quantity.SCALE, RoundingMode.HALF_EVEN))
        }
        val outputs = recipe.outputs
        val valueOf = { g: GoodId, q: Quantity -> mulDiv(content.goodOrNull(g)?.referencePrice?.perUnit?.raw ?: 0L, q.raw, Quantity.SCALE, RoundingMode.HALF_EVEN) }
        val totalValue = outputs.sumOf { valueOf(it.good, it.quantity) }
        val mine = outputs.first { it.good == good }
        val share = if (totalValue <= 0L) Rate.ratio(1, outputs.size.toLong()).ppm else mulDiv(valueOf(good, mine.quantity), Rate.ONE_PPM, totalValue, RoundingMode.HALF_EVEN)
        val grams = mulDiv(batchGrams, share, Rate.ONE_PPM, RoundingMode.HALF_EVEN)
        return mulDiv(grams, Quantity.SCALE, mine.quantity.raw, RoundingMode.HALF_EVEN)
    }

    /** Embedded intensity of every tradable good that carries any carbon, highest first. */
    fun ranking(limit: Int = Int.MAX_VALUE): List<Pair<GoodId, Long>> =
        content.allGoods.filter { it.tradable }.map { it.id to gramsPerUnit(it.id) }.filter { it.second > 0L }
            .sortedWith(compareByDescending<Pair<GoodId, Long>> { it.second }.thenBy { it.first }).take(limit)
}
