package app.many.economytycoon.research

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.content.EffectKind
import app.many.economytycoon.content.FacilityClass
import app.many.economytycoon.content.GoodCategory
import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.content.SkillBand
import app.many.economytycoon.content.TechCategory
import app.many.economytycoon.content.contentPack
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick

object ResearchTestSupport {
    val content: ContentRepository by lazy {
        ContentRepository.of(listOf(contentPack("research-test", 1, 0) {
            industry("baking", "Baking", IndustryFamily.FOOD)
            industry("mining", "Mining", IndustryFamily.MINING)
            good("wheat", "Wheat", GoodCategory.RAW, 10.0)
            good("flour", "Flour", GoodCategory.INTERMEDIATE, 25.0)
            good("bread", "Bread", GoodCategory.CONSUMER, 40.0)
            good("ore", "Ore", GoodCategory.RAW, 30.0)
            role("baker", "Baker", SkillBand.SKILLED, 100.0)
            facility("bakery", "Bakery", FacilityClass.FACTORY, IndustryFamily.FOOD) { cost = 50_000.0; buildTicks = 4; lines = 2; storage = 1_000.0; upkeep = 20.0 }
            recipe("make_flour", "Mill flour", "baking", "bakery", 2) { input("wheat", 4.0); output("flour", 3.0); labor("baker", 2) }
            recipe("make_bread", "Bake bread", "baking", "bakery", 1) { input("flour", 2.0); output("bread", 4.0); labor("baker", 1); tech = "ovens" }
            recipe("dig_ore", "Dig ore", "mining", "bakery", 1) { output("ore", 5.0); labor("baker", 1) }

            tech("milling", "Milling", TechCategory.PRODUCTION, 1, 1_000.0, 10) {
                effect(EffectKind.THROUGHPUT_BONUS, 0.10, industry = "baking")
            }
            tech("ovens", "Ovens", TechCategory.PRODUCTION, 1, 2_000.0, 20) {
                requires("milling")
                effect(EffectKind.YIELD_BONUS, 0.05, recipe = "make_bread")
                unlocksRecipe("make_bread")
            }
            tech("efficiency", "Efficiency", TechCategory.MANAGEMENT, 2, 4_000.0, 40) {
                requires("milling")
                obsolescenceTicks = 100
                effect(EffectKind.INPUT_SAVINGS, 0.20)
                effect(EffectKind.TRANSPORT_SAVINGS, 0.10)
            }
            tech("megamill", "Mega mill", TechCategory.PRODUCTION, 3, 9_000.0, 60) {
                requires("ovens", "efficiency")
                effect(EffectKind.THROUGHPUT_BONUS, 0.50)
                effect(EffectKind.INPUT_SAVINGS, 0.90)
            }
        }))
    }

    class Funding : ResearchFunding {
        val points = HashMap<CompanyId, Long>()
        var spent = 0L
        override fun available(company: CompanyId) = points[company] ?: 0L
        override fun spend(company: CompanyId, points: Long): Boolean {
            val have = this.points[company] ?: 0L
            if (points > have) return false
            this.points[company] = have - points; spent += points; return true
        }
        fun give(company: CompanyId, n: Long) { points[company] = (points[company] ?: 0L) + n }
    }

    class Payments : LicensePayer {
        val log = ArrayList<Triple<CompanyId, CompanyId, Money>>()
        var allow = true
        override fun pay(from: CompanyId, to: CompanyId, amount: Money, memo: String): Boolean { if (allow) log += Triple(from, to, amount); return allow }
    }

    class Rig {
        val funding = Funding()
        val payments = Payments()
        val service = ResearchService(content, funding, licensePayer = payments)
        val effects = TechEffects(service)
        val a = CompanyId(1)
        val b = CompanyId(2)
        var tick = 0L
        fun run(n: Int): List<ResearchEvent> {
            val out = ArrayList<ResearchEvent>()
            repeat(n) { tick++; effects.now = Tick(tick); out += service.step(Tick(tick)) }
            return out
        }
    }
}
