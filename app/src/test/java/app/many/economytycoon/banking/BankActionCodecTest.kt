package app.many.economytycoon.banking

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.finance.CreditRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BankActionCodecTest {
    private val b = BankId(12)

    private val samples: List<BankAction> = listOf(
        BankAction.SetDepositPremium(b, DepositProduct.TERM, -35),
        BankAction.SetLoanMargin(b, 120),
        BankAction.SetLendingStandards(b, CreditRating.BB, Rate.percent(30)),
        BankAction.SetSectorAppetite(b, LoanSector.MORTGAGE, Rate.percent(85)),
        BankAction.SetGrowthAppetite(b, Rate.percent(10)),
        BankAction.SetCapitalPolicy(b, Rate.percent(4), Rate.percent(25)),
        BankAction.SetFundingPolicy(b, Rate.percent(20), Rate.percent(12), Rate.percent(140)),
        BankAction.RaiseCapital(b, CapitalInstrument.ADDITIONAL_TIER1, Money.units(2_500_000)),
        BankAction.BuySecurities(b, SecurityKind.GOVERNMENT_LONG, Money.units(77)),
        BankAction.SellSecurities(b, SecurityKind.CORPORATE_BONDS, Money.units(9)),
        BankAction.BorrowWholesale(b, Money.units(1)),
        BankAction.RepayWholesale(b, Money.units(2)),
        BankAction.RequestEmergencyLiquidity(b, Money.units(3)),
        BankAction.ReassureDepositors(b, Money.units(4)),
        BankAction.RunStressTest(b, "severe"),
        BankAction.PayDividend(b, Money.units(5)),
        BankAction.FoundBank("Harbour Trust", CountryId(2), CompanyId(9), Money.units(1_000_000), true),
        BankAction.FoundBank("Rival", CountryId(1), null, Money.units(2_000_000), false),
    )

    private fun roundTrip(a: BankAction): BankAction {
        val w = ByteWriter()
        BankActionCodec.write(a, w)
        return BankActionCodec.read(ByteReader(w.toByteArray()))
    }

    @Test fun everyVariantRoundTrips() {
        for (a in samples) assertEquals(a, roundTrip(a))
    }

    @Test fun samplesCoverEveryVariant() {
        val kinds = samples.map { it.javaClass }.toSet()
        assertEquals(17, kinds.size)
    }

    @Test fun encodingIsStable() {
        val w1 = ByteWriter(); BankActionCodec.write(samples[1], w1)
        val w2 = ByteWriter(); BankActionCodec.write(samples[1], w2)
        assertTrue(w1.toByteArray().contentEquals(w2.toByteArray()))
    }

    @Test fun differentActionsEncodeDifferently() {
        val encoded = samples.map { a -> ByteWriter().also { BankActionCodec.write(a, it) }.toByteArray().toList() }
        assertEquals(samples.size, encoded.toSet().size)
    }

    @Test fun unknownTagIsCorrupt() {
        val w = ByteWriter(); w.writeVarInt(99); w.writeVarLong(1)
        try { BankActionCodec.read(ByteReader(w.toByteArray())); fail("expected corrupt data") } catch (e: ByteReader.CorruptDataException) { }
    }

    @Test fun badEnumIndexIsCorrupt() {
        val w = ByteWriter(); w.writeVarInt(1); w.writeVarLong(1); w.writeVarInt(77); w.writeVarLong(0)
        try { BankActionCodec.read(ByteReader(w.toByteArray())); fail("expected corrupt data") } catch (e: ByteReader.CorruptDataException) { }
    }

    @Test fun truncatedInputIsCorrupt() {
        val w = ByteWriter(); BankActionCodec.write(samples[7], w)
        val bytes = w.toByteArray()
        try { BankActionCodec.read(ByteReader(bytes.copyOf(bytes.size - 3))); fail("expected corrupt data") } catch (e: ByteReader.CorruptDataException) { }
    }
}
