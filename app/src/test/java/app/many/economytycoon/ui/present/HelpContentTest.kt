package app.many.economytycoon.ui.present

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpContentTest {
    @Test fun topicsHaveUniqueIdsAndRealText() {
        val topics = HelpContent.topics
        assertEquals(topics.size, topics.map { it.id }.toSet().size)
        for (t in topics) {
            assertTrue(t.id, t.title.isNotBlank() && t.summary.isNotBlank())
            assertTrue(t.id, t.paragraphs.size >= 3 && t.paragraphs.all { it.length > 40 })
        }
    }

    @Test fun lookupAndSearchWork() {
        assertEquals("Getting started", HelpContent.byId("start")!!.title)
        assertNull(HelpContent.byId("nothing"))
        assertEquals(HelpContent.topics, HelpContent.search("  "))
        assertTrue(HelpContent.search("strike").any { it.id == "people" })
        assertTrue(HelpContent.search("wages morale").any { it.id == "people" })
        assertTrue(HelpContent.search("zzzzqq").isEmpty())
    }
}
