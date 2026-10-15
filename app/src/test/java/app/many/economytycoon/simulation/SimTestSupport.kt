package app.many.economytycoon.simulation

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.content.packs.AgriculturePack
import app.many.economytycoon.content.packs.BasePack
import app.many.economytycoon.content.packs.ConsumerPack
import app.many.economytycoon.content.packs.EnergyPack
import app.many.economytycoon.content.packs.FoodPack
import app.many.economytycoon.content.packs.IndustryPack
import app.many.economytycoon.content.packs.MiningPack
import app.many.economytycoon.content.packs.ServicesPack
import app.many.economytycoon.world.WorldSize

object SimTestSupport {
    val content: ContentRepository by lazy {
        ContentRepository.of(listOf(
            BasePack.pack, AgriculturePack.pack, FoodPack.pack, MiningPack.pack, EnergyPack.pack,
            IndustryPack.pack, ConsumerPack.pack, ServicesPack.pack,
        ))
    }

    /** The shipped content plus a shed that needs water and wheat to build, for exercising material supply. */
    val contentWithShed: ContentRepository by lazy {
        ContentRepository.of(listOf(
            BasePack.pack, AgriculturePack.pack, FoodPack.pack, MiningPack.pack, EnergyPack.pack,
            IndustryPack.pack, ConsumerPack.pack, ServicesPack.pack,
            app.many.economytycoon.content.contentPack("shed-test", 1, 0) {
                facility("test_shed", "Test shed", app.many.economytycoon.content.FacilityClass.WAREHOUSE, app.many.economytycoon.content.IndustryFamily.CONSTRUCTION) {
                    cost = 40000.0; buildTicks = 6; lines = 0; storage = 1000.0; upkeep = 10.0
                    buildInput("water", 40.0)
                    buildInput("wheat", 10.0)
                }
            },
        ))
    }

    fun config(seed: Long = 7L, ai: Int = 4) = SimConfig(seed = seed, worldSize = WorldSize.SMALL, aiCompanies = ai)

    fun sim(seed: Long = 7L, ai: Int = 4) = Simulation(config(seed, ai), content)
}
