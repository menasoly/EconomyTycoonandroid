package app.many.economytycoon.tutorial

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter

/** Which steps are done, which were skipped, and whether the player has dismissed the guide altogether. */
data class TutorialProgress(
    val completed: Set<String> = emptySet(),
    val skipped: Set<String> = emptySet(),
    val acknowledged: Set<String> = emptySet(),
    val dismissed: Boolean = false,
) {
    fun isDone(id: String) = id in completed || id in skipped

    fun toBytes(): ByteArray {
        val w = ByteWriter()
        w.writeVarInt(VERSION)
        w.writeBoolean(dismissed)
        for (set in listOf(completed, skipped, acknowledged)) {
            w.writeVarInt(set.size)
            for (id in set.sorted()) w.writeString(id)
        }
        return w.toByteArray()
    }

    companion object {
        const val VERSION = 1

        /** Reads stored progress; anything unreadable starts the tutorial afresh. */
        fun fromBytes(bytes: ByteArray?): TutorialProgress {
            if (bytes == null) return TutorialProgress()
            return try {
                val r = ByteReader(bytes)
                if (r.readVarInt() != VERSION) return TutorialProgress()
                val dismissed = r.readBoolean()
                fun ids(): Set<String> {
                    val n = r.readVarInt()
                    require(n in 0..1_000) { "implausible count" }
                    return (0 until n).map { r.readString() }.toSet()
                }
                TutorialProgress(ids(), ids(), ids(), dismissed)
            } catch (e: ByteReader.CorruptDataException) {
                TutorialProgress()
            } catch (e: IllegalArgumentException) {
                TutorialProgress()
            }
        }
    }
}
