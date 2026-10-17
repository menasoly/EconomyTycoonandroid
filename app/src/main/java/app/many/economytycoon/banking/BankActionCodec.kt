package app.many.economytycoon.banking

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.finance.CreditRating

/**
 * Binary form of a [BankAction], for the command log and replays. Each variant has a fixed tag; a tag never changes meaning.
 * The shared command codec stores the bytes written here inside its own record.
 */
object BankActionCodec {
    private const val SET_DEPOSIT_PREMIUM = 1
    private const val SET_LOAN_MARGIN = 2
    private const val SET_LENDING_STANDARDS = 3
    private const val SET_SECTOR_APPETITE = 4
    private const val SET_GROWTH_APPETITE = 5
    private const val SET_CAPITAL_POLICY = 6
    private const val SET_FUNDING_POLICY = 7
    private const val RAISE_CAPITAL = 8
    private const val BUY_SECURITIES = 9
    private const val SELL_SECURITIES = 10
    private const val BORROW_WHOLESALE = 11
    private const val REPAY_WHOLESALE = 12
    private const val REQUEST_EMERGENCY = 13
    private const val REASSURE = 14
    private const val RUN_STRESS_TEST = 15
    private const val PAY_DIVIDEND = 16
    private const val FOUND_BANK = 17

    fun write(a: BankAction, w: ByteWriter) {
        when (a) {
            is BankAction.SetDepositPremium -> { tag(w, SET_DEPOSIT_PREMIUM, a); w.writeVarInt(a.product.ordinal); w.writeVarLong(a.basisPoints) }
            is BankAction.SetLoanMargin -> { tag(w, SET_LOAN_MARGIN, a); w.writeVarLong(a.basisPoints) }
            is BankAction.SetLendingStandards -> { tag(w, SET_LENDING_STANDARDS, a); w.writeVarInt(a.minRating.ordinal); w.writeRate(a.maxSectorShare) }
            is BankAction.SetSectorAppetite -> { tag(w, SET_SECTOR_APPETITE, a); w.writeVarInt(a.sector.ordinal); w.writeRate(a.appetite) }
            is BankAction.SetGrowthAppetite -> { tag(w, SET_GROWTH_APPETITE, a); w.writeRate(a.appetite) }
            is BankAction.SetCapitalPolicy -> { tag(w, SET_CAPITAL_POLICY, a); w.writeRate(a.cushion); w.writeRate(a.dividendPayout) }
            is BankAction.SetFundingPolicy -> { tag(w, SET_FUNDING_POLICY, a); w.writeRate(a.securitiesShare); w.writeRate(a.wholesaleCap); w.writeRate(a.targetLcr) }
            is BankAction.RaiseCapital -> { tag(w, RAISE_CAPITAL, a); w.writeVarInt(a.instrument.ordinal); w.writeMoney(a.amount) }
            is BankAction.BuySecurities -> { tag(w, BUY_SECURITIES, a); w.writeVarInt(a.kind.ordinal); w.writeMoney(a.amount) }
            is BankAction.SellSecurities -> { tag(w, SELL_SECURITIES, a); w.writeVarInt(a.kind.ordinal); w.writeMoney(a.amount) }
            is BankAction.BorrowWholesale -> { tag(w, BORROW_WHOLESALE, a); w.writeMoney(a.amount) }
            is BankAction.RepayWholesale -> { tag(w, REPAY_WHOLESALE, a); w.writeMoney(a.amount) }
            is BankAction.RequestEmergencyLiquidity -> { tag(w, REQUEST_EMERGENCY, a); w.writeMoney(a.amount) }
            is BankAction.ReassureDepositors -> { tag(w, REASSURE, a); w.writeMoney(a.spend) }
            is BankAction.RunStressTest -> { tag(w, RUN_STRESS_TEST, a); w.writeString(a.scenarioId) }
            is BankAction.PayDividend -> { tag(w, PAY_DIVIDEND, a); w.writeMoney(a.amount) }
            is BankAction.FoundBank -> {
                w.writeVarInt(FOUND_BANK)
                w.writeString(a.name); w.writeVarLong(a.country.value)
                w.writeBoolean(a.owner != null); a.owner?.let { w.writeVarLong(it.value) }
                w.writeMoney(a.capital); w.writeBoolean(a.player)
            }
        }
    }

    private fun tag(w: ByteWriter, t: Int, a: BankAction) { w.writeVarInt(t); w.writeVarLong(a.bank.value) }

    private fun <E : Enum<E>> pick(values: Array<E>, index: Int, what: String): E =
        values.getOrNull(index) ?: throw ByteReader.CorruptDataException("unknown $what $index")

    fun read(r: ByteReader): BankAction {
        val t = r.readVarInt()
        if (t == FOUND_BANK) {
            val name = r.readString()
            val country = CountryId(r.readVarLong())
            val owner = if (r.readBoolean()) CompanyId(r.readVarLong()) else null
            return BankAction.FoundBank(name, country, owner, r.readMoney(), r.readBoolean())
        }
        val bank = BankId(r.readVarLong())
        return when (t) {
            SET_DEPOSIT_PREMIUM -> BankAction.SetDepositPremium(bank, pick(DepositProduct.values(), r.readVarInt(), "deposit product"), r.readVarLong())
            SET_LOAN_MARGIN -> BankAction.SetLoanMargin(bank, r.readVarLong())
            SET_LENDING_STANDARDS -> BankAction.SetLendingStandards(bank, pick(CreditRating.values(), r.readVarInt(), "rating"), r.readRate())
            SET_SECTOR_APPETITE -> BankAction.SetSectorAppetite(bank, pick(LoanSector.values(), r.readVarInt(), "loan sector"), r.readRate())
            SET_GROWTH_APPETITE -> BankAction.SetGrowthAppetite(bank, r.readRate())
            SET_CAPITAL_POLICY -> BankAction.SetCapitalPolicy(bank, r.readRate(), r.readRate())
            SET_FUNDING_POLICY -> BankAction.SetFundingPolicy(bank, r.readRate(), r.readRate(), r.readRate())
            RAISE_CAPITAL -> BankAction.RaiseCapital(bank, pick(CapitalInstrument.values(), r.readVarInt(), "capital instrument"), r.readMoney())
            BUY_SECURITIES -> BankAction.BuySecurities(bank, pick(SecurityKind.values(), r.readVarInt(), "security kind"), r.readMoney())
            SELL_SECURITIES -> BankAction.SellSecurities(bank, pick(SecurityKind.values(), r.readVarInt(), "security kind"), r.readMoney())
            BORROW_WHOLESALE -> BankAction.BorrowWholesale(bank, r.readMoney())
            REPAY_WHOLESALE -> BankAction.RepayWholesale(bank, r.readMoney())
            REQUEST_EMERGENCY -> BankAction.RequestEmergencyLiquidity(bank, r.readMoney())
            REASSURE -> BankAction.ReassureDepositors(bank, r.readMoney())
            RUN_STRESS_TEST -> BankAction.RunStressTest(bank, r.readString())
            PAY_DIVIDEND -> BankAction.PayDividend(bank, r.readMoney())
            else -> throw ByteReader.CorruptDataException("unknown bank action tag $t")
        }
    }
}
