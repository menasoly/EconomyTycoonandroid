package app.many.economytycoon.ui.model

import app.many.economytycoon.companies.BuyRule
import app.many.economytycoon.companies.Department
import app.many.economytycoon.companies.DividendPolicy
import app.many.economytycoon.companies.PriceStrategy
import app.many.economytycoon.companies.SellRule
import app.many.economytycoon.contracts.ContractSide
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityId
import app.many.economytycoon.core.GoodId
import app.many.economytycoon.core.LoanId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Price
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.logistics.StockRef
import app.many.economytycoon.markets.Side
import app.many.economytycoon.simulation.Command

/** Result of checking a form: the command to send, or what is wrong with which field. */
sealed class FormResult {
    data class Ready(val command: Command) : FormResult()
    data class Errors(val byField: Map<String, String>) : FormResult()

    val commandOrNull: Command? get() = (this as? Ready)?.command
    fun errorFor(field: String): String? = (this as? Errors)?.byField?.get(field)
}

/** Collects field errors while a form reads its inputs. */
internal class FormReader {
    val errors = LinkedHashMap<String, String>()

    fun <T> need(field: String, parsed: Parsed<T>): T? = when (val p = Parsing.required(parsed)) {
        is Parsed.Value -> p.value
        is Parsed.Invalid -> { errors[field] = p.reason; null }
        Parsed.Empty -> null
    }

    fun <T> optional(field: String, parsed: Parsed<T>): T? = when (parsed) {
        is Parsed.Value -> parsed.value
        is Parsed.Invalid -> { errors[field] = parsed.reason; null }
        Parsed.Empty -> null
    }

    fun check(field: String, ok: Boolean, message: String) { if (!ok && field !in errors) errors[field] = message }

    fun result(build: () -> Command): FormResult = if (errors.isEmpty()) FormResult.Ready(build()) else FormResult.Errors(errors.toMap())
}

/** Place a buy or sell order from a facility's stock. */
data class OrderForm(
    val company: CompanyId,
    val facility: FacilityId,
    val side: Side,
    val good: GoodId,
    val quantityText: String = "",
    val limitText: String = "",
    val ttl: Int = 5,
    val stockOnHand: Quantity? = null,
    val cash: Money? = null,
) {
    fun validate(): FormResult {
        val r = FormReader()
        val quantity = r.need("quantity", Parsing.quantity(quantityText))
        val limit = r.optional("limit", Parsing.money(limitText))
        r.check("quantity", quantity == null || quantity.raw > 0, "Quantity must be more than zero.")
        r.check("ttl", ttl in 1..30, "Orders can stay open for 1 to 30 days.")
        if (side == Side.SELL && quantity != null && stockOnHand != null) r.check("quantity", quantity <= stockOnHand, "Only ${Formatting(stockOnHand)} in stock.")
        if (side == Side.BUY && quantity != null && limit != null && cash != null) {
            r.check("limit", limit.valueOf(quantity) <= cash, "That order costs more than the cash you have.")
        }
        return r.result { Command.PlaceOrder(company, facility, side, good, quantity!!, limit?.let { Price(it) }, ttl) }
    }

    private fun Formatting(q: Quantity) = app.many.economytycoon.session.Formatting.quantity(q)
}

/** Set how many people of a role a facility employs, and optionally what they are paid. */
data class StaffingForm(
    val company: CompanyId,
    val facility: FacilityId,
    val role: RoleId,
    val targetText: String = "",
    val wageText: String = "",
    val minimumWage: Money = Money.ZERO,
) {
    fun validate(): FormResult {
        val r = FormReader()
        val target = r.need("target", Parsing.wholeNumber(targetText))
        val wage = r.optional("wage", Parsing.money(wageText))
        r.check("target", target == null || target <= 100_000, "That is more staff than one facility can hold.")
        r.check("wage", wage == null || wage >= minimumWage, "Wages cannot be below the legal minimum of ${app.many.economytycoon.session.Formatting.money(minimumWage)}.")
        return r.result { Command.SetStaffing(company, facility, role, target!!, wage) }
    }
}

/** Ask the bank for a loan. */
data class LoanForm(
    val company: CompanyId,
    val amountText: String = "",
    val termMonths: Int = 12,
    val creditLimit: Money? = null,
) {
    fun validate(): FormResult {
        val r = FormReader()
        val amount = r.need("amount", Parsing.money(amountText))
        r.check("amount", amount == null || amount.raw > 0, "Borrow more than zero.")
        r.check("term", termMonths in 1..60, "Choose a term of 1 to 60 months.")
        if (amount != null && creditLimit != null) r.check("amount", amount <= creditLimit, "The bank will lend at most ${app.many.economytycoon.session.Formatting.money(creditLimit)}.")
        return r.result { Command.Borrow(company, amount!!, termMonths * 30) }
    }
}

/** Pay back part or all of a loan. */
data class RepayForm(val company: CompanyId, val loan: LoanId?, val amountText: String = "", val outstanding: Money? = null) {
    fun validate(): FormResult {
        val r = FormReader()
        val amount = r.need("amount", Parsing.money(amountText))
        r.check("amount", amount == null || amount.raw > 0, "Repay more than zero.")
        if (amount != null && outstanding != null) r.check("amount", amount <= outstanding, "That is more than is owed.")
        return r.result { Command.Repay(company, loan, amount!!) }
    }
}

/** Keep selling a good automatically. */
data class SellRuleForm(
    val company: CompanyId,
    val facility: FacilityId,
    val good: GoodId,
    val strategy: PriceStrategy = PriceStrategy.MARKET,
    val markupText: String = "0",
    val reserveText: String = "",
    val maxPerOrderText: String = "",
    val minMarginText: String = "0",
    val enabled: Boolean = true,
) {
    fun validate(): FormResult {
        val r = FormReader()
        val markup = r.need("markup", Parsing.percent(markupText, allowNegative = true))
        val reserve = r.optional("reserve", Parsing.quantity(reserveText))
        val maxOrder = r.optional("maxPerOrder", Parsing.quantity(maxPerOrderText))
        val margin = r.need("minMargin", Parsing.percent(minMarginText, allowNegative = true))
        r.check("markup", markup == null || markup.ppm in -900_000L..5_000_000L, "Markup must be between -90% and 500%.")
        r.check("minMargin", margin == null || margin.ppm >= -900_000L, "Minimum margin cannot be below -90%.")
        r.check("maxPerOrder", maxOrder == null || maxOrder.raw > 0, "Order size must be more than zero.")
        return r.result { Command.SetSellRule(company, SellRule(facility, good, strategy, markup!!.ppm, reserve ?: Quantity.ZERO, maxOrder, margin!!.ppm, enabled)) }
    }
}

/** Keep an input stocked automatically. */
data class BuyRuleForm(
    val company: CompanyId,
    val facility: FacilityId,
    val good: GoodId,
    val targetText: String = "",
    val maxMultipleText: String = "110",
    val enabled: Boolean = true,
) {
    fun validate(): FormResult {
        val r = FormReader()
        val target = r.need("target", Parsing.quantity(targetText))
        val multiple = r.need("maxMultiple", Parsing.percent(maxMultipleText))
        r.check("maxMultiple", multiple == null || multiple.ppm in 100_000L..10_000_000L, "Choose between 10% and 1000% of the market price.")
        return r.result { Command.SetBuyRule(company, BuyRule(facility, good, target!!, multiple!!.ppm, enabled)) }
    }
}

/** How much of the profit is paid out to owners, and how often. */
data class DividendForm(val company: CompanyId, val payoutText: String = "0", val reserveDays: Int = 30, val intervalDays: Int = 90) {
    fun validate(): FormResult {
        val r = FormReader()
        val payout = r.need("payout", Parsing.percent(payoutText))
        r.check("payout", payout == null || payout.ppm in 0L..1_000_000L, "Payout must be between 0% and 100%.")
        r.check("reserve", reserveDays in 0..720, "Reserve must be 0 to 720 days of costs.")
        r.check("interval", intervalDays in 5..720, "Pay every 5 to 720 days.")
        return r.result { Command.SetDividend(company, DividendPolicy(payout!!.ppm, reserveDays, intervalDays)) }
    }
}

/** Daily spending of a department. */
data class BudgetForm(val company: CompanyId, val department: Department, val amountText: String = "") {
    fun validate(): FormResult {
        val r = FormReader()
        val amount = r.need("amount", Parsing.money(amountText))
        return r.result { Command.SetBudget(company, department, amount!!) }
    }
}

/** Move goods between two of the company's stores. */
data class ShipForm(
    val company: CompanyId,
    val from: StockRef?,
    val to: StockRef?,
    val good: GoodId?,
    val quantityText: String = "",
    val available: Quantity? = null,
    val useFleet: Boolean = false,
) {
    fun validate(): FormResult {
        val r = FormReader()
        r.check("from", from != null, "Choose where to ship from.")
        r.check("to", to != null, "Choose where to ship to.")
        r.check("to", from == null || to == null || from != to, "Choose two different places.")
        r.check("good", good != null, "Choose a good.")
        val quantity = r.need("quantity", Parsing.quantity(quantityText))
        r.check("quantity", quantity == null || quantity.raw > 0, "Quantity must be more than zero.")
        if (quantity != null && available != null) r.check("quantity", quantity <= available, "Only ${app.many.economytycoon.session.Formatting.quantity(available)} available there.")
        return r.result { Command.Ship(company, from!!, to!!, good!!, quantity!!, null, useFleet) }
    }
}

/** A takeover bid. */
data class OfferForm(
    val company: CompanyId,
    val target: CompanyId,
    val sharesText: String = "",
    val priceText: String = "",
    val shareCount: Long? = null,
) {
    fun validate(): FormResult {
        val r = FormReader()
        val shares = r.need("shares", Parsing.wholeNumber(sharesText))
        val price = r.need("price", Parsing.money(priceText))
        r.check("shares", shares == null || shares > 0, "Bid for at least one share.")
        r.check("price", price == null || price.raw > 0, "Offer a price above zero.")
        r.check("target", company != target, "A company cannot bid for itself.")
        if (shares != null && shareCount != null) r.check("shares", shares <= shareCount, "The company only has ${app.many.economytycoon.session.Formatting.grouped(shareCount)} shares.")
        return r.result { Command.MakeOffer(company, target, shares!!, price!!) }
    }
}

/** Offer a supply contract to another company. */
data class ContractForm(
    val company: CompanyId,
    val partner: CompanyId?,
    val side: ContractSide,
    val facility: FacilityId?,
    val good: GoodId?,
    val quantityText: String = "",
    val priceText: String = "",
    val intervalText: String = "5",
    val deliveriesText: String = "12",
    val minPrice: Money? = null,
    val maxPrice: Money? = null,
    val stockOnHand: Quantity? = null,
) {
    fun validate(): FormResult {
        val r = FormReader()
        r.check("partner", partner != null, "Choose who to offer the contract to.")
        r.check("partner", partner == null || partner != company, "A company cannot contract with itself.")
        r.check("facility", facility != null, "Choose which of your facilities takes part.")
        r.check("good", good != null, "Choose a good.")
        val quantity = r.need("quantity", Parsing.quantity(quantityText))
        val price = r.need("price", Parsing.money(priceText))
        val interval = r.need("interval", Parsing.wholeNumber(intervalText))
        val deliveries = r.need("deliveries", Parsing.wholeNumber(deliveriesText))
        r.check("quantity", quantity == null || quantity.raw > 0, "Each delivery must be more than zero.")
        r.check("price", price == null || price.raw > 0, "The price must be above zero.")
        r.check("interval", interval == null || interval in 1..90, "Deliveries can be 1 to 90 days apart.")
        r.check("deliveries", deliveries == null || deliveries in 1..360, "A contract covers 1 to 360 deliveries.")
        if (price != null && minPrice != null && maxPrice != null) {
            r.check("price", price >= minPrice && price <= maxPrice, "Choose between ${app.many.economytycoon.session.Formatting.money(minPrice)} and ${app.many.economytycoon.session.Formatting.money(maxPrice)} each.")
        }
        if (side == ContractSide.SELLER && quantity != null && stockOnHand != null) {
            r.check("quantity", quantity <= stockOnHand, "You only have ${app.many.economytycoon.session.Formatting.quantity(stockOnHand)} in stock to start from.")
        }
        return r.result { Command.ProposeContract(company, partner!!, side, facility!!, good!!, quantity!!, price!!, interval!!.toInt(), deliveries!!.toInt()) }
    }
}

/** Accept an offer using one of your facilities. */
data class AcceptForm(val company: CompanyId, val contract: app.many.economytycoon.core.ContractId, val facility: FacilityId?) {
    fun validate(): FormResult {
        val r = FormReader()
        r.check("facility", facility != null, "Choose which facility takes part.")
        return r.result { Command.AcceptContract(company, contract, facility!!) }
    }
}

/** Buy or sell shares of a listed company. [available] is what the public offers (buying) or what you hold (selling). */
data class TradeForm(
    val company: CompanyId,
    val issuer: CompanyId,
    val buying: Boolean,
    val sharesText: String = "",
    val available: Long? = null,
) {
    fun validate(): FormResult {
        val r = FormReader()
        val shares = r.need("shares", Parsing.wholeNumber(sharesText))
        r.check("shares", shares == null || shares > 0, "Trade at least one share.")
        r.check("shares", company != issuer || !buying, "A company cannot buy its own shares here; use a buyback.")
        if (shares != null && available != null) {
            val msg = if (buying) "The public is only selling ${app.many.economytycoon.session.Formatting.grouped(available)} shares." else "You hold ${app.many.economytycoon.session.Formatting.grouped(available)} shares."
            r.check("shares", shares <= available, msg)
        }
        return r.result { if (buying) Command.BuyShares(company, issuer, shares!!) else Command.SellShares(company, issuer, shares!!) }
    }
}

/** Float the player's company on the exchange, selling [floatText] percent of it to the public. */
data class ListingForm(val company: CompanyId, val floatText: String = "25", val minFloatPpm: Long = 50_000L, val maxFloatPpm: Long = 490_000L) {
    fun validate(): FormResult {
        val r = FormReader()
        val pct = r.need("float", Parsing.percent(floatText))
        if (pct != null) r.check("float", pct.ppm in minFloatPpm..maxFloatPpm, "Sell between ${app.many.economytycoon.session.Formatting.percentPpm(minFloatPpm)} and ${app.many.economytycoon.session.Formatting.percentPpm(maxFloatPpm)} of the company.")
        return r.result { Command.ListCompany(company, pct!!.ppm) }
    }
}

/** Buy back some of your own shares from the public. */
data class BuybackForm(val company: CompanyId, val sharesText: String = "", val publicShares: Long? = null) {
    fun validate(): FormResult {
        val r = FormReader()
        val shares = r.need("shares", Parsing.wholeNumber(sharesText))
        r.check("shares", shares == null || shares > 0, "Buy back at least one share.")
        if (shares != null && publicShares != null) r.check("shares", shares <= publicShares, "The public holds only ${app.many.economytycoon.session.Formatting.grouped(publicShares)} shares.")
        return r.result { Command.BuybackShares(company, shares!!) }
    }
}

/** Insure a facility. Coverage is the share of each loss the insurer repays, in percent. */
data class PolicyForm(
    val company: CompanyId,
    val facility: FacilityId?,
    val level: app.many.economytycoon.risk.CoverLevel,
    val deductible: app.many.economytycoon.risk.Deductible,
    val coverageText: String = "80",
    val minCoveragePpm: Long = 500_000L,
    val maxCoveragePpm: Long = 1_000_000L,
    val cash: Money? = null,
    val premium: Money? = null,
) {
    fun validate(): FormResult {
        val r = FormReader()
        r.check("facility", facility != null, "Choose which facility to insure.")
        val pct = r.need("coverage", Parsing.percent(coverageText))
        if (pct != null) {
            val f = app.many.economytycoon.session.Formatting
            r.check("coverage", pct.ppm in minCoveragePpm..maxCoveragePpm, "Choose cover between ${f.percentPpm(minCoveragePpm)} and ${f.percentPpm(maxCoveragePpm)}.")
        }
        if (cash != null && premium != null) r.check("premium", cash >= premium, "The first premium is ${app.many.economytycoon.session.Formatting.money(premium)}, more than you have.")
        return r.result { Command.BuyPolicy(company, facility!!, level, pct!!.ppm, deductible) }
    }
}

/** Bid for a public tender with one of your facilities. */
data class BidForm(
    val company: CompanyId,
    val tender: app.many.economytycoon.core.TenderId,
    val facility: FacilityId?,
    val priceText: String = "",
    val floor: Money? = null,
    val ceiling: Money? = null,
) {
    fun validate(): FormResult {
        val r = FormReader()
        r.check("facility", facility != null, "Choose which facility will deliver.")
        val price = r.need("price", Parsing.money(priceText))
        r.check("price", price == null || price.raw > 0, "The price must be above zero.")
        if (price != null && floor != null && ceiling != null) {
            val f = app.many.economytycoon.session.Formatting
            r.check("price", price >= floor && price <= ceiling, "Bid between ${f.money(floor)} and ${f.money(ceiling)} a unit.")
        }
        return r.result { Command.BidTender(company, tender, facility!!, price!!) }
    }
}

/** Spend money to push a law through or block it. */
data class LobbyForm(
    val company: CompanyId,
    val law: app.many.economytycoon.core.RegulationId,
    val support: Boolean?,
    val amountText: String = "",
    val minimum: Money? = null,
    val cash: Money? = null,
) {
    fun validate(): FormResult {
        val r = FormReader()
        r.check("side", support != null, "Choose whether to back the law or oppose it.")
        val amount = r.need("amount", Parsing.money(amountText))
        if (amount != null) {
            val f = app.many.economytycoon.session.Formatting
            r.check("amount", amount.raw > 0, "The amount must be above zero.")
            if (minimum != null) r.check("amount", amount.raw <= 0 || amount >= minimum, "Lobbying needs at least ${f.money(minimum)}.")
            if (cash != null) r.check("amount", amount <= cash, "You only have ${f.money(cash)}.")
        }
        return r.result { Command.LobbyLaw(company, law, support!!, amount!!) }
    }
}

enum class BondAction { SUBSCRIBE, BUY, SELL }

/** Subscribe to a new bond issue, or buy bonds from or sell them to the dealer. */
data class BondForm(
    val company: CompanyId,
    val bond: app.many.economytycoon.core.BondId,
    val action: BondAction,
    val unitsText: String = "",
    val available: Long? = null,
    val unitCost: Money? = null,
    val cash: Money? = null,
) {
    fun validate(): FormResult {
        val r = FormReader()
        val units = r.need("units", Parsing.wholeNumber(unitsText))
        r.check("units", units == null || units > 0, "Choose at least one unit.")
        if (units != null && available != null) {
            val f = app.many.economytycoon.session.Formatting
            val msg = when (action) {
                BondAction.SUBSCRIBE -> "Only ${f.grouped(available)} units are left in this issue."
                BondAction.BUY -> "The dealer has only ${f.grouped(available)} units."
                BondAction.SELL -> "You hold ${f.grouped(available)} units."
            }
            r.check("units", units <= available, msg)
        }
        if (units != null && unitCost != null && cash != null && action != BondAction.SELL && units > 0 && units <= Long.MAX_VALUE / maxOf(1L, unitCost.raw)) {
            val f = app.many.economytycoon.session.Formatting
            r.check("units", Money(unitCost.raw * units) <= cash, "That costs ${f.money(Money(unitCost.raw * units))} and you have ${f.money(cash)}.")
        }
        return r.result {
            when (action) {
                BondAction.SUBSCRIBE -> Command.SubscribeBond(company, bond, units!!)
                BondAction.BUY -> Command.TradeBond(company, bond, units!!, true)
                BondAction.SELL -> Command.TradeBond(company, bond, units!!, false)
            }
        }
    }
}

/** Answer a union's wage demand: accept, make a smaller offer, or refuse. */
data class DemandResponseForm(
    val company: CompanyId,
    val demand: Long,
    val kind: app.many.economytycoon.unions.ResponseKind,
    val counterText: String = "",
    val demandPpm: Long? = null,
) {
    fun validate(): FormResult {
        val r = FormReader()
        var counter = 0L
        if (kind == app.many.economytycoon.unions.ResponseKind.COUNTER) {
            val rate = r.need("counter", Parsing.percent(counterText))
            if (rate != null) {
                counter = rate.ppm
                r.check("counter", counter > 0L, "A counter-offer must be a raise above zero.")
                if (demandPpm != null) r.check("counter", counter < demandPpm, "Offer less than they ask, or accept their demand.")
            }
        }
        return r.result { Command.RespondDemand(company, demand, kind, counter) }
    }
}

/** Open a futures position: buy to fix a price you will need, or sell to lock in one you will get. */
data class FutureForm(
    val company: CompanyId,
    val good: app.many.economytycoon.core.GoodId?,
    val side: app.many.economytycoon.futures.Side?,
    val contractsText: String = "",
    val tenor: Int? = null,
    val maxContracts: Long = 40L,
    val tenors: List<Int> = listOf(30, 60, 90),
    val cashNeeded: Money? = null,
    val cash: Money? = null,
) {
    fun validate(): FormResult {
        val r = FormReader()
        r.check("good", good != null, "Choose a good.")
        r.check("side", side != null, "Choose whether to buy or sell.")
        val contracts = r.need("contracts", Parsing.wholeNumber(contractsText))
        if (contracts != null) r.check("contracts", contracts in 1..maxContracts, "Trade between 1 and $maxContracts contracts.")
        r.check("tenor", tenor != null && tenor in tenors, "Choose when the contract settles.")
        if (cashNeeded != null && cash != null) {
            val f = app.many.economytycoon.session.Formatting
            r.check("contracts", cashNeeded <= cash, "Margin and fee come to ${f.money(cashNeeded)} and you have ${f.money(cash)}.")
        }
        return r.result { Command.OpenFuture(company, good!!, side!!, contracts!!, tenor!!) }
    }
}

/** Add cash to a position to keep it above the clearing house's minimum. */
data class MarginForm(
    val company: CompanyId,
    val position: Long,
    val amountText: String = "",
    val cash: Money? = null,
) {
    fun validate(): FormResult {
        val r = FormReader()
        val amount = r.need("amount", Parsing.money(amountText))
        if (amount != null) {
            r.check("amount", amount.raw > 0L, "Add more than nothing.")
            if (cash != null) r.check("amount", amount <= cash, "You have only ${app.many.economytycoon.session.Formatting.money(cash)}.")
        }
        return r.result { Command.AddMargin(company, position, amount!!) }
    }
}

/** Put a vacant parcel up for sale. */
data class SellParcelForm(
    val company: CompanyId,
    val parcel: app.many.economytycoon.core.ParcelId,
    val askText: String = "",
    val minAsk: Money? = null,
    val maxAsk: Money? = null,
) {
    fun validate(): FormResult {
        val r = FormReader()
        val ask = r.need("ask", Parsing.money(askText))
        if (ask != null) {
            r.check("ask", ask.raw > 0L, "Set a price above zero.")
            val f = app.many.economytycoon.session.Formatting
            if (minAsk != null) r.check("ask", ask >= minAsk, "Buyers will not look below ${f.money(minAsk)}.")
            if (maxAsk != null) r.check("ask", ask <= maxAsk, "The most you can ask is ${f.money(maxAsk)}.")
        }
        return r.result { Command.ListParcel(company, parcel, ask!!) }
    }
}

/** Offer a vacant parcel to tenants. */
data class LeaseOfferForm(
    val company: CompanyId,
    val parcel: app.many.economytycoon.core.ParcelId,
    val termDays: Int?,
    val rentText: String = "",
    val terms: List<Int> = listOf(90, 180, 360),
    val minRent: Money? = null,
    val maxRent: Money? = null,
) {
    fun validate(): FormResult {
        val r = FormReader()
        r.check("term", termDays != null && termDays in terms, "Choose how long the lease runs.")
        val rent = r.need("rent", Parsing.money(rentText))
        if (rent != null) {
            r.check("rent", rent.raw > 0L, "Set a rent above zero.")
            val f = app.many.economytycoon.session.Formatting
            if (minRent != null) r.check("rent", rent >= minRent, "Tenants will not pay under ${f.money(minRent)} a month for this plot.")
            if (maxRent != null) r.check("rent", rent <= maxRent, "No tenant will pay over ${f.money(maxRent)} a month.")
        }
        return r.result { Command.OfferLease(company, parcel, termDays!!, rent!!) }
    }
}
