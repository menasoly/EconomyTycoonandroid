package app.many.economytycoon.content

import app.many.economytycoon.content.packs.AerospacePack
import app.many.economytycoon.content.packs.AutomotivePack
import app.many.economytycoon.content.packs.ChemicalsPack
import app.many.economytycoon.content.packs.ConstructionMaterialsPack
import app.many.economytycoon.content.packs.ElectronicsPack
import app.many.economytycoon.content.packs.MedicalDevicesPack
import app.many.economytycoon.content.packs.PharmaceuticalsPack
import app.many.economytycoon.content.packs.TextilesPack

/**
 * The shipped content plus the industry expansion packs, merged and indexed once. It exists so the expansion
 * can be built, validated and browsed without touching [ShippedContent]; once the shipped list includes the
 * expansion packs this object keeps working unchanged because it only appends packs that are not yet present.
 */
object ExpandedContent {
    /** Expansion packs in dependency order: a pack only uses goods, roles and technologies of the packs before it. */
    val expansionPacks: List<ContentPack> by lazy {
        listOf(
            ChemicalsPack.pack, PharmaceuticalsPack.pack, TextilesPack.pack, ConstructionMaterialsPack.pack,
            ElectronicsPack.pack, AutomotivePack.pack, AerospacePack.pack, MedicalDevicesPack.pack,
        )
    }

    val packs: List<ContentPack> by lazy {
        val present = ShippedContent.packs.map { it.version.packId }.toSet()
        ShippedContent.packs + expansionPacks.filter { it.version.packId !in present }
    }

    val repository: ContentRepository by lazy { ContentRepository.of(packs) }

    /** Pack ids of the expansion, in dependency order. */
    val expansionPackIds: List<String> get() = expansionPacks.map { it.version.packId }

    /** Packs that existed before the expansion. */
    val basePacks: List<ContentPack> get() = packs.filter { it.version.packId !in expansionPackIds.toSet() }

    fun expansionPack(packId: String): ContentPack =
        expansionPacks.firstOrNull { it.version.packId == packId } ?: error("unknown expansion pack $packId")
}
