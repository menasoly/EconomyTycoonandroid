package app.many.economytycoon.persistence

import java.io.File
import java.io.IOException

/** Where save bytes live. Keys are short names made of letters, digits, '-', '_' and '.'. */
interface SaveStore {
    fun keys(): List<String>
    fun get(key: String): ByteArray?
    fun put(key: String, data: ByteArray)
    fun remove(key: String)
    fun contains(key: String): Boolean = get(key) != null
}

object SaveKeys {
    private val allowed = Regex("[A-Za-z0-9_.-]{1,64}")
    fun check(key: String): String {
        require(allowed.matches(key) && !key.startsWith(".")) { "invalid save key '$key'" }
        return key
    }
}

/** Holds saves in memory. Used by tests and by sessions that must not touch storage. */
class MemorySaveStore : SaveStore {
    private val data = java.util.TreeMap<String, ByteArray>()
    override fun keys(): List<String> = data.keys.toList()
    override fun get(key: String): ByteArray? = data[SaveKeys.check(key)]?.copyOf()
    override fun put(key: String, data: ByteArray) { this.data[SaveKeys.check(key)] = data.copyOf() }
    override fun remove(key: String) { data.remove(SaveKeys.check(key)) }
}

/**
 * One file per key in a directory. A write goes to a temporary file that is flushed to disk and then renamed
 * over the target, so a crash or a full disk leaves either the old save or the new one, never half of each.
 */
class FileSaveStore(private val directory: File) : SaveStore {
    init {
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) throw IOException("cannot create ${directory.path}")
    }

    private fun file(key: String) = File(directory, SaveKeys.check(key))

    override fun keys(): List<String> =
        directory.listFiles { f -> f.isFile && !f.name.endsWith(TEMP_SUFFIX) }?.map { it.name }?.sorted().orEmpty()

    override fun get(key: String): ByteArray? = file(key).takeIf { it.isFile }?.readBytes()

    override fun put(key: String, data: ByteArray) {
        val target = file(key)
        val temp = File(directory, target.name + TEMP_SUFFIX)
        java.io.FileOutputStream(temp).use { out ->
            out.write(data)
            out.flush()
            out.fd.sync()
        }
        if (!temp.renameTo(target)) {
            // Some file systems refuse to rename over an existing file.
            if (target.exists() && !target.delete()) { temp.delete(); throw IOException("cannot replace ${target.name}") }
            if (!temp.renameTo(target)) { temp.delete(); throw IOException("cannot write ${target.name}") }
        }
    }

    override fun remove(key: String) { file(key).delete() }

    /** Removes temporary files left behind by an interrupted write. */
    fun cleanUp(): Int = directory.listFiles { f -> f.name.endsWith(TEMP_SUFFIX) }?.count { it.delete() } ?: 0

    companion object { const val TEMP_SUFFIX = ".tmp" }
}
