package app.many.economytycoon.world

import app.many.economytycoon.core.DeterministicRandom

/** Deterministic place-name generator from syllable tables. */
object Names {
    private val starts = listOf("Bel", "Cor", "Dun", "El", "Fal", "Gran", "Har", "Ith", "Jor", "Kel", "Lor", "Mar", "Nor", "Ost", "Pel", "Quin", "Ros", "Sal", "Tor", "Ul", "Val", "Wes", "Yar", "Zan", "Ash", "Brook", "Cald", "Dar", "Eld", "Fen")
    private val middles = listOf("a", "e", "i", "o", "an", "en", "or", "ar", "el", "in", "ad", "on", "um", "is", "ow")
    private val ends = listOf("ford", "ton", "burg", "stead", "mouth", "field", "haven", "port", "dale", "wick", "ville", "gate", "bridge", "minster", "holm", "by", "chester", "shire")
    private val countryEnds = listOf("ia", "land", "mark", "stan", "ica", "onia", "avia", "ora")
    private val regionWords = listOf("Northern", "Southern", "Eastern", "Western", "Central", "Upper", "Lower", "Coastal", "Highland", "Lakeland", "Riverlands", "Borderlands")

    fun city(rnd: DeterministicRandom, used: MutableSet<String>): String {
        repeat(40) {
            val name = starts[rnd.nextInt(starts.size)] + (if (rnd.nextBoolean()) middles[rnd.nextInt(middles.size)] else "") + ends[rnd.nextInt(ends.size)]
            if (used.add(name)) return name
        }
        var n = 2
        while (true) {
            val name = starts[rnd.nextInt(starts.size)] + ends[rnd.nextInt(ends.size)] + " $n"
            if (used.add(name)) return name
            n++
        }
    }

    fun country(rnd: DeterministicRandom, used: MutableSet<String>): String {
        repeat(40) {
            val name = starts[rnd.nextInt(starts.size)] + middles[rnd.nextInt(middles.size)] + countryEnds[rnd.nextInt(countryEnds.size)]
            if (used.add(name)) return name
        }
        val name = "Land ${used.size + 1}"
        used.add(name)
        return name
    }

    fun region(rnd: DeterministicRandom, country: String, index: Int): String =
        regionWords[(rnd.nextInt(regionWords.size) + index) % regionWords.size] + " " + country
}
