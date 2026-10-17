package app.many.economytycoon.content

import app.many.economytycoon.content.packs.AerospacePack
import app.many.economytycoon.content.packs.AgriculturePack
import app.many.economytycoon.content.packs.BasePack
import app.many.economytycoon.content.packs.AutomotivePack
import app.many.economytycoon.content.packs.ChemicalsPack
import app.many.economytycoon.content.packs.ConstructionMaterialsPack
import app.many.economytycoon.content.packs.ConsumerPack
import app.many.economytycoon.content.packs.EnergyPack
import app.many.economytycoon.content.packs.FoodPack
import app.many.economytycoon.content.packs.ElectronicsPack
import app.many.economytycoon.content.packs.IndustryPack
import app.many.economytycoon.content.packs.MedicalDevicesPack
import app.many.economytycoon.content.packs.MiningPack
import app.many.economytycoon.content.packs.PharmaceuticalsPack
import app.many.economytycoon.content.packs.ServicesPack
import app.many.economytycoon.content.packs.TextilesPack

/** The content that ships with the game: every pack, merged and validated once. */
object ShippedContent {
    val packs: List<ContentPack> by lazy {
        listOf(
            BasePack.pack, AgriculturePack.pack, FoodPack.pack, MiningPack.pack, EnergyPack.pack, IndustryPack.pack,
            ConsumerPack.pack, ServicesPack.pack,
            ChemicalsPack.pack, PharmaceuticalsPack.pack, TextilesPack.pack, ConstructionMaterialsPack.pack,
            ElectronicsPack.pack, AutomotivePack.pack, AerospacePack.pack, MedicalDevicesPack.pack,
        )
    }

    val repository: ContentRepository by lazy { ContentRepository.of(packs) }
}
