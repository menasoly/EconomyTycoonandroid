package app.many.economytycoon.ui.present

import app.many.economytycoon.banking.BankAction
import app.many.economytycoon.banking.BankStatus
import app.many.economytycoon.banking.BankingFx
import app.many.economytycoon.banking.BankingService
import app.many.economytycoon.banking.RunStage
import app.many.economytycoon.banking.StaticBankingInputs
import app.many.economytycoon.banking.SupervisoryRung
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RandomProvider
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.CreditRating
import app.many.economytycoon.session.Formatting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BankingPresenterTest {
    private val inputs = StaticBankingInputs()
    private val country = BankingFx.country
    private val me = CompanyId(7)
    private val money: (Money) -> String = { Formatting.compactMoney(it) }

    private fun sector(): BankingService {
        val s = BankingService()
        s.seedRivals(country, 3, inputs, Tick(0), RandomProvider(3L))
        s.found(BankAction.FoundBank("Mine", country, me, Money.units(1_000_000), true), inputs, Tick(0), RandomProvider(1L)).getOrThrow()
        return s
    }

    @Test fun overviewSeparatesMineFromRivals() {
        val o = BankingPresenter.overview(sector(), inputs, country, me, money)
        assertEquals(1, o.mine.size)
        assertEquals(3, o.rivals.size)
        assertTrue(o.mine.single().yours)
        assertTrue(o.rivals.none { it.yours })
        assertEquals("Mine", o.mine.single().name)
    }

    @Test fun overviewWithoutOwnerShowsEveryBankAsRival() {
        val o = BankingPresenter.overview(sector(), inputs, country, null, money)
        assertEquals(0, o.mine.size)
        assertEquals(4, o.rivals.size)
        assertFalse(o.canFound)
    }

    @Test fun rivalsAreOrderedBySize() {
        val o = BankingPresenter.overview(sector(), inputs, country, me, money)
        val sizes = o.rivals.map { it.tiles.first().value }
        assertEquals(3, sizes.size)
    }

    @Test fun foundingIsBlockedAtTheLimit() {
        val s = sector()
        val o = BankingPresenter.overview(s, inputs, country, me, money, maxPlayerBanks = 1)
        assertFalse(o.canFound)
        assertTrue(o.foundingNote.contains("most allowed"))
    }

    @Test fun healthySectorHasNoTroubleTile() {
        val o = BankingPresenter.overview(sector(), inputs, country, me, money)
        val banks = o.tiles.first()
        assertEquals("Banks", banks.label)
        assertEquals(Tone.GOOD, banks.tone)
        assertEquals("None in trouble", banks.note)
    }

    @Test fun rowShowsFourTilesAndNoAlertsForAHealthyBank() {
        val s = sector()
        val bank = s.banksOf(me).single()
        val row = BankingPresenter.row(s, bank, inputs, money, true)
        assertEquals(4, row.tiles.size)
        assertTrue(row.alerts.isEmpty())
        assertEquals("Active", row.status)
    }

    @Test fun marketFactsIncludePolicyRateAndFund() {
        val o = BankingPresenter.overview(sector(), inputs, country, me, money)
        val labels = o.market.map { it.label }
        assertTrue("Policy rate" in labels)
        assertTrue("Guarantee fund" in labels)
        assertTrue("Stress in the system" in labels)
    }

    @Test fun statusTonesCoverEveryStatus() {
        for (st in BankStatus.values()) BankingLabels.statusTone(st)
        assertEquals(Tone.GOOD, BankingLabels.statusTone(BankStatus.ACTIVE))
        assertEquals(Tone.BAD, BankingLabels.statusTone(BankStatus.IN_RESOLUTION))
        assertEquals(Tone.MUTED, BankingLabels.statusTone(BankStatus.LIQUIDATED))
    }

    @Test fun rungAndRunTonesEscalate() {
        assertEquals(Tone.GOOD, BankingLabels.rungTone(SupervisoryRung.NORMAL))
        assertEquals(Tone.WARN, BankingLabels.rungTone(SupervisoryRung.BUFFER))
        assertEquals(Tone.BAD, BankingLabels.rungTone(SupervisoryRung.CRITICAL))
        assertEquals(Tone.GOOD, BankingLabels.runTone(RunStage.CALM))
        assertEquals(Tone.WARN, BankingLabels.runTone(RunStage.WATCHFUL))
        assertEquals(Tone.BAD, BankingLabels.runTone(RunStage.PANIC))
    }

    @Test fun ratingTonesFollowGrade() {
        assertEquals(Tone.GOOD, BankingLabels.ratingTone(CreditRating.AA))
        assertEquals(Tone.NEUTRAL, BankingLabels.ratingTone(CreditRating.BBB))
        assertEquals(Tone.WARN, BankingLabels.ratingTone(CreditRating.BB))
        assertEquals(Tone.BAD, BankingLabels.ratingTone(CreditRating.CCC))
    }

    @Test fun confidenceIsClampedForDisplay() {
        assertEquals(Formatting.percentPpm(1_000_000L), BankingLabels.confidence(5_000_000L))
        assertEquals(Formatting.percentPpm(0L), BankingLabels.confidence(-10L))
    }

    @Test fun coverageCapsAtTenTimes() {
        assertEquals("10x+", BankingLabels.coverage(app.many.economytycoon.core.Rate(50_000_000L)))
        assertEquals("1.50x", BankingLabels.coverage(app.many.economytycoon.core.Rate(1_500_000L)))
    }

    @Test fun overviewIsDeterministic() {
        assertEquals(
            BankingPresenter.overview(sector(), inputs, country, me, money),
            BankingPresenter.overview(sector(), inputs, country, me, money),
        )
    }
}
