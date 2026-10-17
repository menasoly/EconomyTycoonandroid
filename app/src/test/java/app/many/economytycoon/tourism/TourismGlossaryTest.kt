package app.many.economytycoon.tourism

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TourismGlossaryTest {
    @Test fun keysAreUniqueAndLowercaseWords() {
        val keys = TourismGlossary.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(keys.all { k -> k.isNotEmpty() && k.all { it.isLowerCase() || it == '-' } })
    }

    @Test fun everyEntryHasATermAndAnExplanation() {
        for (e in TourismGlossary.entries) {
            assertTrue(e.term.isNotBlank())
            assertTrue("${e.key} text", e.text.length > 40)
            assertTrue(e.text.trim().endsWith("."))
        }
    }

    @Test fun lookupByKey() {
        assertEquals("Revenue per available room", TourismGlossary.find("revpar")?.term)
        assertNull(TourismGlossary.find("nothing"))
        assertNotNull(TourismGlossary.find("overbooking"))
    }

    @Test fun theMainIdeasAreCovered() {
        for (k in listOf("occupancy", "adr", "revpar", "dynamic-pricing", "overbooking", "allotment", "commission", "seasonality", "tourism-levy", "local-spending")) {
            assertNotNull(k, TourismGlossary.find(k))
        }
    }
}
