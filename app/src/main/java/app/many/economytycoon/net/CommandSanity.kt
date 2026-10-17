package app.many.economytycoon.net

import app.many.economytycoon.core.Money
import app.many.economytycoon.simulation.Command

/**
 * Which commands a client may send at all and whether their numbers are possible. The simulation would refuse
 * most of these itself, but a client that sends them is either broken or probing, and the server would rather
 * notice than let the game engine absorb the cost of every attempt.
 */
data class CommandPolicy(
    /** Founding companies is the server's job at the start; a player founding more would mint capital. */
    val allowFounding: Boolean = false,
    val maxMoneyUnits: Long = 1_000_000_000_000L,
    val maxQuantityUnits: Long = 1_000_000_000L,
    val maxTermTicks: Int = 3_600,
)

object CommandSanity {
    /** The rule the command breaks, or null when it is plausible. */
    fun check(command: Command, policy: CommandPolicy = CommandPolicy()): String? {
        val cap = Money.units(policy.maxMoneyUnits).raw
        fun badMoney(m: Money, allowZero: Boolean = false): Boolean = m.raw < 0L || (!allowZero && m.raw == 0L) || m.raw > cap
        fun badQty(raw: Long): Boolean = raw <= 0L || raw > policy.maxQuantityUnits * 1_000L
        fun badPpm(v: Long, max: Long = 1_000_000L) = v < 0L || v > max
        fun badName(n: String) = n.trim().length < 3 || n.trim().length > 40 || n.any { it.isISOControl() }
        fun badTerm(t: Int) = t < 1 || t > policy.maxTermTicks
        return when (command) {
            is Command.FoundCompany -> when {
                !policy.allowFounding -> "founding_not_allowed"
                badMoney(command.capital) || badName(command.name) -> "impossible_value"
                else -> null
            }
            is Command.Rename -> if (badName(command.name)) "impossible_value" else null
            is Command.Build -> if (command.name.length > 40 || command.name.any { it.isISOControl() }) "impossible_value" else null
            is Command.DeliverMaterials -> if (badQty(command.quantity.raw)) "impossible_value" else null
            is Command.SetQuality -> if (badPpm(command.levelPpm)) "impossible_value" else null
            is Command.SetLinePriority -> if (command.priority < 0 || command.priority > 1_000) "impossible_value" else null
            is Command.Maintain -> if (badMoney(command.budget, allowZero = true)) "impossible_value" else null
            is Command.SetStaffing -> if (command.target < 0L || command.target > 1_000_000L || (command.wage?.let { badMoney(it) } == true)) "impossible_value" else null
            is Command.SetBenefits -> if (badPpm(command.ppm, 2_000_000L)) "impossible_value" else null
            is Command.SetTraining -> if (badPpm(command.ppm)) "impossible_value" else null
            is Command.PlaceOrder -> when {
                badQty(command.quantity.raw) || command.ttl < 1 || command.ttl > 365 -> "impossible_value"
                command.limit?.let { badMoney(it.perUnit) } == true -> "impossible_value"
                else -> null
            }
            is Command.Borrow -> if (badMoney(command.amount) || badTerm(command.termTicks)) "impossible_value" else null
            is Command.Repay -> if (badMoney(command.amount)) "impossible_value" else null
            is Command.SetBudget -> if (badMoney(command.perTick, allowZero = true)) "impossible_value" else null
            is Command.Ship -> if (badQty(command.quantity.raw)) "impossible_value" else null
            is Command.MoveLocal -> if (badQty(command.quantity.raw)) "impossible_value" else null
            is Command.LeaseWarehouse -> if (badQty(command.capacity.raw)) "impossible_value" else null
            is Command.ProposeContract -> if (badQty(command.quantity.raw) || badMoney(command.unitPrice) || command.intervalTicks < 1 || command.intervalTicks > policy.maxTermTicks || command.deliveries < 1 || command.deliveries > 10_000) "impossible_value" else null
            is Command.ListCompany -> if (command.floatPpm <= 0L || command.floatPpm > 1_000_000L) "impossible_value" else null
            is Command.BuyShares -> if (command.shares <= 0L) "impossible_value" else null
            is Command.SellShares -> if (command.shares <= 0L) "impossible_value" else null
            is Command.BuybackShares -> if (command.shares <= 0L) "impossible_value" else null
            is Command.BidTender -> if (badMoney(command.unitPrice)) "impossible_value" else null
            is Command.OpenFuture -> if (command.contracts <= 0L || command.contracts > 1_000_000L || badTerm(command.tenor)) "impossible_value" else null
            is Command.AddMargin -> if (badMoney(command.amount)) "impossible_value" else null
            is Command.ListParcel -> if (badMoney(command.ask)) "impossible_value" else null
            is Command.OfferLease -> if (badTerm(command.termDays) || badMoney(command.monthlyRent)) "impossible_value" else null
            is Command.RespondDemand -> if (badPpm(command.counterPpm, 2_000_000L)) "impossible_value" else null
            is Command.SubscribeBond -> if (command.units <= 0L) "impossible_value" else null
            is Command.TradeBond -> if (command.units <= 0L) "impossible_value" else null
            is Command.LobbyLaw -> if (badMoney(command.amount, allowZero = true)) "impossible_value" else null
            is Command.BuyPolicy -> if (badPpm(command.coveragePpm)) "impossible_value" else null
            is Command.MakeOffer -> if (command.shares <= 0L || badMoney(command.pricePerShare)) "impossible_value" else null
            is Command.EscrowTransfer -> "server_only"
            else -> null
        }
    }
}
