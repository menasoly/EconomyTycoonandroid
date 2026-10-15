package app.many.economytycoon.core

/**
 * Fixed-capacity ring buffer. Oldest entries are overwritten. Iteration order is oldest to newest,
 * which keeps agent memory and rolling statistics deterministic.
 */
class RingBuffer<T>(val capacity: Int) {
    init { require(capacity > 0) { "capacity must be positive" } }

    private val items = arrayOfNulls<Any?>(capacity)
    private var start = 0
    var size = 0
        private set

    fun add(item: T) {
        if (size < capacity) {
            items[(start + size) % capacity] = item
            size++
        } else {
            items[start] = item
            start = (start + 1) % capacity
        }
    }

    @Suppress("UNCHECKED_CAST")
    operator fun get(index: Int): T {
        require(index in 0 until size) { "index $index out of bounds for size $size" }
        return items[(start + index) % capacity] as T
    }

    fun last(): T? = if (size == 0) null else get(size - 1)
    fun first(): T? = if (size == 0) null else get(0)

    fun toList(): List<T> = List(size) { get(it) }

    fun clear() {
        for (i in items.indices) items[i] = null
        start = 0
        size = 0
    }
}

/**
 * A map with deterministic iteration in ascending key order. Backed by a sorted structure so
 * entity iteration never depends on hash ordering.
 */
class SortedRegistry<K : Comparable<K>, V> {
    private val map = java.util.TreeMap<K, V>()

    operator fun get(key: K): V? = map[key]
    fun getValue(key: K): V = map[key] ?: throw NoSuchElementException("missing key $key")
    operator fun set(key: K, value: V) { map[key] = value }
    fun put(key: K, value: V): V? = map.put(key, value)
    fun remove(key: K): V? = map.remove(key)
    fun containsKey(key: K) = map.containsKey(key)
    val size: Int get() = map.size
    fun isEmpty() = map.isEmpty()
    val keys: Set<K> get() = map.keys
    val values: Collection<V> get() = map.values
    val entries: Set<Map.Entry<K, V>> get() = map.entries
    fun clear() = map.clear()

    fun firstKey(): K? = if (map.isEmpty()) null else map.firstKey()
    fun lastKey(): K? = if (map.isEmpty()) null else map.lastKey()

    inline fun forEachValue(action: (V) -> Unit) { for (v in values) action(v) }
}

/** Groups items by key preserving ascending key order and original item order within a key. */
fun <T, K : Comparable<K>> Iterable<T>.groupBySorted(key: (T) -> K): java.util.TreeMap<K, MutableList<T>> {
    val out = java.util.TreeMap<K, MutableList<T>>()
    for (item in this) out.getOrPut(key(item)) { ArrayList() }.add(item)
    return out
}

/** Sum of Long selector over items with overflow checking. */
inline fun <T> Iterable<T>.sumExact(selector: (T) -> Long): Long {
    var total = 0L
    for (item in this) total = Math.addExact(total, selector(item))
    return total
}
