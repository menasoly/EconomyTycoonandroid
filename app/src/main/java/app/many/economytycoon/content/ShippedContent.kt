package app.many.economytycoon.content

import app.many.economytycoon.content.packs.AgriculturePack
import app.many.economytycoon.content.packs.BasePack
import app.many.economytycoon.content.packs.ConsumerPack
import app.many.economytycoon.content.packs.EnergyPack
import app.many.economytycoon.content.packs.FoodPack
import app.many.economytycoon.content.packs.IndustryPack
import app.many.economytycoon.content.packs.MiningPack
import app.many.economytycoon.content.packs.ServicesPack

/** The content that ships with the game: every pack, merged and validated once. */
object ShippedContent {
    val packs: List<ContentPack> by lazy {
        listOf(BasePack.pack, AgriculturePack.pack, FoodPack.pack, MiningPack.pack, EnergyPack.pack, IndustryPack.pack, ConsumerPack.pack, ServicesPack.pack)
    }

    val repository: ContentRepository by lazy { ContentRepository.of(packs) }
}
