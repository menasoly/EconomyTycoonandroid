package app.many.economytycoon.blueprint

import app.many.economytycoon.companies.PriceStrategy
import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.content.FacilityClass
import app.many.economytycoon.content.GoodCategory
import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.content.SkillBand
import app.many.economytycoon.content.TechCategory
import app.many.economytycoon.content.contentPack
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.LineId
import app.many.economytycoon.core.MachineTypeId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.TechId
import app.many.economytycoon.production.SchedulePolicy

object BlueprintTestSupport {
    val bakery = FacilityTypeId("bakery")
    val oven = MachineTypeId("oven")
    val mixer = MachineTypeId("mixer")
    val proOven = MachineTypeId("pro_oven")
    val makeFlour = RecipeId("make_flour")
    val makeBread = RecipeId("make_bread")
    val makeBreadPro = RecipeId("make_bread_pro")
    val makeSourdough = RecipeId("make_sourdough")
    val baker = RoleId("baker")
    val clerk = RoleId("clerk")
    val bread = GoodId("bread")
    val flour = GoodId("flour")
    val wheat = GoodId("wheat")
    val region = RegionId(1)

    val content: ContentRepository by lazy {
        ContentRepository.of(listOf(contentPack("test", 1, 4) {
            industry("baking", "Baking", IndustryFamily.FOOD)
            industry("mining", "Mining", IndustryFamily.MINING)
            industry("farming", "Farming", IndustryFamily.AGRICULTURE)
            good("wheat", "Wheat", GoodCategory.RAW, 10.0)
            good("flour", "Flour", GoodCategory.INTERMEDIATE, 25.0)
            good("bread", "Bread", GoodCategory.CONSUMER, 60.0, spoilagePpm = 50_000)
            good("ash", "Ash", GoodCategory.WASTE, 0.0, tradable = false)
            good("ore", "Ore", GoodCategory.RAW, 30.0)
            good("electricity", "Electricity", GoodCategory.ENERGY, 1.0)
            role("baker", "Baker", SkillBand.SKILLED, 100.0)
            role("clerk", "Clerk", SkillBand.UNSKILLED, 60.0)
            role("miner", "Miner", SkillBand.UNSKILLED, 80.0)
            tech("mixing", "Mixing", TechCategory.PRODUCTION, 1, 10_000.0, 10)
            tech("automation", "Automation", TechCategory.PRODUCTION, 2, 40_000.0, 20) { requires("mixing") }
            machine("oven", "Oven", 10_000.0, throughput = 1.25, maintenance = 5.0)
            machine("mixer", "Mixer", 4_000.0, maintenance = 2.0, tech = "mixing")
            machine("pro_oven", "Pro oven", 24_000.0, throughput = 1.6, maintenance = 9.0, tech = "automation")
            facility("bakery", "Bakery", FacilityClass.FACTORY, IndustryFamily.FOOD) { cost = 50_000.0; buildTicks = 4; lines = 3; storage = 1_000.0; upkeep = 20.0; staff("clerk", 1); buildInput("wheat", 10.0) }
            facility("mine", "Mine", FacilityClass.MINE, IndustryFamily.MINING) { cost = 80_000.0; buildTicks = 6; lines = 1; storage = 200.0; upkeep = 30.0; extracts = "ore" }
            facility("farm", "Farm", FacilityClass.FARM, IndustryFamily.AGRICULTURE) { cost = 30_000.0; buildTicks = 3; lines = 2; storage = 500.0; upkeep = 10.0 }
            facility("harbor", "Harbor", FacilityClass.PORT, IndustryFamily.LOGISTICS) { cost = 120_000.0; buildTicks = 8; lines = 1; storage = 2_000.0; upkeep = 60.0 }
            facility("lab_bakery", "Lab bakery", FacilityClass.FACTORY, IndustryFamily.FOOD) { cost = 90_000.0; buildTicks = 6; lines = 2; storage = 800.0; upkeep = 40.0; tech = "automation" }
            recipe("make_flour", "Mill flour", "baking", "bakery", 2) { input("wheat", 4.0); output("flour", 3.0); waste("ash", 1.0); labor("baker", 2); defectPpm = 0 }
            recipe("make_bread", "Bake bread", "baking", "bakery", 1) { input("flour", 2.0); output("bread", 4.0); labor("baker", 1); machine("oven"); defectPpm = 0 }
            recipe("make_bread_pro", "Bake bread (pro)", "baking", "bakery", 1) { input("flour", 2.0); output("bread", 5.0); labor("baker", 1); machine("pro_oven"); tech = "automation"; defectPpm = 0 }
            recipe("make_sourdough", "Bake sourdough", "baking", "bakery", 2) { input("flour", 3.0); output("bread", 7.0); labor("baker", 2); machine("oven"); machine("mixer"); tech = "mixing"; defectPpm = 0 }
            recipe("dig_ore", "Dig ore", "mining", "mine", 1) { output("ore", 5.0); labor("miner", 1); defectPpm = 0 }
            recipe("grow_wheat", "Grow wheat", "farming", "farm", 3) { output("wheat", 8.0); defectPpm = 0 }
        }))
    }

    fun line(recipe: RecipeId?, machines: List<MachineTypeId> = emptyList(), enabled: Boolean = true, priority: Int = 0) =
        LineSpec(priority, enabled, recipe, machines)

    /** A small but complete bakery set-up touching every part of the spec. */
    fun sampleSpec(): BlueprintSpec = BlueprintSpec(
        facilityType = bakery,
        lines = listOf(
            line(makeFlour, priority = 0),
            line(makeBread, listOf(oven), priority = 1),
            line(makeSourdough, listOf(oven, mixer), priority = 2),
        ),
        policy = SchedulePolicy.AUTO_MARGIN, qualityPpm = 300_000L,
        staff = listOf(StaffSpec(baker, 6, Money.units(105), 100_000L), StaffSpec(clerk, 1)),
        sells = listOf(SellSpec(bread, PriceStrategy.MARKET, 50_000L, Quantity.units(20), Quantity.units(100), 100_000L, true)),
        buys = listOf(BuySpec(wheat, Quantity.units(80), 1_200_000L, true)),
        storageExpansions = 1,
    ).normalized()

    fun sample(name: String = "Corner bakery") = Blueprint.of(sampleSpec(), name, "Three lines, auto margin.", "test@1.4", 12L)

    fun view(spec: BlueprintSpec = sampleSpec(), id: Long = 7L, running: Set<Int> = emptySet()): FacilityView = FacilityView(
        id = FacilityId(id), type = spec.facilityType, name = "Bakery $id", region = region, operating = true,
        lines = spec.lines.mapIndexed { i, l -> LineView(LineId(100L * id + i), l.priority, l.enabled, l.recipe, l.machines, i in running) },
        policy = spec.policy, qualityPpm = spec.qualityPpm, staff = spec.staff, sells = spec.sells, buys = spec.buys,
        storageCapacity = Quantity.units(1_250), storageExpansions = spec.storageExpansions,
    )

    fun known(vararg tech: String): ResearchView = ResearchView.of(tech.map { TechId(it) }.toSet())
}
