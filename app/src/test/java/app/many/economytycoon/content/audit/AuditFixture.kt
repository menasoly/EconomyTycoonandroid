package app.many.economytycoon.content.audit

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.content.FacilityClass
import app.many.economytycoon.content.GoodCategory
import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.content.SkillBand
import app.many.economytycoon.content.contentPack

/** A small world of content with one example of everything the audit looks for. */
object AuditFixture {
    val content: ContentRepository by lazy {
        ContentRepository.of(listOf(contentPack("audit-fixture", 1, 0) {
            industry("basics", "Basics", IndustryFamily.MINING)
            good("electricity", "Electricity", GoodCategory.ENERGY, 8.0)
            good("ore", "Ore", GoodCategory.RAW, 10.0)
            good("bar", "Bar", GoodCategory.INTERMEDIATE, 40.0)
            good("junk", "Junk", GoodCategory.INTERMEDIATE, 1.0)
            good("gem", "Gem", GoodCategory.CONSUMER, 100.0)
            good("mystery", "Mystery", GoodCategory.RAW, 5.0)
            good("loop_a", "Loop A", GoodCategory.INTERMEDIATE, 5.0)
            good("loop_b", "Loop B", GoodCategory.INTERMEDIATE, 5.0)
            good("orphan", "Orphan", GoodCategory.INTERMEDIATE, 5.0)
            role("worker", "Worker", SkillBand.UNSKILLED, 10.0)

            facility("mine", "Mine", FacilityClass.MINE, IndustryFamily.MINING) {
                cost = 1_000.0; buildTicks = 1; lines = 2; upkeep = 10.0; extracts = "ore"
            }
            facility("smelter", "Smelter", FacilityClass.FACTORY, IndustryFamily.MINING) {
                cost = 100_000.0; buildTicks = 1; lines = 2; upkeep = 50.0
            }
            facility("press", "Press", FacilityClass.FACTORY, IndustryFamily.MINING) {
                cost = 5_000.0; buildTicks = 1; lines = 1; upkeep = 1.0
            }
            facility("vault", "Vault", FacilityClass.FACTORY, IndustryFamily.MINING) {
                cost = 50_000.0; buildTicks = 1; lines = 1; upkeep = 1.0
            }
            facility("loop_shop", "Loop shop", FacilityClass.FACTORY, IndustryFamily.MINING) {
                cost = 5_000.0; buildTicks = 1; lines = 1; upkeep = 1.0
            }
            facility("empty_hall", "Empty hall", FacilityClass.WAREHOUSE, IndustryFamily.MINING) {
                cost = 5_000.0; buildTicks = 1; lines = 2; upkeep = 1.0
            }

            recipe("dig_ore", "Dig ore", "basics", "mine", 1) { output("ore", 10.0); labor("worker", 2) }
            recipe("smelt", "Smelt bar", "basics", "smelter", 1) { input("ore", 2.0); output("bar", 1.0); labor("worker", 1) }
            recipe("press_junk", "Press junk", "basics", "press", 1) { input("ore", 2.0); output("junk", 1.0) }
            recipe("cut_gem", "Cut gem", "basics", "vault", 1) { input("mystery", 1.0); output("gem", 1.0); labor("worker", 1) }
            recipe("make_a", "Make A", "basics", "loop_shop", 1) { input("loop_b", 1.0); output("loop_a", 1.0); labor("worker", 1) }
            recipe("make_b", "Make B", "basics", "loop_shop", 1) { input("loop_a", 1.0); output("loop_b", 1.0); labor("worker", 1) }
            recipe("make_orphan", "Make orphan", "basics", "smelter", 1) { input("ore", 1.0); output("orphan", 1.0); labor("worker", 1) }
        }))
    }
}
