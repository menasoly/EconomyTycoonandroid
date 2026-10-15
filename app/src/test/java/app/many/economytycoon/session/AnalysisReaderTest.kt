package app.many.economytycoon.session

import app.many.economytycoon.analysis.Measure
import app.many.economytycoon.ui.present.PresentSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisReaderTest {
    private val session by lazy { PresentSupport.session("Ratio Works", 11L).also { s -> repeat(60) { s.step() } } }

    @Test fun theSelectedCompanyHasAnAnalysis() {
        val v = session.analysis()
        assertNotNull(v)
        assertEquals("Ratio Works", v!!.name)
        assertEquals(session.selectedCompany, v.company)
    }

    @Test fun historyComesFromTheRecordedSamples() {
        val v = session.analysis()!!
        assertTrue("sixty days of play should be enough for an outlook", v.analysis.outlook != null)
    }

    @Test fun peersAreOtherTradingCompanies() {
        val v = session.analysis()!!
        assertTrue(v.peerCount >= 0)
        assertTrue(v.standings.size <= Measure.values().size)
        if (v.peerCount < 3) assertTrue(v.standings.isEmpty())
    }

    @Test fun peersNeverIncludeTheCompanyItself() {
        val reader = AnalysisReader(session.economy, session.reports, session.stats)
        val me = session.selectedCompany!!
        assertTrue(me !in reader.peersOf(me))
    }

    @Test fun peerListIsInIdOrder() {
        val reader = AnalysisReader(session.economy, session.reports, session.stats)
        val peers = reader.peersOf(session.selectedCompany!!)
        assertEquals(peers.sorted(), peers)
    }

    @Test fun anUnknownCompanyGivesNothing() {
        val reader = AnalysisReader(session.economy, session.reports, session.stats)
        assertNull(reader.view(app.many.economytycoon.core.CompanyId(99_999)))
        assertNull(reader.analyse(app.many.economytycoon.core.CompanyId(99_999)))
    }

    @Test fun aFreshGameHasNoOutlookYet() {
        val fresh = PresentSupport.session("New Works", 12L)
        val v = fresh.analysis()!!
        assertNull(v.analysis.outlook)
    }

    @Test fun measureListMatchesTheEnum() {
        assertEquals(Measure.values().toList(), AnalysisReader.measures())
    }
}
