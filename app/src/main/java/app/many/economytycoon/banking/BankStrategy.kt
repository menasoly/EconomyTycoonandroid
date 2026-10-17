package app.many.economytycoon.banking

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.DeterministicRandom
import app.many.economytycoon.core.Rate

enum class BankArchetype(val label: String) {
    CONSERVATIVE_RETAIL("Conservative retail bank"),
    BALANCED("Balanced universal bank"),
    AGGRESSIVE_GROWTH("Aggressive growth lender"),
    WHOLESALE_TRADER("Wholesale-funded trader"),
    PROPERTY_LENDER("Property lender"),
}

/**
 * The temperament of a computer-controlled bank. Every trait is a rate: how boldly it grows, how much risk it takes,
 * how hard it competes for deposits and how much cushion it keeps over the regulatory requirements.
 */
data class BankStrategy(
    val archetype: BankArchetype,
    val growthAppetite: Rate,
    val riskAppetite: Rate,
    val depositAggression: Rate,
    val payout: Rate,
    /** CET1 it wants to hold above the requirement including buffers. */
    val capitalCushion: Rate,
    /** Liquidity coverage it wants above 100%. */
    val liquidityCushion: Rate,
    /** Share of assets it is willing to fund in the wholesale market. */
    val wholesaleReliance: Rate,
    /** Share of assets it wants in securities. */
    val securitiesShare: Rate,
    /** Months between planning rounds. */
    val thinkMonths: Int,
    /** Preference for each sector, 0 to 1,000,000. */
    val sectorBias: Map<LoanSector, Rate>,
) {
    init {
        require(thinkMonths >= 1) { "think interval must be positive" }
        require(LoanSector.values().all { sectorBias.containsKey(it) }) { "a bias is needed for every sector" }
    }

    fun biasOf(sector: LoanSector): Rate = sectorBias.getValue(sector)

    fun hashInto(h: CanonicalHasher) {
        h.putInt(archetype.ordinal).putRate(growthAppetite).putRate(riskAppetite).putRate(depositAggression).putRate(payout)
            .putRate(capitalCushion).putRate(liquidityCushion).putRate(wholesaleReliance).putRate(securitiesShare).putInt(thinkMonths)
        for (s in LoanSector.values()) h.putRate(biasOf(s))
    }
}

object BankStrategies {
    private fun bias(vararg p: Long): Map<LoanSector, Rate> {
        require(p.size == LoanSector.values().size) { "one bias per sector" }
        return LoanSector.values().withIndex().associate { (i, s) -> s to Rate(p[i]) }
    }

    fun of(a: BankArchetype): BankStrategy = when (a) {
        BankArchetype.CONSERVATIVE_RETAIL -> BankStrategy(
            a, Rate(300_000), Rate(150_000), Rate(350_000), Rate(500_000), Rate.percent(4), Rate.percent(50), Rate.percent(5), Rate.percent(30), 3,
            bias(900_000, 500_000, 400_000, 600_000, 200_000, 400_000),
        )
        BankArchetype.BALANCED -> BankStrategy(
            a, Rate(500_000), Rate(450_000), Rate(500_000), Rate(400_000), Rate.percent(3), Rate.percent(30), Rate.percent(12), Rate.percent(22), 2,
            bias(700_000, 600_000, 600_000, 700_000, 500_000, 500_000),
        )
        BankArchetype.AGGRESSIVE_GROWTH -> BankStrategy(
            a, Rate(900_000), Rate(800_000), Rate(850_000), Rate(150_000), Rate.percent(2), Rate.percent(12), Rate.percent(22), Rate.percent(12), 1,
            bias(500_000, 900_000, 950_000, 800_000, 700_000, 600_000),
        )
        BankArchetype.WHOLESALE_TRADER -> BankStrategy(
            a, Rate(650_000), Rate(550_000), Rate(200_000), Rate(350_000), Rate.percent(2) + Rate.basisPoints(50), Rate.percent(20), Rate.percent(30), Rate.percent(30), 2,
            bias(300_000, 200_000, 400_000, 950_000, 600_000, 900_000),
        )
        BankArchetype.PROPERTY_LENDER -> BankStrategy(
            a, Rate(700_000), Rate(650_000), Rate(600_000), Rate(300_000), Rate.percent(3), Rate.percent(20), Rate.percent(15), Rate.percent(15), 2,
            bias(950_000, 250_000, 450_000, 400_000, 950_000, 200_000),
        )
    }

    /** Draws an archetype and nudges each trait so that banks of one type still differ. */
    fun roll(random: DeterministicRandom): BankStrategy {
        val a = BankArchetype.values()[random.nextInt(BankArchetype.values().size)]
        val base = of(a)
        fun jitter(r: Rate) = Rate((r.ppm + random.nextInRange(-60_000L, 60_000L)).coerceIn(10_000L, 990_000L))
        return base.copy(
            growthAppetite = jitter(base.growthAppetite), riskAppetite = jitter(base.riskAppetite),
            depositAggression = jitter(base.depositAggression), payout = jitter(base.payout),
            sectorBias = base.sectorBias.mapValues { jitter(it.value) },
        )
    }
}
