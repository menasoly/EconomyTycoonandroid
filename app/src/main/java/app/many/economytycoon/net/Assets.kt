package app.many.economytycoon.net

/** What can change hands in a trade between players. */
enum class AssetKind(val tag: Int) {
    CASH(0), GOODS(1);

    companion object {
        fun fromTag(tag: Int): AssetKind? = values().firstOrNull { it.tag == tag }
    }
}

/**
 * One line of a trade: [amount] raw units of cash, or of the good named [key]. Cash uses the fixed-point raw
 * scale of Money and goods the raw scale of Quantity, so the numbers are exact.
 */
data class TradeAsset(val kind: AssetKind, val key: String, val amount: Long) : Comparable<TradeAsset> {
    override fun compareTo(other: TradeAsset): Int = compareValuesBy(this, other, { it.kind.tag }, { it.key }, { it.amount })

    companion object {
        fun cash(raw: Long) = TradeAsset(AssetKind.CASH, "", raw)
        fun goods(good: String, raw: Long) = TradeAsset(AssetKind.GOODS, good, raw)
    }
}

/** A movement of assets between two companies that every participant applies at the same point of a tick. */
data class Transfer(val trade: TradeId, val from: Long, val to: Long, val asset: TradeAsset)
