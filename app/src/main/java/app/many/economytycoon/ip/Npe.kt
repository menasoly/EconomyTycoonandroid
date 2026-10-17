package app.many.economytycoon.ip

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.Money

/** How a non-practising entity makes its living. */
enum class NpeStrategy(val label: String, val blurb: String) {
    DEMAND_LETTERS("Demand letters", "Sends many modest demands priced below the cost of a defence."),
    PORTFOLIO_BUYER("Portfolio buyer", "Buys patents cheaply from firms in trouble and licenses them to anyone using the idea."),
    LITIGATOR("Litigator", "Goes to court quickly against firms with deep pockets."),
}

/** A scripted actor that owns patents and makes no products. */
class Npe(val id: NpeId, val name: String, val strategy: NpeStrategy, val aggressionPpm: Long, val settleFloorPpm: Long) {
    var chest: Money = Money.ZERO
        internal set
    var demandsSent: Int = 0
        internal set
    var earned: Money = Money.ZERO
        internal set
    var spent: Money = Money.ZERO
        internal set
    var lastActiveMonth: Long = -1L
        internal set

    val owner: IpOwner get() = IpOwner.npe(id)

    fun hashInto(h: CanonicalHasher) {
        h.putLong(id.value).putString(name).putInt(strategy.ordinal).putLong(aggressionPpm).putLong(settleFloorPpm)
        h.putMoney(chest).putInt(demandsSent).putMoney(earned).putMoney(spent).putLong(lastActiveMonth)
    }
}
