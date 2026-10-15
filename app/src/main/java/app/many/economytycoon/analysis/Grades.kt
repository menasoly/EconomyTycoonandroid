package app.many.economytycoon.analysis

/** How a reading compares with what a business of this kind can live with. */
enum class Grade(val label: String, val rank: Int) {
    STRONG("Strong", 3),
    SOUND("Sound", 2),
    WATCH("Watch", 1),
    WEAK("Weak", 0),
}

/** Which way a ratio should move to be healthier. */
enum class Direction { HIGHER_IS_BETTER, LOWER_IS_BETTER }

/**
 * Cut-offs for one ratio. For [Direction.HIGHER_IS_BETTER] a value at or above [strong] is strong, at or above
 * [sound] is sound, at or above [watch] needs watching and anything lower is weak; the other direction mirrors it.
 */
data class Band(val direction: Direction, val strong: Long, val sound: Long, val watch: Long) {
    fun grade(value: Long): Grade = when (direction) {
        Direction.HIGHER_IS_BETTER -> when {
            value >= strong -> Grade.STRONG
            value >= sound -> Grade.SOUND
            value >= watch -> Grade.WATCH
            else -> Grade.WEAK
        }
        Direction.LOWER_IS_BETTER -> when {
            value <= strong -> Grade.STRONG
            value <= sound -> Grade.SOUND
            value <= watch -> Grade.WATCH
            else -> Grade.WEAK
        }
    }
}

/** The named ratios the analysis grades, each with its cut-offs and a plain description. */
enum class Measure(val label: String, val band: Band, val hint: String) {
    CURRENT("Current ratio", Band(Direction.HIGHER_IS_BETTER, 2_000_000, 1_400_000, 1_000_000), "Short-term assets for each unit of short-term debts."),
    CASH("Cash ratio", Band(Direction.HIGHER_IS_BETTER, 800_000, 400_000, 150_000), "Cash on hand for each unit of short-term debts."),
    DEBT_TO_EQUITY("Debt to equity", Band(Direction.LOWER_IS_BETTER, 500_000, 1_200_000, 2_500_000), "Borrowing for each unit of owners' money."),
    INTEREST_COVER("Interest cover", Band(Direction.HIGHER_IS_BETTER, 8_000_000, 4_000_000, 1_500_000), "Operating profit for each unit of interest."),
    GROSS_MARGIN("Gross margin", Band(Direction.HIGHER_IS_BETTER, 400_000, 250_000, 100_000), "Share of sales left after materials."),
    NET_MARGIN("Net margin", Band(Direction.HIGHER_IS_BETTER, 120_000, 60_000, 10_000), "Share of sales kept as profit."),
    RETURN_ON_EQUITY("Return on equity", Band(Direction.HIGHER_IS_BETTER, 200_000, 100_000, 30_000), "Yearly profit for each unit of owners' money."),
    RETURN_ON_ASSETS("Return on assets", Band(Direction.HIGHER_IS_BETTER, 100_000, 50_000, 15_000), "Yearly profit for each unit of assets."),
    EQUITY_RATIO("Equity ratio", Band(Direction.HIGHER_IS_BETTER, 600_000, 400_000, 200_000), "Share of assets paid for by the owners."),
    CASH_CYCLE("Cash cycle (days)", Band(Direction.LOWER_IS_BETTER, 30, 60, 120), "Days money is tied up between paying for goods and being paid."),
    ASSET_TURNOVER("Asset turnover", Band(Direction.HIGHER_IS_BETTER, 1_200_000, 800_000, 400_000), "Yearly sales for each unit of assets."),
    ;
}

/** One graded reading, with the words to show next to it. */
data class Reading(val measure: Measure, val value: Long, val grade: Grade) {
    val label: String get() = measure.label
}

object Grader {
    fun read(measure: Measure, value: Long?): Reading? = value?.let { Reading(measure, it, measure.band.grade(it)) }

    /** The worst grade among [readings], or null with none; one weak ratio is enough to call the whole company weak-ish. */
    fun overall(readings: List<Reading>): Grade? {
        if (readings.isEmpty()) return null
        val avg = readings.sumOf { it.grade.rank } * 100 / readings.size
        val worst = readings.minOf { it.grade.rank }
        return when {
            worst == 0 && avg < 150 -> Grade.WEAK
            avg >= 250 -> Grade.STRONG
            avg >= 170 -> Grade.SOUND
            avg >= 100 -> Grade.WATCH
            else -> Grade.WEAK
        }
    }

    fun weakest(readings: List<Reading>, count: Int = 3): List<Reading> =
        readings.sortedWith(compareBy<Reading> { it.grade.rank }.thenBy { it.measure.ordinal }).take(count).filter { it.grade.rank <= Grade.WATCH.rank }
}
