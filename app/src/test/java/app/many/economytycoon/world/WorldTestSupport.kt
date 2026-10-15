package app.many.economytycoon.world

import app.many.economytycoon.content.ContentRepository
import app.many.economytycoon.content.packs.AgriculturePack
import app.many.economytycoon.content.packs.BasePack
import app.many.economytycoon.content.packs.ConsumerPack
import app.many.economytycoon.content.packs.EnergyPack
import app.many.economytycoon.content.packs.FoodPack
import app.many.economytycoon.content.packs.IndustryPack
import app.many.economytycoon.content.packs.MiningPack
import app.many.economytycoon.content.packs.ServicesPack
import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.IdAllocators

object WorldTestSupport {
    val content: ContentRepository by lazy {
        ContentRepository.of(listOf(
            BasePack.pack, AgriculturePack.pack, FoodPack.pack, MiningPack.pack, EnergyPack.pack,
            IndustryPack.pack, ConsumerPack.pack, ServicesPack.pack,
        ))
    }

    fun generate(seed: Long, size: WorldSize = WorldSize.MEDIUM): Pair<WorldState, IdAllocators> {
        val ids = IdAllocators()
        return WorldGenerator(content, ids).generate(WorldParams(seed, size)) to ids
    }

    fun hash(w: WorldState) = CanonicalHasher().also { w.hashInto(it) }.finish()
}
