package app.many.economytycoon.production

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.content.FacilityClass
import app.many.economytycoon.content.GoodCategory
import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.content.SkillBand
import app.many.economytycoon.content.contentPack
import app.many.economytycoon.content.LaborRequirement
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.DepositId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Price
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.Tick

object ProductionTestSupport {
    val wheat = GoodId("wheat"); val flour = GoodId("flour"); val bread = GoodId("bread"); val ash = GoodId("ash"); val ore = GoodId("ore")
    val company = CompanyId(1)
    val region = RegionId(1)

    val content: ContentRepository by lazy {
        ContentRepository.of(listOf(contentPack("test", 1, 0) {
            industry("baking", "Baking", IndustryFamily.FOOD)
            industry("mining", "Mining", IndustryFamily.MINING)
            good("wheat", "Wheat", GoodCategory.RAW, 10.0)
            good("flour", "Flour", GoodCategory.INTERMEDIATE, 25.0)
            good("bread", "Bread", GoodCategory.CONSUMER, 40.0, spoilagePpm = 50_000)
            good("ash", "Ash", GoodCategory.WASTE, 0.0)
            good("ore", "Ore", GoodCategory.RAW, 30.0)
            role("baker", "Baker", SkillBand.SKILLED, 100.0)
            role("miner", "Miner", SkillBand.UNSKILLED, 80.0)
            machine("oven", "Oven", 10_000.0, throughput = 1.25, wearPerBatchPpm = 100_000)
            machine("mixer", "Mixer", 4_000.0, wearPerBatchPpm = 1_000, tech = "mixing")
            facility("bakery", "Bakery", FacilityClass.FACTORY, IndustryFamily.FOOD) { cost = 50_000.0; buildTicks = 4; lines = 2; storage = 1_000.0; upkeep = 20.0 }
            facility("mine", "Mine", FacilityClass.MINE, IndustryFamily.MINING) { cost = 80_000.0; buildTicks = 6; lines = 1; storage = 200.0; upkeep = 30.0; extracts = "ore"; buildInput("wheat", 10.0) }
            recipe("make_flour", "Mill flour", "baking", "bakery", 2) { input("wheat", 4.0); output("flour", 3.0); waste("ash", 1.0); labor("baker", 2); defectPpm = 0 }
            recipe("make_bread", "Bake bread", "baking", "bakery", 1) { input("flour", 2.0); output("bread", 4.0); labor("baker", 1); machine("oven"); defectPpm = 0 }
            recipe("make_bread_def", "Bake bread (flawed)", "baking", "bakery", 1) { input("flour", 2.0); output("bread", 4.0); defectPpm = 1_000_000 }
            recipe("dig_ore", "Dig ore", "mining", "mine", 1) { output("ore", 5.0); labor("miner", 1); defectPpm = 0 }
        }))
    }

    class FakeLabor(var staffingPpm: Long = 1_000_000L, var strike: Boolean = false) : LaborSupply {
        override fun staffingPpm(facility: Facility, required: List<LaborRequirement>, tick: Tick) = if (required.isEmpty()) 1_000_000L else staffingPpm
        override fun onStrike(facility: Facility, tick: Tick) = strike
    }

    class FakeEnergy(var available: Long = Long.MAX_VALUE / 4) : EnergySupply {
        var drawn = 0L
        override fun draw(facility: Facility, wanted: Quantity): Quantity {
            val g = minOf(wanted.raw, available); available -= g; drawn += g; return Quantity(g)
        }
    }

    class FakeResources(var reserve: Long = 1_000_000L) : ResourceAccess {
        override fun extract(deposit: DepositId, good: GoodId, wanted: Quantity): Quantity {
            val g = minOf(wanted.raw, reserve); reserve -= g; return Quantity(g)
        }
    }

    val flatPrices = PriceView { g -> content.goodOrNull(g)?.referencePrice ?: Price(Money.ZERO) }

    class Rig(seed: Long = 7) {
        val ids = IdAllocators()
        val svc = ProductionService(ids, content)
        val random = RandomProvider(seed)
        val labor = FakeLabor()
        val energy = FakeEnergy()
        val resources = FakeResources()
        var tick = 0L
        var modifiers: ProductionModifiers = NoModifiers

        fun ctx(): TickContext = TickContext(Tick(tick), random, labor, energy, resources, modifiers, flatPrices)

        fun step(): ProductionReport { tick++; return svc.step(ctx()) }
        fun run(n: Int): List<ProductionReport> = (1..n).map { step() }

        fun bakery(withOven: Boolean = false): Facility {
            val f = svc.grantFacility(company, app.many.economytycoon.core.FacilityTypeId("bakery"), region, null, null, null, Tick(0))
            if (withOven) svc.installMachine(company, f.id, f.lines[0].id, app.many.economytycoon.core.MachineTypeId("oven"), Tick(0)).getOrThrow()
            return f
        }

        fun stock(f: Facility, good: GoodId, units: Double, unitPrice: Double) {
            val q = Quantity((units * 1000).toLong())
            f.inventory.add(good, q, Money((units * unitPrice * 10_000).toLong()))
        }

        fun hash(): String = CanonicalHasher().also { svc.hashInto(it) }.finish().toHex()
    }
}
