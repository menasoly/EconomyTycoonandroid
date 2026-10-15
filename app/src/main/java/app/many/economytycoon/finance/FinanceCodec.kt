package app.many.economytycoon.finance

import app.many.economytycoon.core.AccountId
import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.LoanId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.TransactionId

/** Binary save format for the financial engine. Field order is the schema; bump [VERSION] when it changes. */
object FinanceCodec {
    const val VERSION = 1

    /** Balance changes this far back are saved in full; older history is folded into opening balances. */
    const val HISTORY_TICKS = 420L

    /** Journal entries this far back are saved with their detail. */
    const val TAIL_TICKS = 60L

    private val chartByCode = Chart.values().associateBy { it.code }

    fun write(f: FinanceService, w: ByteWriter) {
        w.writeVarInt(VERSION)
        val close = f.lastCloseTickValue()
        val taxTick = f.lastTaxTickValue()
        w.writeVarLong(close.value)
        w.writeVarLong(taxTick.value)
        val now = f.ledger.latestTick().coerceAtLeast(0L)
        val image = f.ledger.windowImage(Tick(now - HISTORY_TICKS), Tick(maxOf(minOf(close.value, taxTick.value), now - TAIL_TICKS)))
        writeLedger(image, w, 0)
        writeArrears(f.settlement.arrears(), w)
        writeLoans(f.loans.all(), w)
        writeAssets(f.assets, w)
        writeEquity(f.equity.tables(), w)
        writeCompanies(f.companies(), w)
        w.writeVarInt(f.policiesView().size)
        for ((gov, p) in f.policiesView()) { writeOwner(gov, w); writePolicy(p, w) }
    }

    fun read(r: ByteReader, ids: IdAllocators, config: FinanceConfig): FinanceService {
        val version = r.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported finance save version $version")
        val f = FinanceService(ids, config)
        val close = Tick(r.readVarLong())
        val taxTick = Tick(r.readVarLong())
        f.setCloseTicks(close, taxTick)
        f.ledger.restore(readLedger(r))
        f.settlement.restoreArrears(readArrears(r))
        f.loans.restore(readLoans(r))
        readAssets(r, f.assets)
        f.equity.restore(readEquity(r))
        f.restoreCompanies(readCompanies(r, config))
        val n = r.readVarInt()
        val map = java.util.TreeMap<LedgerOwner, TaxPolicy>()
        repeat(n) { map[readOwner(r)] = readPolicy(r) }
        f.restorePolicies(map)
        return f
    }

    // ----- owners and entries -----------------------------------------------------------------

    private fun writeOwner(o: LedgerOwner, w: ByteWriter) { w.writeVarInt(o.kind.ordinal); w.writeVarLong(o.id) }
    private fun readOwner(r: ByteReader): LedgerOwner {
        val k = r.readVarInt()
        val kinds = OwnerKind.values()
        if (k !in kinds.indices) throw ByteReader.CorruptDataException("bad owner kind $k")
        return LedgerOwner(kinds[k], r.readVarLong())
    }
    private fun writeRef(a: AccountRef, w: ByteWriter) { writeOwner(a.owner, w); w.writeVarInt(a.chart.code) }
    private fun readRef(r: ByteReader): AccountRef {
        val owner = readOwner(r)
        val code = r.readVarInt()
        return AccountRef(owner, chartByCode[code] ?: throw ByteReader.CorruptDataException("bad chart code $code"))
    }

    // ----- ledger -----------------------------------------------------------------------------

    private fun writeLedger(img: LedgerImage, w: ByteWriter, anchorCount: Int) {
        w.writeVarLong(img.lastTick)
        w.writeVarInt(anchorCount)
        w.writeVarInt(img.accounts.size)
        for (a in img.accounts) {
            writeRef(a.ref, w)
            w.writeVarLong(a.id.value)
            w.writeVarInt(a.anchors.size)
            for ((t, b) in a.anchors) { w.writeVarLong(t); w.writeVarLong(b) }
            w.writeVarLong(a.balance)
        }
        w.writeVarInt(img.tail.size)
        for (t in img.tail) {
            w.writeVarLong(t.id.value); w.writeString(t.key); w.writeVarLong(t.tick.value)
            w.writeVarInt(t.reason.ordinal); w.writeString(t.memo)
            w.writeVarInt(t.entries.size)
            for (e in t.entries) { writeRef(e.account, w); w.writeMoney(e.amount) }
        }
    }

    private fun readLedger(r: ByteReader): LedgerImage {
        val lastTick = r.readVarLong()
        r.readVarInt()
        val accounts = ArrayList<AccountImage>()
        repeat(r.readVarInt()) {
            val ref = readRef(r)
            val id = AccountId(r.readVarLong())
            val anchors = ArrayList<Pair<Long, Long>>()
            repeat(r.readVarInt()) { anchors.add(r.readVarLong() to r.readVarLong()) }
            accounts.add(AccountImage(ref, id, anchors, r.readVarLong()))
        }
        val reasons = ReasonCode.values()
        val tail = ArrayList<Transaction>()
        repeat(r.readVarInt()) {
            val id = TransactionId(r.readVarLong())
            val key = r.readString()
            val tick = Tick(r.readVarLong())
            val reason = reasons.getOrNull(r.readVarInt()) ?: throw ByteReader.CorruptDataException("bad reason")
            val memo = r.readString()
            val entries = ArrayList<Entry>()
            repeat(r.readVarInt()) { entries.add(Entry(readRef(r), r.readMoney())) }
            tail.add(Transaction(id, key, tick, reason, entries, memo))
        }
        return LedgerImage(accounts, tail, lastTick)
    }

    // ----- arrears and loans ------------------------------------------------------------------

    private fun writeArrears(list: List<Arrear>, w: ByteWriter) {
        w.writeVarInt(list.size)
        for (a in list) {
            w.writeString(a.key); writeOwner(a.payer, w); writeOwner(a.payee, w)
            w.writeVarInt(a.priority.ordinal); w.writeVarInt(a.reason.ordinal); w.writeMoney(a.amount); w.writeVarLong(a.since.value)
        }
    }

    private fun readArrears(r: ByteReader): List<Arrear> {
        val out = ArrayList<Arrear>()
        repeat(r.readVarInt()) {
            out.add(Arrear(
                r.readString(), readOwner(r), readOwner(r),
                SettlementPriority.values()[r.readVarInt()], ReasonCode.values()[r.readVarInt()], r.readMoney(), Tick(r.readVarLong()),
            ))
        }
        return out
    }

    private fun writeLoans(list: List<Loan>, w: ByteWriter) {
        w.writeVarInt(list.size)
        for (l in list) {
            w.writeVarLong(l.id.value); writeOwner(l.borrower, w); writeOwner(l.lender, w)
            w.writeMoney(l.principal); w.writeRate(l.annualRate); w.writeVarLong(l.startTick.value)
            w.writeVarInt(l.termTicks); w.writeVarInt(l.paymentInterval); w.writeVarInt(l.type.ordinal)
            w.writeVarInt(l.collateral.size)
            for (c in l.collateral) { w.writeString(c.description); w.writeMoney(c.value) }
            w.writeVarInt(l.covenants.size)
            for (c in l.covenants) {
                when (c) {
                    is Covenant.MinCashRatio -> { w.writeByte(1); w.writeRate(c.minimum) }
                    is Covenant.MaxDebtToEquity -> { w.writeByte(2); w.writeRate(c.maximum) }
                    is Covenant.MinInterestCoverage -> { w.writeByte(3); w.writeRate(c.minimum) }
                }
            }
            w.writeMoney(l.outstanding); w.writeMoney(l.accruedInterest); w.writeVarLong(l.carryValue)
            w.writeVarInt(l.paymentsMadeValue); w.writeVarInt(l.missedPayments); w.writeBoolean(l.penaltyActive); w.writeVarInt(l.status.ordinal)
        }
    }

    private fun readLoans(r: ByteReader): List<Loan> {
        val out = ArrayList<Loan>()
        repeat(r.readVarInt()) {
            val id = LoanId(r.readVarLong())
            val borrower = readOwner(r); val lender = readOwner(r)
            val principal = r.readMoney(); val rate = r.readRate(); val start = Tick(r.readVarLong())
            val term = r.readVarInt(); val interval = r.readVarInt(); val type = RepaymentType.values()[r.readVarInt()]
            val collateral = ArrayList<Collateral>()
            repeat(r.readVarInt()) { collateral.add(Collateral(r.readString(), r.readMoney())) }
            val covenants = ArrayList<Covenant>()
            repeat(r.readVarInt()) {
                covenants.add(when (val tag = r.readByte()) {
                    1 -> Covenant.MinCashRatio(r.readRate())
                    2 -> Covenant.MaxDebtToEquity(r.readRate())
                    3 -> Covenant.MinInterestCoverage(r.readRate())
                    else -> throw ByteReader.CorruptDataException("bad covenant tag $tag")
                })
            }
            val loan = Loan(id, borrower, lender, principal, rate, start, term, interval, type, collateral, covenants)
            loan.restoreDynamic(r.readMoney(), r.readMoney(), r.readVarLong(), r.readVarInt(), r.readVarInt(), r.readBoolean(), LoanStatus.values()[r.readVarInt()])
            out.add(loan)
        }
        return out
    }

    // ----- assets and equity ------------------------------------------------------------------

    private fun writeAssets(reg: AssetRegister, w: ByteWriter) {
        w.writeVarLong(reg.nextIdValue())
        val all = reg.all()
        w.writeVarInt(all.size)
        for (a in all) {
            w.writeVarLong(a.id); writeOwner(a.owner, w); w.writeString(a.assetClass.id); w.writeString(a.label)
            w.writeMoney(a.cost); w.writeVarLong(a.acquired.value); w.writeMoney(a.accumulated); w.writeBoolean(a.disposed); w.writeMoney(a.impairment)
        }
    }

    private fun readAssets(r: ByteReader, reg: AssetRegister) {
        val next = r.readVarLong()
        val list = ArrayList<FixedAsset>()
        repeat(r.readVarInt()) {
            val a = FixedAsset(r.readVarLong(), readOwner(r), AssetClasses.byId(r.readString()), r.readString(), r.readMoney(), Tick(r.readVarLong()))
            a.accumulated = r.readMoney(); a.disposed = r.readBoolean(); a.impairment = r.readMoney()
            list.add(a)
        }
        reg.restore(list, next)
    }

    private fun writeEquity(tables: List<CapTable>, w: ByteWriter) {
        w.writeVarInt(tables.size)
        for (t in tables) {
            writeOwner(t.issuer, w)
            val classes = t.classes()
            w.writeVarInt(classes.size)
            for (c in classes) {
                w.writeString(c.id); w.writeVarInt(c.votesPerShare)
                w.writeBoolean(c.preferredYield != null); c.preferredYield?.let { w.writeRate(it) }
                w.writeVarLong(t.issuedOf(c.id)); w.writeVarLong(t.treasuryOf(c.id))
            }
            val holdings = t.snapshot()
            w.writeVarInt(holdings.size)
            for ((cls, h, n) in holdings) { w.writeString(cls); w.writeString(h.kind); w.writeVarLong(h.id); w.writeVarLong(n) }
        }
    }

    private fun readEquity(r: ByteReader): List<CapTable> {
        val out = ArrayList<CapTable>()
        repeat(r.readVarInt()) {
            val table = CapTable(readOwner(r))
            val issued = HashMap<String, Long>(); val treasury = HashMap<String, Long>()
            repeat(r.readVarInt()) {
                val id = r.readString(); val votes = r.readVarInt()
                val pref = if (r.readBoolean()) r.readRate() else null
                table.addClass(ShareClass(id, votes, pref))
                issued[id] = r.readVarLong(); treasury[id] = r.readVarLong()
            }
            val list = ArrayList<Triple<String, Holder, Long>>()
            repeat(r.readVarInt()) { list.add(Triple(r.readString(), Holder(r.readString(), r.readVarLong()), r.readVarLong())) }
            table.restore(issued, treasury, list)
            out.add(table)
        }
        return out
    }

    // ----- companies and policy ---------------------------------------------------------------

    private fun writeCompanies(list: List<CompanyFinance>, w: ByteWriter) {
        w.writeVarInt(list.size)
        for (c in list) {
            writeOwner(c.owner, w); writeOwner(c.government, w); w.writeVarLong(c.founded.value)
            val losses = c.tax.carriedLosses()
            w.writeVarInt(losses.size)
            for (l in losses) { w.writeMoney(l.amount); w.writeVarLong(l.incurred.value) }
            w.writeMoney(c.tax.lifetimeTaxPaid); w.writeMoney(c.tax.lastAssessedProfit)
            w.writeVarInt(c.rating.rating.ordinal); w.writeVarInt(c.rating.lastScore)
            w.writeVarInt(c.rating.pendingDirectionValue + 1); w.writeVarInt(c.rating.pendingCountValue)
            w.writeVarInt(c.distress.state.ordinal); w.writeVarInt(c.distress.badTicksValue)
            w.writeVarInt(c.distress.improveTicksValue); w.writeVarInt(c.distress.insolventTicksValue)
        }
    }

    private fun readCompanies(r: ByteReader, config: FinanceConfig): List<CompanyFinance> {
        val out = ArrayList<CompanyFinance>()
        repeat(r.readVarInt()) {
            val owner = readOwner(r); val gov = readOwner(r); val founded = Tick(r.readVarLong())
            val tax = TaxAccount(owner)
            val pools = ArrayList<LossPool>()
            repeat(r.readVarInt()) { pools.add(LossPool(r.readMoney(), Tick(r.readVarLong()))) }
            tax.restore(pools, r.readMoney(), r.readMoney())
            val rating = RatingTracker()
            rating.restore(CreditRating.values()[r.readVarInt()], r.readVarInt(), r.readVarInt() - 1, r.readVarInt())
            val distress = DistressMonitor(config.distress)
            distress.restore(DistressState.values()[r.readVarInt()], r.readVarInt(), r.readVarInt(), r.readVarInt())
            out.add(CompanyFinance(owner, gov, founded, tax, rating, distress))
        }
        return out
    }

    private fun writePolicy(p: TaxPolicy, w: ByteWriter) {
        w.writeRate(p.corporateRate); w.writeRate(p.salesRate); w.writeRate(p.payrollRate); w.writeRate(p.propertyRatePerYear)
        w.writeMoney(p.carbonPerUnitEmission); w.writeRate(p.lossOffsetCap); w.writeVarLong(p.lossCarryTicks)
        w.writeMoney(p.minimumProfitTaxed); w.writeMoney(p.smallProfitThreshold); w.writeRate(p.smallProfitRate)
    }

    private fun readPolicy(r: ByteReader) = TaxPolicy(
        r.readRate(), r.readRate(), r.readRate(), r.readRate(), r.readMoney(), r.readRate(), r.readVarLong(),
        r.readMoney(), r.readMoney(), r.readRate(),
    )
}
