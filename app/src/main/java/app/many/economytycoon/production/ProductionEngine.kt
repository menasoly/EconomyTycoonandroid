package app.many.economytycoon.production

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.content.GoodCategory
import app.many.economytycoon.content.RecipeDef
import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

/** Everything the engine needs from the outside world for one tick. */
class TickContext(
    val tick: Tick,
    val random: RandomProvider,
    val labor: LaborSupply,
    val energy: EnergySupply,
    val resources: ResourceAccess,
    val modifiers: ProductionModifiers,
    val prices: PriceView,
)

/**
 * Runs lines forward one tick at a time. The engine owns no state of its own; facilities, lines and
 * machines are mutated in place and every outcome is described in a [FacilityReport].
 */
class ProductionEngine(private val content: ContentRepository, private val params: ProductionParams = ProductionParams()) {
    private val ONE = Rate.ONE_PPM

    fun stepFacility(f: Facility, ctx: TickContext): FacilityReport {
        val rep = FacilityReport(f.id)
        if (f.status != FacilityStatus.OPERATING) {
            for (l in f.lines) { l.blocked = Blocked.Shutdown; l.utilizationPpm = 0; rep.blocked[l.id] = Blocked.Shutdown }
            return rep
        }
        val type = content.facilityType(f.type)
        rep.upkeep = type.upkeepPerTick + Money(mulDiv(type.upkeepPerTick.raw, f.qualityAssurancePpm, 2 * ONE))
        f.unmaintainedTicks++
        f.conditionPpm = (f.conditionPpm - params.conditionDecayPpm).coerceAtLeast(0L)
        applySpoilage(f, rep)

        val overhead = if (type.overheadStaff.isEmpty()) ONE else ctx.labor.staffingPpm(f, type.overheadStaff, ctx.tick)
        val strike = ctx.labor.onStrike(f, ctx.tick)
        val rnd = ctx.random.stream("production", f.id.value)

        val ordered = f.lines.sortedWith(compareByDescending<ProductionLine> { it.priority }.thenBy { it.id.value })
        for (line in ordered) {
            stepLine(f, line, ctx, rep, rnd, overhead, strike)
            rep.blocked[line.id] = line.blocked
        }
        rollBreakdowns(f, ctx.tick, rnd, rep)
        updatePollution(f, rep)
        rep.energyUsed = f.energyUsed
        f.energyUsed = Quantity.ZERO
        return rep
    }

    private fun applySpoilage(f: Facility, rep: FacilityReport) {
        if (!content.params.storageSpoilageEnabled) return
        for ((good, slot) in f.inventory.entries()) {
            val def = content.goodOrNull(good) ?: continue
            if (def.spoilagePpm <= 0 || slot.quantity.raw <= 0) continue
            val valueBefore = slot.value
            val lost = f.inventory.spoil(good, def.spoilagePpm)
            if (lost.raw > 0) {
                rep.add(rep.spoiled, good, lost)
                rep.spoiledValue += valueBefore - slot.value
            }
        }
    }

    private fun stepLine(f: Facility, line: ProductionLine, ctx: TickContext, rep: FacilityReport, rnd: DeterministicRandom, overhead: Long, strike: Boolean) {
        line.utilizationPpm = 0
        if (!line.enabled) { line.blocked = Blocked.Shutdown; return }
        if (line.batch == null && f.policy == SchedulePolicy.AUTO_MARGIN) {
            val candidates = content.recipesForFacility(f.type).filter { r -> line.machines.map { it.type }.containsAll(r.machines) }
            Scheduler.choose(candidates, content, ctx.prices, f.inventory)?.let { line.recipe = it.id }
        }
        val rid = line.recipe
        if (rid == null) { line.blocked = Blocked.NoRecipe; return }
        val recipe = content.recipe(rid)
        if (strike) { line.blocked = Blocked.Strike; return }

        // Required machines installed and working.
        var machineThroughput = ONE
        for (mt in recipe.machines) {
            val m = line.machines.firstOrNull { it.type == mt }
            if (m == null) { line.blocked = Blocked.MissingMachine; return }
            if (m.isBroken(ctx.tick)) { line.blocked = Blocked.Broken; return }
            val def = content.machineType(mt)
            val perf = mulDiv(def.throughputPpm, m.conditionFactorPpm, ONE)
            machineThroughput = mulDiv(machineThroughput, perf, ONE)
        }

        val labor = adjustedLabor(f, recipe, ctx)
        val staffing = if (labor.isEmpty()) ONE else ctx.labor.staffingPpm(f, labor, ctx.tick)
        val staffed = mulDiv(staffing, overhead, ONE)
        if (staffed <= 0L) { line.blocked = Blocked.NoLabor; return }

        val tech = ONE + ctx.modifiers.throughputBonus(f.owner, recipe).ppm
        var rate = mulDiv(ONE, staffed, ONE)
        rate = mulDiv(rate, machineThroughput, ONE)
        rate = mulDiv(rate, f.conditionFactorPpm, ONE)
        rate = mulDiv(rate, tech, ONE)
        rate = rate / recipe.durationTicks

        // Energy is drawn in proportion to the work attempted; a shortfall slows the line.
        val type = content.facilityType(f.type)
        val energySavings = ctx.modifiers.energySavings(f.owner, recipe).ppm
        val perBatch = mulDiv(recipe.energyPerBatch.raw, rate, ONE)
        val wantedRaw = (type.baseEnergyPerLine.raw + perBatch)
        val wanted = Quantity(mulDiv(wantedRaw, ONE - energySavings.coerceIn(0, ONE), ONE))
        if (wanted.raw > 0) {
            val got = ctx.energy.draw(f, wanted)
            f.energyUsed += got
            if (got.raw <= 0L) { line.blocked = Blocked.NoEnergy; return }
            if (got < wanted) rate = mulDiv(rate, got.raw, wanted.raw)
        }
        var machineCost = Money.ZERO
        for (m in line.machines) machineCost += content.machineType(m.type).maintenanceCostPerTick
        rep.machineMaintenance += machineCost

        var work = rate
        var iterations = 0
        line.blocked = Blocked.None
        val maxIter = params.maxBatchesPerLinePerTick
        while (work > 0 && iterations < maxIter) {
            iterations++
            if (line.batch == null) {
                val why = tryStart(f, line, recipe, ctx, rep)
                if (why != Blocked.None) { line.blocked = why; break }
            }
            val b = line.batch!!
            val need = ONE - b.progressPpm
            val use = minOf(work, need)
            b.progressPpm += use
            work -= use
            if (b.progressPpm >= ONE) {
                if (!finishBatch(f, line, recipe, b, rnd, rep, ctx)) { line.blocked = Blocked.OutputFull; break }
            }
        }
        line.utilizationPpm = if (rate <= 0) 0 else mulDiv(rate - work, ONE, rate).coerceIn(0, ONE)
    }

    private fun adjustedLabor(f: Facility, recipe: RecipeDef, ctx: TickContext): List<app.many.economytycoon.content.LaborRequirement> {
        val savings = ctx.modifiers.laborSavings(f.owner, recipe).ppm.coerceIn(0, 900_000)
        if (savings == 0L) return recipe.labor
        return recipe.labor.map { l ->
            val adjusted = mulDiv(l.headcount.toLong(), ONE - savings, ONE).coerceAtLeast(1L).toInt()
            app.many.economytycoon.content.LaborRequirement(l.role, adjusted)
        }
    }

    private fun tryStart(f: Facility, line: ProductionLine, recipe: RecipeDef, ctx: TickContext, rep: FacilityReport): Blocked {
        val savings = ctx.modifiers.inputSavings(f.owner, recipe).ppm.coerceIn(0, 900_000)
        val needs = recipe.inputs.map { it to scaled(it.quantity, ONE - savings) }
        for ((amount, need) in needs) {
            if (f.inventory.available(amount.good) < need) return Blocked.MissingInput(amount.good)
        }
        // Extraction facilities draw their yield from the deposit they sit on.
        var yieldPpm = ONE + ctx.modifiers.yieldBonus(f.owner, recipe).ppm
        val type = content.facilityType(f.type)
        val extracts = type.extracts
        if (extracts != null) {
            val dep = f.depositId ?: return Blocked.NoDeposit
            val wanted = recipe.outputs.filter { it.good == extracts }.fold(Quantity.ZERO) { a, o -> a + o.quantity }
            if (wanted.raw > 0) {
                val got = ctx.resources.extract(dep, extracts, scaled(wanted, yieldPpm))
                if (got.raw <= 0L) return Blocked.NoDeposit
                val target = scaled(wanted, yieldPpm)
                yieldPpm = if (target.raw == 0L) 0L else mulDiv(yieldPpm, got.raw, target.raw)
            }
        }
        var inputValue = Money.ZERO
        for ((amount, need) in needs) {
            val catalyst = recipe.catalytic && recipe.outputs.any { it.good == amount.good }
            if (catalyst) continue
            val taken = f.inventory.remove(amount.good, need).getOrThrow()
            inputValue += taken
            rep.add(rep.consumed, amount.good, need)
        }
        rep.consumedValue += inputValue
        line.batch = ActiveBatch(recipe.id, 0L, inputValue, yieldPpm, conversionCost(recipe))
        return Blocked.None
    }

    /** Standard cost of labor and machine time for one batch, capitalised into the produced stock. */
    fun conversionCost(recipe: RecipeDef): Money {
        var total = 0L
        for (l in recipe.labor) total += content.role(l.role).baseWage.raw * l.headcount * recipe.durationTicks
        for (m in recipe.machines) total += content.machineType(m).maintenanceCostPerTick.raw * recipe.durationTicks
        return Money(total)
    }

    private fun scaled(q: Quantity, ppm: Long) = Quantity(mulDiv(q.raw, ppm, ONE))

    private fun finishBatch(f: Facility, line: ProductionLine, recipe: RecipeDef, b: ActiveBatch, rnd: DeterministicRandom, rep: FacilityReport, ctx: TickContext): Boolean {
        val defect = rnd.chance(Rate(defectChancePpm(f, line, recipe, ctx)))
        val share = if (defect) params.defectOutputPpm else ONE
        val outputs = recipe.outputs.map { it to Quantity(mulDiv(mulDiv(it.quantity.raw, b.yieldPpm, ONE), share, ONE)) }.filter { it.second.raw > 0 }
        var volume = 0L
        for ((o, q) in outputs) volume += f.inventory.volume(o.good, q).raw
        if (volume > f.inventory.free().raw) return false

        val totalValue = b.inputValue + b.conversionCost
        val weights = LongArray(outputs.size) { i ->
            val (o, q) = outputs[i]
            val w = ctx.prices.price(o.good).valueOf(q).raw
            if (w > 0) w else q.raw
        }
        val parts = if (outputs.isEmpty()) emptyArray() else if (weights.all { it == 0L }) totalValue.split(LongArray(outputs.size) { 1L }) else totalValue.split(weights)
        for (i in outputs.indices) {
            val (o, q) = outputs[i]
            f.inventory.add(o.good, q, parts[i], allowOverflow = true)
            rep.add(rep.produced, o.good, q)
        }
        rep.producedValue += if (outputs.isEmpty()) Money.ZERO else totalValue
        for (w in recipe.waste) {
            f.inventory.add(w.good, w.quantity, Money.ZERO, allowOverflow = true)
            rep.add(rep.wasteProduced, w.good, w.quantity)
        }
        for (mt in recipe.machines) {
            val m = line.machines.firstOrNull { it.type == mt } ?: continue
            MachineWear.wear(m, content.machineType(mt), 1)
        }
        line.batch = null
        line.batchesCompleted++
        rep.batches++
        if (defect) rep.defects++
        return true
    }

    fun defectChancePpm(f: Facility, line: ProductionLine, recipe: RecipeDef, ctx: TickContext): Long {
        var p = recipe.baseDefectPpm
        p = mulDiv(p, ONE - f.qualityAssurancePpm.coerceIn(0, ONE), ONE)
        var wornShare = 0L
        for (mt in recipe.machines) {
            val m = line.machines.firstOrNull { it.type == mt } ?: continue
            wornShare += ONE - m.conditionPpm
        }
        p += wornShare / 8
        p -= ctx.modifiers.qualityBonus(f.owner, recipe).ppm
        return p.coerceIn(0, ONE)
    }

    private fun rollBreakdowns(f: Facility, tick: Tick, rnd: DeterministicRandom, rep: FacilityReport) {
        for (line in f.lines) for (m in line.machines) {
            if (line.batch == null && line.blocked != Blocked.None) continue
            if (MachineWear.rollBreakdown(m, rnd, tick)) rep.breakdowns++
        }
    }

    private fun updatePollution(f: Facility, rep: FacilityReport) {
        var wasteUnits = 0L
        for ((g, s) in f.inventory.entries()) {
            val def = content.goodOrNull(g) ?: continue
            if (def.category == GoodCategory.WASTE) wasteUnits += s.quantity.raw / Quantity.SCALE
        }
        f.pollutionPpm = (f.pollutionPpm + wasteUnits * params.pollutionPerWasteUnitPpm - params.pollutionDecayPpm).coerceAtLeast(0L)
        if (f.pollutionPpm >= ONE) rep.pollutionViolation = true
    }
}
