package app.many.economytycoon.ip

import app.many.economytycoon.content.IndustryFamily
import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.ip.IpTestSupport.a
import app.many.economytycoon.ip.IpTestSupport.b
import app.many.economytycoon.ip.IpTestSupport.eu
import app.many.economytycoon.ip.IpTestSupport.milling
import app.many.economytycoon.ip.IpTestSupport.us
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class IpCommandCodecTest {
    private val terms = RoyaltyTerms(RoyaltyBasis.HYBRID, Money.units(2), Rate.percent(3), Money.ZERO, Money.units(50), Money.units(5_000), Money.units(70))
    private val every: List<IpCommand> = listOf(
        IpCommand.FilePatent(a, milling, listOf(us, eu), BreadthChoice.BROAD, 14, IndustryFamily.values()[0]),
        IpCommand.FilePatent(a, milling, listOf(us)),
        IpCommand.AbandonPatent(a, PatentId(3), eu), IpCommand.AbandonPatent(a, PatentId(3)),
        IpCommand.SetAutoMaintain(a, PatentId(3), false), IpCommand.PayMaintenance(a, PatentId(3), us), IpCommand.DeclareEssential(a, PatentId(3), true),
        IpCommand.KeepSecret(a, milling), IpCommand.InvestInSecret(a, SecretId(2), Money.units(500)), IpCommand.DropSecret(a, SecretId(2)), IpCommand.StealSecret(b, SecretId(2)),
        IpCommand.OfferLicense(a, OfferRequest(IpOwner.company(a), b, listOf(PatentId(1), PatentId(2)), terms, LicenseKind.EXCLUSIVE, LicenseScope.of(IndustryFamily.values().take(2), listOf(us)), 700, true, PoolId(4))),
        IpCommand.OfferLicense(a, OfferRequest(IpOwner.company(a), b, listOf(PatentId(1)), RoyaltyTerms(RoyaltyBasis.LUMP_SUM, lumpSum = Money.units(1)))),
        IpCommand.AcceptLicense(b, LicenseId(5)), IpCommand.DeclineLicense(b, LicenseId(5)), IpCommand.WithdrawOffer(a, LicenseId(5)), IpCommand.TerminateLicense(a, LicenseId(5)),
        IpCommand.RequestFrandLicense(b, PatentId(1)), IpCommand.AuditLicense(a, LicenseId(5)),
        IpCommand.ProposeCrossLicense(a, b, listOf(PatentId(1)), listOf(PatentId(2), PatentId(3)), 1_000),
        IpCommand.FormPool(a, "Pool", TechField.values()[1], terms, 150_000L), IpCommand.JoinPool(b, PoolId(1), listOf(PatentId(2))), IpCommand.LeavePool(b, PoolId(1)),
        IpCommand.DissolvePool(a, PoolId(1)), IpCommand.OfferPool(a, PoolId(1), b),
        IpCommand.ReportInfringement(a, PatentId(1), b, us), IpCommand.SendNotice(a, DisputeId(1)), IpCommand.SendNotice(a, DisputeId(1), Money.units(77)),
        IpCommand.FileSuit(a, DisputeId(1)), IpCommand.Appeal(b, DisputeId(1)), IpCommand.WithdrawSuit(a, DisputeId(1)),
        IpCommand.OfferSettlement(b, DisputeId(1), Money.units(3)), IpCommand.AcceptSettlement(a, DisputeId(1)), IpCommand.ChallengePatent(b, PatentId(1), us),
        IpCommand.StartDesignAround(b, PatentId(1)), IpCommand.StartDesignAround(b, PatentId(1), DisputeId(9)), IpCommand.CancelDesignAround(b, DesignAroundId(2)),
        IpCommand.RequestCompulsory(b, PatentId(1), us, CompulsoryGround.ANTI_COMPETITIVE), IpCommand.WithdrawCompulsory(b, CompulsoryId(1)),
        IpCommand.ListPatent(a, PatentId(1), Money.units(10)), IpCommand.UnlistPatent(a, PatentId(1)), IpCommand.BuyPatent(b, PatentId(1)),
        IpCommand.SetPolicy(a, IpPolicy(EnforcementStance.PASSIVE, 123_000L, BreadthChoice.NARROW, listOf(eu), false)),
    )

    @Test fun everyCommandRoundTrips() {
        for (c in every) assertEquals(c.toString(), c, IpCommandCodec.decode(IpCommandCodec.encode(c)))
    }

    @Test fun everyCommandClassIsCovered() {
        val classes = every.map { it.javaClass }.toSet()
        val expected = IpCommand::class.java.declaredClasses.toSet()
        assertEquals(expected, classes)
    }

    @Test fun encodingIsStable() {
        for (c in every) assertTrue(IpCommandCodec.encode(c).contentEquals(IpCommandCodec.encode(c)))
    }

    @Test fun unknownTagIsRejected() {
        try { IpCommandCodec.decode(byteArrayOf(120, 2)); fail("expected failure") } catch (e: ByteReader.CorruptDataException) { assertTrue(e.message!!.contains("tag")) }
    }

    @Test fun truncatedCommandsAreRejected() {
        for (c in every) {
            val bytes = IpCommandCodec.encode(c)
            if (bytes.size < 3) continue
            try { IpCommandCodec.decode(bytes.copyOf(bytes.size - 1)); fail("accepted a truncated ${c::class.simpleName}") } catch (e: ByteReader.CorruptDataException) { /* expected */ }
        }
    }

    @Test fun badEnumInsideACommandIsRejected() {
        val bytes = IpCommandCodec.encode(IpCommand.RequestCompulsory(b, PatentId(1), us, CompulsoryGround.PUBLIC_HEALTH))
        bytes[bytes.size - 1] = 40
        try { IpCommandCodec.decode(bytes); fail("expected failure") } catch (e: ByteReader.CorruptDataException) { assertTrue(e.message!!.contains("ground")) }
    }
}
