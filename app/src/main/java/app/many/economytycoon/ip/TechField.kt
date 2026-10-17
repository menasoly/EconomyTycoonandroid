package app.many.economytycoon.ip

import app.many.economytycoon.content.TechCategory
import app.many.economytycoon.content.TechDef

/**
 * The classes a patent office files inventions under. Citations and design-around difficulty look at how close two
 * fields are; a patent in one field is rarely prior art for a patent in a distant one.
 */
enum class TechField(val label: String) {
    MANUFACTURING("Manufacturing processes"),
    TRANSPORT_SYSTEMS("Transport systems"),
    ENERGY_SYSTEMS("Energy systems"),
    ADVANCED_MATERIALS("Advanced materials"),
    COMPUTING("Computing and data"),
    BUSINESS_METHODS("Business methods"),
    COMMERCE("Commerce and retail"),
    FINANCIAL_METHODS("Financial methods"),
}

object TechFields {
    fun of(category: TechCategory): TechField = when (category) {
        TechCategory.PRODUCTION -> TechField.MANUFACTURING
        TechCategory.LOGISTICS -> TechField.TRANSPORT_SYSTEMS
        TechCategory.ENERGY -> TechField.ENERGY_SYSTEMS
        TechCategory.MATERIALS -> TechField.ADVANCED_MATERIALS
        TechCategory.DIGITAL -> TechField.COMPUTING
        TechCategory.MANAGEMENT -> TechField.BUSINESS_METHODS
        TechCategory.RETAIL -> TechField.COMMERCE
        TechCategory.FINANCE -> TechField.FINANCIAL_METHODS
    }

    fun of(def: TechDef): TechField = of(def.category)

    private val close: Map<Pair<TechField, TechField>, Long> = buildMap {
        fun link(a: TechField, b: TechField, ppm: Long) { put(a to b, ppm); put(b to a, ppm) }
        link(TechField.MANUFACTURING, TechField.ADVANCED_MATERIALS, 600_000L)
        link(TechField.MANUFACTURING, TechField.ENERGY_SYSTEMS, 400_000L)
        link(TechField.MANUFACTURING, TechField.COMPUTING, 350_000L)
        link(TechField.MANUFACTURING, TechField.TRANSPORT_SYSTEMS, 250_000L)
        link(TechField.ENERGY_SYSTEMS, TechField.ADVANCED_MATERIALS, 400_000L)
        link(TechField.ENERGY_SYSTEMS, TechField.TRANSPORT_SYSTEMS, 350_000L)
        link(TechField.COMPUTING, TechField.BUSINESS_METHODS, 500_000L)
        link(TechField.COMPUTING, TechField.FINANCIAL_METHODS, 450_000L)
        link(TechField.COMPUTING, TechField.COMMERCE, 300_000L)
        link(TechField.BUSINESS_METHODS, TechField.COMMERCE, 500_000L)
        link(TechField.BUSINESS_METHODS, TechField.FINANCIAL_METHODS, 450_000L)
        link(TechField.COMMERCE, TechField.TRANSPORT_SYSTEMS, 300_000L)
        link(TechField.COMMERCE, TechField.FINANCIAL_METHODS, 250_000L)
    }

    /** How close two fields are, ppm: 1.0 for the same field, 0 for unrelated ones. */
    fun affinityPpm(a: TechField, b: TechField): Long = if (a == b) 1_000_000L else close[a to b] ?: 0L
}
