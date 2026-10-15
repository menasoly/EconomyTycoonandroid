package app.many.economytycoon.persistence

import app.many.economytycoon.core.ByteReader

/**
 * Brings the sections of an older snapshot up to the current layout, one version at a time. A step receives the
 * sections as they were written by version N and returns them as version N+1 expects them. A snapshot from a
 * newer game is never guessed at; it is refused.
 */
class Migrations(private val steps: Map<Int, (List<Section>) -> List<Section>> = emptyMap()) {
    fun upgrade(sections: List<Section>, from: Int, to: Int): List<Section> {
        if (from > to) throw ByteReader.CorruptDataException("save was made by a newer version of the game (format $from, this game reads up to $to)")
        var current = sections
        for (v in from until to) {
            val step = steps[v] ?: throw ByteReader.CorruptDataException("no migration from save format $v to ${v + 1}")
            current = step(current)
        }
        return current
    }

    fun canUpgrade(from: Int, to: Int): Boolean = from <= to && (from until to).all { it in steps }
}
