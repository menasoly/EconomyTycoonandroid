package app.many.economytycoon.production

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.mulDiv

/** Stock of one good: quantity, units set aside for pending sales, and the total carrying value. */
class StockSlot {
    var quantity: Quantity = Quantity.ZERO
        internal set
    var reserved: Quantity = Quantity.ZERO
        internal set
    /** Total carrying value of everything in the slot; average cost is value / quantity. */
    var value: Money = Money.ZERO
        internal set
    /** Fractional spoilage carried between ticks so small stocks still decay deterministically. */
    internal var spoilCarry: Long = 0

    val available: Quantity get() = (quantity - reserved).coerceAtLeast(Quantity.ZERO)

    /** Average cost per whole unit, rounded half-even. */
    val unitCost: Money
        get() = if (quantity.raw == 0L) Money.ZERO else Money(mulDiv(value.raw, Quantity.SCALE, quantity.raw))
}

/**
 * Goods held at one location with weighted-average valuation and reservations. All mutations go
 * through this class so stock can never go negative and value always follows quantity.
 */
class Inventory(
    var capacity: Quantity,
    private val volumeOf: (GoodId) -> Long = { 1_000L },
) {
    private val slots = java.util.TreeMap<GoodId, StockSlot>()

    fun slot(good: GoodId): StockSlot? = slots[good]
    fun quantity(good: GoodId): Quantity = slots[good]?.quantity ?: Quantity.ZERO
    fun available(good: GoodId): Quantity = slots[good]?.available ?: Quantity.ZERO
    fun reserved(good: GoodId): Quantity = slots[good]?.reserved ?: Quantity.ZERO
    fun unitCost(good: GoodId): Money = slots[good]?.unitCost ?: Money.ZERO
    fun reservedTotal(): Quantity { var t = 0L; for (s in slots.values) t += s.reserved.raw; return Quantity(t) }
    fun goods(): List<GoodId> = slots.filter { it.value.quantity.raw > 0 }.keys.toList()
    fun entries(): List<Pair<GoodId, StockSlot>> = slots.entries.map { it.key to it.value }

    /** Total carrying value of all stock. */
    fun totalValue(): Money {
        var t = 0L
        for (s in slots.values) t += s.value.raw
        return Money(t)
    }

    /** Space occupied, in the same units as [capacity]. */
    fun used(): Quantity {
        var t = 0L
        for ((g, s) in slots) t += mulDiv(s.quantity.raw, volumeOf(g), 1_000L)
        return Quantity(t)
    }

    fun free(): Quantity = (capacity - used()).coerceAtLeast(Quantity.ZERO)

    fun volume(good: GoodId, quantity: Quantity): Quantity = Quantity(mulDiv(quantity.raw, volumeOf(good), 1_000L))

    fun fits(good: GoodId, quantity: Quantity): Boolean = volume(good, quantity) <= free()

    /** Largest quantity of [good] that still fits. */
    fun room(good: GoodId): Quantity {
        val v = volumeOf(good)
        return if (v <= 0L) Quantity(Long.MAX_VALUE / 4) else Quantity(mulDiv(free().raw, 1_000L, v))
    }

    /**
     * Adds stock with a total [value]. Returns the quantity actually stored; the remainder (if the
     * location is full) is not added and its value is not recorded.
     */
    fun add(good: GoodId, quantity: Quantity, value: Money, allowOverflow: Boolean = false): Quantity {
        if (quantity.raw <= 0) return Quantity.ZERO
        val stored = if (allowOverflow) quantity else Quantity.min(quantity, room(good))
        if (stored.raw <= 0) return Quantity.ZERO
        val storedValue = if (stored == quantity) value else Money(mulDiv(value.raw, stored.raw, quantity.raw))
        val s = slots.getOrPut(good) { StockSlot() }
        s.quantity += stored
        s.value += storedValue
        return stored
    }

    /** Removes [quantity] of unreserved stock and returns the value removed, or a rejection. */
    fun remove(good: GoodId, quantity: Quantity): Outcome<Money> {
        if (quantity.raw <= 0) return Outcome.reject(Rules.INVALID_QUANTITY, "quantity must be positive")
        val s = slots[good]
        if (s == null || s.available < quantity) return Outcome.reject(Rules.INSUFFICIENT_STOCK, "not enough ${good.key}")
        return Outcome.ok(take(s, quantity))
    }

    /** Removes stock that was previously reserved (a sale being delivered). */
    fun removeReserved(good: GoodId, quantity: Quantity): Outcome<Money> {
        val s = slots[good]
        if (s == null || s.reserved < quantity || s.quantity < quantity) return Outcome.reject(Rules.INSUFFICIENT_STOCK, "reservation too small for ${good.key}")
        s.reserved -= quantity
        return Outcome.ok(take(s, quantity))
    }

    private fun take(s: StockSlot, quantity: Quantity): Money {
        val removedValue = if (quantity == s.quantity) s.value else Money(mulDiv(s.value.raw, quantity.raw, s.quantity.raw))
        s.quantity -= quantity
        s.value -= removedValue
        return removedValue
    }

    fun reserve(good: GoodId, quantity: Quantity): Outcome<Quantity> {
        if (quantity.raw <= 0) return Outcome.reject(Rules.INVALID_QUANTITY, "quantity must be positive")
        val s = slots[good]
        if (s == null || s.available < quantity) return Outcome.reject(Rules.INSUFFICIENT_STOCK, "not enough free ${good.key} to reserve")
        s.reserved += quantity
        return Outcome.ok(quantity)
    }

    fun release(good: GoodId, quantity: Quantity) {
        val s = slots[good] ?: return
        s.reserved = (s.reserved - Quantity.min(quantity, s.reserved))
    }

    /** Applies spoilage to a good; returns the quantity lost. Value is lost with it. */
    fun spoil(good: GoodId, ratePpm: Long): Quantity {
        val s = slots[good] ?: return Quantity.ZERO
        if (s.quantity.raw <= 0 || ratePpm <= 0) return Quantity.ZERO
        val numerator = s.quantity.raw * ratePpm + s.spoilCarry
        s.spoilCarry = numerator % 1_000_000L
        val lost = Quantity((numerator / 1_000_000L).coerceAtMost(s.quantity.raw - s.reserved.raw).coerceAtLeast(0L))
        if (lost.raw > 0) take(s, lost)
        return lost
    }

    /** Drops a good entirely (waste disposal). Returns what was removed and its value. */
    fun clear(good: GoodId): Pair<Quantity, Money> {
        val s = slots[good] ?: return Quantity.ZERO to Money.ZERO
        val q = s.quantity - s.reserved
        if (q.raw <= 0) return Quantity.ZERO to Money.ZERO
        return q to take(s, q)
    }

    internal fun restoreSlot(good: GoodId, quantity: Quantity, reserved: Quantity, value: Money, carry: Long = 0L) {
        val s = slots.getOrPut(good) { StockSlot() }
        s.quantity = quantity; s.reserved = reserved; s.value = value; s.spoilCarry = carry
    }

    fun hashInto(h: CanonicalHasher) {
        h.putQuantity(capacity)
        for ((g, s) in slots) if (s.quantity.raw != 0L || s.value.raw != 0L) h.putString(g.key).putQuantity(s.quantity).putQuantity(s.reserved).putMoney(s.value).putLong(s.spoilCarry)
    }
}
