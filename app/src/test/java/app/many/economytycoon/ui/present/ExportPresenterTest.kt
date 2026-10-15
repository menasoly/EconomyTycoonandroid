package app.many.economytycoon.ui.present

import app.many.economytycoon.export.ExportKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportPresenterTest {
    @Test fun itemsKeepTheKindsAndTheirWords() {
        val items = ExportPresenter.items(ExportKind.values().toList())
        assertEquals(ExportKind.values().size, items.size)
        assertEquals(ExportKind.YEARLY.label, items.first { it.kind == ExportKind.YEARLY }.title)
        assertTrue(items.all { it.blurb.isNotBlank() })
    }

    @Test fun theSubjectNamesTheCompanyAndTheFile() {
        assertEquals("Test Works: Yearly results", ExportPresenter.subject("Test Works", ExportKind.YEARLY))
    }

    @Test fun aSessionOffersEveryKind() {
        val s = PresentSupport.session("Share Works", 23L)
        val m = ExportPresenter.build(s)!!
        assertEquals("Share Works", m.company)
        assertEquals(ExportKind.values().size, m.items.size)
        assertEquals(ExportPresenter.NOTE, m.note)
    }

    @Test fun eachItemCanBeBuilt() {
        val s = PresentSupport.session("Share Works", 23L)
        for (item in ExportPresenter.build(s)!!.items) assertTrue(item.kind.name, ExportPresenter.file(s, item.kind) != null)
    }

    @Test fun anEmptyKindIsFlaggedSoTheScreenCanSaySo() {
        val s = PresentSupport.session("Share Works", 23L)
        val activity = ExportPresenter.file(s, ExportKind.ACTIVITY)!!
        assertTrue(activity.isEmpty)
        assertFalse(ExportPresenter.file(s, ExportKind.BALANCE)!!.isEmpty)
    }
}
