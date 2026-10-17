package app.many.economytycoon.marketing

import app.many.economytycoon.content.IncomeBand
import app.many.economytycoon.core.Money

/**
 * How one advertising channel behaves. Money is per day; shares are parts per million.
 *
 * Reach follows a saturating curve: with daily spend s per 1,000 people in the audience it is
 * `ceiling * s / (s + halfSpend)`, so the first money buys the most and the ceiling is never passed.
 */
data class ChannelSpec(
    /** The largest share of the audience this channel can ever reach. */
    val reachCeilingPpm: Long,
    /** Daily spend per 1,000 audience members that reaches half the ceiling. */
    val halfSpend: Money,
    /** Share of each exposure that turns into awareness. */
    val awarenessWeightPpm: Long,
    /** Share of each exposure that turns into loyalty (needs awareness to work). */
    val loyaltyWeightPpm: Long,
    /** Share of yesterday's exposure that is still felt today once the money stops. */
    val carryOverPpm: Long,
    /** Freshness a creative loses per day when the channel reaches the whole audience. */
    val wearPerDayPpm: Long,
    val minDailySpend: Money,
    val maxDailySpend: Money,
    /** Days of spend that must be paid up front when booking. */
    val depositDays: Int,
    /** Share of the unused deposit kept by the seller when the booking is cancelled. */
    val cancelFeePpm: Long,
    /** Days a new campaign takes to reach full strength. */
    val rampDays: Int,
    /** How well the channel suits each income band, indexed by [IncomeBand] ordinal. */
    val bandFitPpm: List<Long>,
) {
    init {
        require(reachCeilingPpm in 1..Ppm.ONE) { "reach ceiling out of range" }
        require(halfSpend.raw > 0L) { "half spend must be positive" }
        require(minDailySpend.raw > 0L && maxDailySpend >= minDailySpend) { "spend limits out of order" }
        require(bandFitPpm.size == IncomeBand.values().size) { "one fit per income band" }
        require(rampDays >= 1 && depositDays >= 0) { "bad timing" }
    }

    fun fit(band: IncomeBand): Long = bandFitPpm[band.ordinal]
}

/** The ways a company can put a product in front of people. */
enum class Channel(val label: String, val blurb: String, val spec: ChannelSpec) {
    LOCAL_PRESS(
        "Local press", "Cheap and steady. Reaches readers of the regional papers and fades slowly.",
        ChannelSpec(350_000, Money.units(2), 800_000, 150_000, 700_000, 18_000, Money.units(40), Money.units(8_000), 0, 100_000, 2, listOf(600_000, 1_000_000, 900_000)),
    ),
    RADIO(
        "Radio", "Broad daily reach for modest money. Commuters and shoppers hear it repeatedly.",
        ChannelSpec(550_000, Money.units(4), 750_000, 200_000, 650_000, 30_000, Money.units(80), Money.units(15_000), 0, 100_000, 3, listOf(900_000, 1_000_000, 700_000)),
    ),
    TELEVISION(
        "Television", "The biggest audience and the biggest bill. Slow to start, slow to forget, booked ahead.",
        ChannelSpec(850_000, Money.units(14), 800_000, 150_000, 850_000, 22_000, Money.units(400), Money.units(80_000), 7, 250_000, 7, listOf(900_000, 1_000_000, 850_000)),
    ),
    ONLINE(
        "Online", "Targeted and instant. Cheap to try, but people tire of the same banner quickly.",
        ChannelSpec(700_000, Money.units(6), 650_000, 300_000, 450_000, 45_000, Money.units(30), Money.units(40_000), 0, 0, 1, listOf(700_000, 1_000_000, 1_100_000)),
    ),
    SPONSORSHIP(
        "Sponsorship", "Lends the brand to events and teams. Prestige that builds loyalty more than awareness.",
        ChannelSpec(300_000, Money.units(10), 350_000, 600_000, 900_000, 8_000, Money.units(250), Money.units(50_000), 30, 400_000, 10, listOf(500_000, 900_000, 1_300_000)),
    ),
    IN_STORE(
        "In-store promotion", "Displays and samples at the shelf. Small audience, but they are already buying.",
        ChannelSpec(250_000, Money.units(3), 400_000, 450_000, 500_000, 25_000, Money.units(25), Money.units(6_000), 0, 100_000, 1, listOf(1_200_000, 1_000_000, 700_000)),
    ),
    LOYALTY_PROGRAMME(
        "Loyalty programme", "Points and member prices for repeat buyers. Builds loyalty, adds almost no awareness.",
        ChannelSpec(400_000, Money.units(5), 50_000, 900_000, 800_000, 6_000, Money.units(100), Money.units(25_000), 14, 200_000, 7, listOf(1_100_000, 1_000_000, 800_000)),
    );

    companion object {
        /** Channels in a fixed order, used wherever per-channel arrays are saved. */
        val ordered: List<Channel> = values().toList()
        fun byOrdinal(i: Int): Channel? = values().getOrNull(i)
    }
}

/** Which part of the population a campaign speaks to. */
enum class TargetSegment(val label: String, val bands: List<IncomeBand>) {
    EVERYONE("Everyone", IncomeBand.values().toList()),
    BUDGET("Budget shoppers", listOf(IncomeBand.LOW)),
    MAINSTREAM("Mainstream", listOf(IncomeBand.MIDDLE)),
    PREMIUM("Premium buyers", listOf(IncomeBand.HIGH));

    companion object {
        fun byOrdinal(i: Int): TargetSegment? = values().getOrNull(i)
    }
}
