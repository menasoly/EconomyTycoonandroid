package app.many.economytycoon.ip

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.mulDiv

data class SaleResult(val patent: PatentId, val seller: IpOwner, val buyer: IpOwner, val price: Money)

/**
 * Where patents change hands: listed at an asking price, bought outright, or sold in a hurry when the owner is going
 * under. A sale carries the licences granted on the patent with it, and is refused when it would tear a licence bundle
 * in two or take a patent out of a pool.
 */
class PatentMarket(
    private val state: IpState,
    private val office: PatentOffice,
    private val book: LicenseBook,
    private val pools: PoolService,
    private val valuer: PatentValuer,
    val params: IpParams = IpParams(),
) {
    fun listed(): List<Patent> = state.patentMap.values.filter { it.isListed && it.isLive }

    fun list(run: IpRun, patent: PatentId, by: IpOwner, price: Money): Outcome<Patent> {
        val p = state.patentMap[patent] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown patent")
        if (p.owner != by) return Outcome.reject(Rules.NOT_OWNER, "only the owner can sell")
        if (!p.isLive) return Outcome.reject(Rules.WRONG_STATE, "nothing in force to sell")
        if (price.raw <= 0L) return Outcome.reject(Rules.INVALID_ARGUMENT, "the price must be positive")
        if (pools.poolOf(patent) != null) return Outcome.reject(Rules.WRONG_STATE, "the patent sits in a pool")
        p.askingPrice = price
        p.listedAt = run.tick
        return Outcome.ok(p)
    }

    fun unlist(patent: PatentId, by: IpOwner): Outcome<Patent> {
        val p = state.patentMap[patent] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown patent")
        if (p.owner != by) return Outcome.reject(Rules.NOT_OWNER, "only the owner can withdraw it")
        p.askingPrice = null
        p.listedAt = null
        return Outcome.ok(p)
    }

    /** Listings nobody bought for a while come off the market. */
    fun expireListings(tick: Tick) {
        for (p in state.patentMap.values) {
            val at = p.listedAt ?: continue
            if (tick.value - at.value >= params.office.listingDays) { p.askingPrice = null; p.listedAt = null }
        }
    }

    private fun canPay(run: IpRun, who: IpOwner, amount: Money): Boolean {
        val npe = who.npeOrNull()?.let { state.npeMap[it] }
        return if (npe != null) npe.chest >= amount else run.wallet.canPay(who, amount)
    }

    /** Buys a listed patent at its asking price. */
    fun buy(run: IpRun, patent: PatentId, buyer: IpOwner): Outcome<SaleResult> {
        val p = state.patentMap[patent] ?: return Outcome.reject(Rules.UNKNOWN_ENTITY, "unknown patent")
        val price = p.askingPrice ?: return Outcome.reject(Rules.WRONG_STATE, "the patent is not for sale")
        return transfer(run, p, buyer, price)
    }

    private fun transfer(run: IpRun, p: Patent, buyer: IpOwner, price: Money): Outcome<SaleResult> {
        val seller = p.owner
        if (seller == buyer) return Outcome.reject(Rules.INVALID_ARGUMENT, "you already own it")
        if (!p.isLive) return Outcome.reject(Rules.WRONG_STATE, "nothing in force to buy")
        if (pools.poolOf(p.id) != null) return Outcome.reject(Rules.WRONG_STATE, "the patent sits in a pool")
        val buyerCo = buyer.companyOrNull()
        if (buyerCo != null && !run.ctx.isActive(buyerCo)) return Outcome.reject(Rules.WRONG_STATE, "the buyer is out of business")
        if (state.patentsOf(buyer).size >= params.office.maxPatentsPerOwner) return Outcome.reject(Rules.LIMIT_EXCEEDED, "too many patents held")
        if (book.splitsABundle(seller, setOf(p.id)) != null) return Outcome.reject(Rules.WRONG_STATE, "a licence bundle covers this patent with others")
        if (!canPay(run, buyer, price)) return Outcome.reject(Rules.INSUFFICIENT_FUNDS, "the buyer cannot pay")
        val buyerNpe = buyer.npeOrNull()?.let { state.npeMap[it] }
        val sellerNpe = seller.npeOrNull()?.let { state.npeMap[it] }
        if (buyerNpe != null) { buyerNpe.chest -= price; buyerNpe.spent += price } else run.wallet.reserve(buyer, price)
        if (sellerNpe != null) { sellerNpe.chest += price; sellerNpe.earned += price }
        if (price.raw > 0L) run.out.charge(run.tick, ChargeKind.PATENT_PURCHASE, IpLedger.of(buyer), IpLedger.of(seller), price, "patent ${p.title}", p.id.value.toString())
        book.transferLicensor(run, seller, buyer, setOf(p.id))
        p.owner = buyer
        p.transfers += 1
        p.askingPrice = null
        p.listedAt = null
        run.out.event(IpEvent.PatentSold(p.id, seller, buyer, price, run.tick))
        return Outcome.ok(SaleResult(p.id, seller, buyer, price))
    }

    /** What a scripted buyer or planner would bid for a patent: a share of its value. */
    fun bid(p: Patent, run: IpRun, sharePpm: Long): Money = Money(mulDiv(valuer.value(p, run).value.raw, sharePpm, 1_000_000L))

    /** The best buyer for a patent sold in a hurry: whoever can pay the fire-sale price, preferring scripted buyers in id order, then cash-rich computer companies. */
    private fun fireBuyer(run: IpRun, p: Patent, price: Money): IpOwner? {
        val npe = state.npeMap.values.firstOrNull { it.chest >= price && state.patentsOf(it.owner).size < params.npe.maxPatents }
        if (npe != null) return npe.owner
        return run.ctx.aiCompanies().asSequence().filter { it != p.owner.companyOrNull() && run.ctx.isActive(it) && !run.ctx.isDistressed(it) }
            .map { IpOwner.company(it) }.filter { run.wallet.canPay(it, price) }
            .sortedWith(compareBy<IpOwner> { -run.ctx.cash(it).raw }.thenBy { it }).firstOrNull()
    }

    /** Sells a distressed owner's live patents for what they fetch; returns the sales made. */
    fun fireSale(run: IpRun, owner: IpOwner): List<SaleResult> {
        val sold = ArrayList<SaleResult>()
        for (p in state.patentsOf(owner)) {
            if (!p.isLive) continue
            val price = valuer.fireSalePrice(valuer.value(p, run))
            val buyer = fireBuyer(run, p, price) ?: continue
            val r = transfer(run, p, buyer, price)
            if (r is Outcome.Ok) sold += r.value
        }
        return sold
    }

    /** A company that closed: its patents are sold if anyone wants them and abandoned otherwise. */
    fun companyClosed(run: IpRun, company: CompanyId) {
        val owner = IpOwner.company(company)
        fireSale(run, owner)
        for (p in state.patentsOf(owner)) {
            if (p.isLive) office.endAll(p.id, run.tick)
        }
    }
}
