package app.many.economytycoon.watch

import app.many.economytycoon.persistence.SaveStore

/** Keeps the player's rules in a [SaveStore] under one key. */
class WatchStore(private val store: SaveStore, private val key: String = KEY) {
    fun load(): RuleBook = RuleBook.fromBytes(store.get(key))
    fun save(book: RuleBook) = store.put(key, book.toBytes())
    fun reset() = store.remove(key)

    companion object { const val KEY = "watch.rules" }
}
