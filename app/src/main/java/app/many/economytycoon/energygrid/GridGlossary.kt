package app.many.economytycoon.energygrid

/** Plain-language entries for the electricity screens and help pages. */
object GridGlossary {
    data class Entry(val key: String, val term: String, val text: String)

    val entries: List<Entry> = listOf(
        Entry("merit-order", "Merit order", "Plants are called on from the cheapest to run to the dearest until demand is met. The last one called sets the price for everyone."),
        Entry("spot-price", "Pool price", "What each megawatt-hour is worth in a given hour, set by the dearest plant needed. It rises steeply when spare capacity runs low."),
        Entry("reserve-margin", "Reserve margin", "Reliable capacity above the peak demand, as a share of the peak. Grids aim for a margin so that a few failures do not cause a blackout."),
        Entry("curtailment", "Curtailment", "Wind and solar are switched off when there is more supply than demand and nowhere to store or send it."),
        Entry("load-shedding", "Load shedding", "When supply falls short, the grid cuts off customers in a set order: interruptible industry, then shops, then factories, then homes."),
        Entry("blackout", "Blackout", "A stretch of hours in which some customers had no power. Factories caught by it lose condition."),
        Entry("ppa", "Power purchase agreement", "A long contract that fixes the price a buyer pays a seller. The pool still moves the energy; each month the two settle the difference."),
        Entry("capacity-market", "Capacity market", "Plants are paid each day for being available, whether or not they run, so that enough exist to cover the peak."),
        Entry("interconnector", "Interconnector", "A link between two grids. Power flows from the cheaper side to the dearer one, less losses on the way."),
        Entry("storage", "Storage", "Batteries charge when power is cheap and sell it back when it is dear. They lose some energy on the round trip."),
        Entry("load-factor", "Load factor", "Average demand divided by peak demand. A flat load is easy to supply; a peaky one needs plant that stands idle most of the year."),
        Entry("carbon-intensity", "Carbon intensity", "Grams of carbon dioxide for each kilowatt-hour made, averaged over everything the grid produced."),
    )

    fun find(key: String): Entry? = entries.firstOrNull { it.key == key }
}
