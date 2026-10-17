package app.many.economytycoon.banking

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.CreditRating

/**
 * Binary save format of the banking sector: every bank with its books, history and policy, the guarantee funds, the
 * state of the markets and the stress test records. Events, advice and other things the screens derive are not saved.
 */
object BankingCodec {
    const val VERSION = 1

    fun write(service: BankingService, out: ByteWriter) {
        val book = service.book
        out.writeVarInt(VERSION)
        out.writeVarLong(book.nextBankId)
        out.writeVarLong(book.lastStressTest)
        out.writeVarInt(book.banks.size)
        for (b in book.banks.values) writeBank(b, out)
        out.writeVarInt(book.funds.size)
        for (f in book.funds.values) {
            out.writeVarLong(f.country.value)
            out.writeMoney(f.balance); out.writeMoney(f.stateLoan); out.writeMoney(f.totalLevied); out.writeMoney(f.totalPaid)
            out.writeVarLong(f.lastLevy); out.writeMoney(f.extraordinaryThisYear); out.writeVarLong(f.yearStart)
        }
        out.writeVarInt(book.systemic.size)
        for ((c, v) in book.systemic) { out.writeVarLong(c.value); out.writeVarLong(v) }
        out.writeVarInt(book.markets.size)
        for ((c, m) in book.markets) {
            out.writeVarLong(c.value); out.writeRate(m.rate); out.writeMoney(m.volume); out.writeVarLong(m.scarcityPpm); out.writeBoolean(m.frozen)
        }
        out.writeVarInt(book.stress.size)
        for ((id, r) in book.stress) {
            out.writeVarLong(id.value); out.writeVarLong(r.tick); out.writeString(r.scenarioId)
            out.writeRate(r.startCet1); out.writeRate(r.minCet1); out.writeRate(r.endCet1)
            out.writeMoney(r.shortfall); out.writeRate(r.stressedLcr); out.writeBoolean(r.passed)
        }
        out.writeVarInt(book.hiddenAdvice.size)
        for ((code, until) in book.hiddenAdvice) { out.writeString(code); out.writeVarLong(until) }
    }

    private fun writeBank(b: Bank, out: ByteWriter) {
        out.writeVarLong(b.id.value); out.writeString(b.name); out.writeVarLong(b.country.value)
        out.writeVarInt(b.operator.ordinal)
        out.writeBoolean(b.owner != null); b.owner?.let { out.writeVarLong(it.value) }
        out.writeVarLong(b.foundedAt.value)
        val s = b.strategy
        out.writeBoolean(s != null)
        if (s != null) writeStrategy(s, out)
        out.writeVarInt(b.status.ordinal); out.writeVarLong(b.statusSince.value)
        for (m in listOf(
            b.reserves, b.interbankLent, b.interbankBorrowed, b.centralBankMarginal, b.centralBankEmergency, b.fixedAssets, b.intangibles,
            b.shareCapital, b.retained, b.oci, b.additionalTier1, b.tier2, b.wholesale, b.allowance, b.taxLossCarry,
        )) out.writeMoney(m)
        out.writeVarLong(b.wholesaleRatePpm)
        out.writeVarInt(b.loans.size)
        for ((k, p) in b.loans) {
            out.writeVarInt(k.sector.ordinal); out.writeVarInt(k.rating.ordinal); out.writeVarInt(k.stage.ordinal)
            out.writeMoney(p.principal); out.writeVarLong(p.ratePpm)
        }
        out.writeVarInt(b.deposits.size)
        for ((k, p) in b.deposits) {
            out.writeVarInt(k.depositor.ordinal); out.writeVarInt(k.product.ordinal); out.writeMoney(p.balance); out.writeVarLong(p.ratePpm)
        }
        out.writeVarInt(b.securities.size)
        for ((k, h) in b.securities) { out.writeVarInt(k.ordinal); out.writeMoney(h.value); out.writeVarLong(h.yieldPpm); out.writeVarInt(h.durationMonths) }
        writePolicy(b.policy, out)
        out.writeVarLong(b.confidencePpm); out.writeVarInt(b.runStage.ordinal); out.writeVarLong(b.rumourPpm); out.writeVarLong(b.supportPpm)
        out.writeVarInt(b.panicDays); out.writeMoney(b.lastOutflow)
        out.writeVarInt(b.rating.ordinal); out.writeVarInt(b.ratingScore); out.writeVarInt(b.ratingPendingDirection); out.writeVarInt(b.ratingPendingCount)
        out.writeVarInt(b.rung.ordinal); out.writeVarLong(b.rungSince.value); out.writeVarInt(b.cleanMonths)
        out.writeVarLong(b.restorationDeadline); out.writeString(b.interventionReason)
        out.writeBoolean(b.lastDividend != null); b.lastDividend?.let { out.writeVarLong(it.value) }
        out.writeMoney(b.levyPaid); out.writeVarLong(b.lastMonthClosed); out.writeVarInt(b.monthsSincePlan)
        out.writeMoney(b.periodInterbankIncome); out.writeMoney(b.periodInterbankCost); out.writeMoney(b.periodTrading)
        out.writeVarInt(b.historyList.size)
        for (r in b.historyList) {
            out.writeVarLong(r.tick)
            for (m in listOf(
                r.loanInterest, r.securityIncome, r.interbankIncome, r.depositInterest, r.wholesaleInterest, r.centralBankInterest, r.interbankCost,
                r.subordinatedInterest, r.fees, r.tradingResult, r.operatingCost, r.provisionCharge, r.tax, r.averageEarningAssets, r.averageLoans,
                r.averageEquity, r.averageDeposits,
            )) out.writeMoney(m)
        }
    }

    private fun writeStrategy(s: BankStrategy, out: ByteWriter) {
        out.writeVarInt(s.archetype.ordinal)
        for (r in listOf(s.growthAppetite, s.riskAppetite, s.depositAggression, s.payout, s.capitalCushion, s.liquidityCushion, s.wholesaleReliance, s.securitiesShare)) out.writeRate(r)
        out.writeVarInt(s.thinkMonths)
        for (sector in LoanSector.values()) out.writeRate(s.biasOf(sector))
    }

    private fun writePolicy(p: BankPolicy, out: ByteWriter) {
        for (product in DepositProduct.values()) out.writeVarLong(p.premiumOf(product))
        out.writeVarLong(p.loanMarginBps); out.writeVarInt(p.minRating.ordinal)
        for (sector in LoanSector.values()) out.writeRate(p.appetiteOf(sector))
        for (r in listOf(p.maxSectorShare, p.capitalCushion, p.targetLcr, p.dividendPayout, p.growthAppetite, p.securitiesShare, p.wholesaleCap)) out.writeRate(r)
    }

    // Reading ---------------------------------------------------------------------------------------------------------------------

    fun read(r: ByteReader, service: BankingService) {
        val version = r.readVarInt()
        if (version != VERSION) throw ByteReader.CorruptDataException("unsupported banking save version $version")
        service.clearAll()
        val book = service.book
        book.nextBankId = r.readVarLong()
        book.lastStressTest = r.readVarLong()
        repeat(r.readVarInt()) { val b = readBank(r); book.banks[b.id] = b }
        repeat(r.readVarInt()) {
            val f = GuaranteeFund(CountryId(r.readVarLong()))
            f.restore(r.readMoney(), r.readMoney(), r.readMoney(), r.readMoney(), r.readVarLong(), r.readMoney(), r.readVarLong())
            book.funds[f.country] = f
        }
        repeat(r.readVarInt()) { book.systemic[CountryId(r.readVarLong())] = r.readVarLong() }
        repeat(r.readVarInt()) {
            val c = CountryId(r.readVarLong())
            book.markets[c] = MarketStatus(r.readRate(), r.readMoney(), r.readVarLong(), r.readBoolean())
        }
        repeat(r.readVarInt()) {
            val id = BankId(r.readVarLong())
            book.stress[id] = StressRecord(r.readVarLong(), r.readString(), r.readRate(), r.readRate(), r.readRate(), r.readMoney(), r.readRate(), r.readBoolean())
        }
        repeat(r.readVarInt()) { book.hiddenAdvice[r.readString()] = r.readVarLong() }
        verify(book)
    }

    /** A save whose banks do not balance, or whose records point at nothing, is rejected rather than played on. */
    private fun verify(book: BankingBook) {
        for (b in book.banks.values) {
            if (b.snapshot().imbalance != Money.ZERO) throw ByteReader.CorruptDataException("books of bank ${b.id.value} do not balance")
        }
        for (id in book.stress.keys) if (!book.banks.containsKey(id)) throw ByteReader.CorruptDataException("stress record for unknown bank ${id.value}")
        if (book.banks.keys.any { it.value >= book.nextBankId }) throw ByteReader.CorruptDataException("bank id counter is behind the banks")
    }

    private fun <T> pick(all: Array<T>, index: Int, what: String): T = all.getOrNull(index) ?: throw ByteReader.CorruptDataException("unknown $what $index")

    private fun readBank(r: ByteReader): Bank {
        val id = BankId(r.readVarLong())
        val name = r.readString()
        val country = CountryId(r.readVarLong())
        val operator = pick(BankOperator.values(), r.readVarInt(), "bank operator")
        val owner = if (r.readBoolean()) CompanyId(r.readVarLong()) else null
        val founded = Tick(r.readVarLong())
        val strategy = if (r.readBoolean()) readStrategy(r) else null
        val b = Bank(id, name, country, operator, owner, founded, strategy)
        b.status = pick(BankStatus.values(), r.readVarInt(), "bank status"); b.statusSince = Tick(r.readVarLong())
        b.reserves = r.readMoney(); b.interbankLent = r.readMoney(); b.interbankBorrowed = r.readMoney()
        b.centralBankMarginal = r.readMoney(); b.centralBankEmergency = r.readMoney()
        b.fixedAssets = r.readMoney(); b.intangibles = r.readMoney()
        b.shareCapital = r.readMoney(); b.retained = r.readMoney(); b.oci = r.readMoney()
        b.additionalTier1 = r.readMoney(); b.tier2 = r.readMoney(); b.wholesale = r.readMoney(); b.allowance = r.readMoney(); b.taxLossCarry = r.readMoney()
        b.wholesaleRatePpm = r.readVarLong()
        repeat(r.readVarInt()) {
            val key = PoolKey(pick(LoanSector.values(), r.readVarInt(), "loan sector"), pick(CreditRating.values(), r.readVarInt(), "rating"), pick(LoanStage.values(), r.readVarInt(), "loan stage"))
            b.loans[key] = LoanPool(r.readMoney(), r.readVarLong())
        }
        repeat(r.readVarInt()) {
            val key = DepositKey(pick(Depositor.values(), r.readVarInt(), "depositor"), pick(DepositProduct.values(), r.readVarInt(), "deposit product"))
            b.deposits[key] = DepositPool(r.readMoney(), r.readVarLong())
        }
        repeat(r.readVarInt()) {
            val kind = pick(SecurityKind.values(), r.readVarInt(), "security kind")
            b.securities[kind] = SecurityHolding(r.readMoney(), r.readVarLong(), r.readVarInt())
        }
        b.policy = readPolicy(r)
        b.confidencePpm = r.readVarLong(); b.runStage = pick(RunStage.values(), r.readVarInt(), "run stage")
        b.rumourPpm = r.readVarLong(); b.supportPpm = r.readVarLong(); b.panicDays = r.readVarInt(); b.lastOutflow = r.readMoney()
        b.rating = pick(CreditRating.values(), r.readVarInt(), "rating"); b.ratingScore = r.readVarInt()
        b.ratingPendingDirection = r.readVarInt(); b.ratingPendingCount = r.readVarInt()
        b.rung = pick(SupervisoryRung.values(), r.readVarInt(), "supervisory rung"); b.rungSince = Tick(r.readVarLong()); b.cleanMonths = r.readVarInt()
        b.restorationDeadline = r.readVarLong(); b.interventionReason = r.readString()
        b.lastDividend = if (r.readBoolean()) Tick(r.readVarLong()) else null
        b.levyPaid = r.readMoney(); b.lastMonthClosed = r.readVarLong(); b.monthsSincePlan = r.readVarInt()
        b.periodInterbankIncome = r.readMoney(); b.periodInterbankCost = r.readMoney(); b.periodTrading = r.readMoney()
        repeat(r.readVarInt()) {
            b.historyList.add(
                MonthlyResult(
                    r.readVarLong(), r.readMoney(), r.readMoney(), r.readMoney(), r.readMoney(), r.readMoney(), r.readMoney(), r.readMoney(),
                    r.readMoney(), r.readMoney(), r.readMoney(), r.readMoney(), r.readMoney(), r.readMoney(), r.readMoney(), r.readMoney(),
                    r.readMoney(), r.readMoney(),
                ),
            )
        }
        return b
    }

    private fun readStrategy(r: ByteReader): BankStrategy {
        val archetype = pick(BankArchetype.values(), r.readVarInt(), "bank archetype")
        val rates = List(8) { r.readRate() }
        val think = r.readVarInt()
        if (think < 1) throw ByteReader.CorruptDataException("think interval $think")
        val bias = LoanSector.values().associateWith { r.readRate() }
        return BankStrategy(archetype, rates[0], rates[1], rates[2], rates[3], rates[4], rates[5], rates[6], rates[7], think, bias)
    }

    private fun readPolicy(r: ByteReader): BankPolicy {
        val premiums = DepositProduct.values().associateWith { r.readVarLong() }
        val margin = r.readVarLong()
        val minRating = pick(CreditRating.values(), r.readVarInt(), "rating")
        if (minRating == CreditRating.D) throw ByteReader.CorruptDataException("a bank cannot lend to defaulted borrowers")
        val appetite = LoanSector.values().associateWith { r.readRate() }
        val rates = List(7) { r.readRate() }
        return BankPolicy(premiums, margin, minRating, appetite, rates[0], rates[1], rates[2], rates[3], rates[4], rates[5], rates[6])
    }
}
