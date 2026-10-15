package app.many.economytycoon.ui.present

/** How a value should be coloured. The drawing layer maps tones to theme colours. */
enum class Tone { NEUTRAL, GOOD, BAD, WARN, INFO, MUTED }

/** A headline number with a caption. */
data class Tile(val label: String, val value: String, val tone: Tone = Tone.NEUTRAL, val note: String? = null)

/** A label/value pair in a list of facts. */
data class Fact(val label: String, val value: String, val tone: Tone = Tone.NEUTRAL, val hint: String? = null)

object Tones {
    fun bySign(value: Long): Tone = when {
        value > 0 -> Tone.GOOD
        value < 0 -> Tone.BAD
        else -> Tone.NEUTRAL
    }

    /** Colour for a 0..1 health value stored as parts per million. */
    fun health(ppm: Long): Tone = when {
        ppm < 350_000L -> Tone.BAD
        ppm < 650_000L -> Tone.WARN
        else -> Tone.GOOD
    }
}
