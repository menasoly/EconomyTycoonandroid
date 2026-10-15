package app.many.economytycoon.automation

import app.many.economytycoon.content.TechCategory
import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.Money

/** The routine jobs a player can hand to a manager. */
enum class ManagerKind(val label: String, val summary: String, val everyDays: Int) {
    MAINTENANCE("Maintenance", "Repairs buildings and machines before they wear out.", 7),
    WASTE("Waste disposal", "Clears waste before it builds up into pollution fines.", 7),
    SELLING("Selling", "Puts every product on sale at the market price.", 3),
    STAFFING("Staffing", "Raises hiring targets so every running line has its crew.", 7),
    RESEARCH("Research", "Starts the cheapest technology available, favouring a chosen field.", 5),
    REPAYMENT("Loan repayment", "Pays down the dearest loan whenever cash is comfortably above the reserve.", 15),
}

/** What the managers are allowed to do, and the limits they keep to. */
data class ManagerSettings(
    val enabled: Set<ManagerKind> = emptySet(),
    /** Cash managers never spend below. */
    val reserve: Money = Money.units(50_000),
    /** Maintain anything below this condition, in ppm. */
    val repairBelowPpm: Long = 750_000L,
    /** Clear waste once a facility holds this many units. */
    val wasteUnits: Long = 20L,
    /** Lowest margin over cost a sell rule accepts, in ppm; negative accepts a loss. */
    val minMarginPpm: Long = 0L,
    val researchFocus: TechCategory? = null,
) {
    init {
        require(reserve.raw >= 0L) { "reserve cannot be negative" }
        require(repairBelowPpm in 100_000L..1_000_000L) { "repair threshold out of range" }
        require(wasteUnits in 1L..100_000L) { "waste threshold out of range" }
        require(minMarginPpm in -500_000L..2_000_000L) { "margin out of range" }
    }

    fun isOn(k: ManagerKind) = k in enabled
    fun with(k: ManagerKind, on: Boolean) = copy(enabled = if (on) enabled + k else enabled - k)
    val anyOn: Boolean get() = enabled.isNotEmpty()

    fun toBytes(): ByteArray {
        val w = ByteWriter()
        w.writeVarInt(VERSION)
        w.writeVarInt(enabled.size)
        for (k in enabled.sortedBy { it.ordinal }) w.writeVarInt(k.ordinal)
        w.writeMoney(reserve); w.writeVarLong(repairBelowPpm); w.writeVarLong(wasteUnits); w.writeVarLong(minMarginPpm)
        w.writeVarInt(researchFocus?.ordinal ?: -1)
        return w.toByteArray()
    }

    companion object {
        const val VERSION = 1

        fun fromBytes(bytes: ByteArray?): ManagerSettings {
            if (bytes == null) return ManagerSettings()
            return try {
                val r = ByteReader(bytes)
                if (r.readVarInt() != VERSION) return ManagerSettings()
                val n = r.readVarInt()
                require(n in 0..ManagerKind.values().size) { "implausible count" }
                val kinds = (0 until n).map { ManagerKind.values()[r.readVarInt()] }.toSet()
                val reserve = r.readMoney(); val repair = r.readVarLong(); val waste = r.readVarLong(); val margin = r.readVarLong()
                val focus = r.readVarInt().let { if (it < 0) null else TechCategory.values()[it] }
                ManagerSettings(kinds, reserve, repair, waste, margin, focus)
            } catch (e: ByteReader.CorruptDataException) {
                ManagerSettings()
            } catch (e: IllegalArgumentException) {
                ManagerSettings()
            } catch (e: IndexOutOfBoundsException) {
                ManagerSettings()
            }
        }
    }
}
