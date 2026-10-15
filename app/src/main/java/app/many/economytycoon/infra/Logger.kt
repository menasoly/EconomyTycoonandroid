package app.many.economytycoon.infra

/** Minimal logging abstraction so domain code never touches android.util.Log directly. */
interface Logger {
    fun debug(tag: String, message: () -> String)
    fun info(tag: String, message: String)
    fun warn(tag: String, message: String)
    fun error(tag: String, message: String, cause: Throwable? = null)
}

object NoopLogger : Logger {
    override fun debug(tag: String, message: () -> String) {}
    override fun info(tag: String, message: String) {}
    override fun warn(tag: String, message: String) {}
    override fun error(tag: String, message: String, cause: Throwable?) {}
}

/** Keeps a bounded in-memory log; used by tests and the diagnostics screen. */
class MemoryLogger(private val maxEntries: Int = 500) : Logger {
    data class Entry(val level: Level, val tag: String, val message: String)
    enum class Level { DEBUG, INFO, WARN, ERROR }

    private val entries = ArrayDeque<Entry>()

    private fun add(level: Level, tag: String, message: String) {
        if (entries.size >= maxEntries) entries.removeFirst()
        entries.addLast(Entry(level, tag, message))
    }

    override fun debug(tag: String, message: () -> String) = add(Level.DEBUG, tag, message())
    override fun info(tag: String, message: String) = add(Level.INFO, tag, message)
    override fun warn(tag: String, message: String) = add(Level.WARN, tag, message)
    override fun error(tag: String, message: String, cause: Throwable?) =
        add(Level.ERROR, tag, if (cause == null) message else "$message (${cause.message})")

    fun snapshot(): List<Entry> = entries.toList()
    fun clear() = entries.clear()
}
