package app.many.economytycoon.automation

import app.many.economytycoon.persistence.SaveStore

/** Keeps manager settings in a [SaveStore] under one key. */
class ManagerStore(private val store: SaveStore, private val key: String = KEY) {
    fun load(): ManagerSettings = ManagerSettings.fromBytes(store.get(key))
    fun save(settings: ManagerSettings) = store.put(key, settings.toBytes())
    fun reset() = store.remove(key)

    companion object { const val KEY = "managers.dat" }
}
