package app.many.economytycoon.ui.present

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlossaryTest {
    @Test fun idsAndTermsAreUnique() {
        assertEquals(Glossary.terms.size, Glossary.terms.map { it.id }.toSet().size)
        assertEquals(Glossary.terms.size, Glossary.terms.map { it.term.lowercase() }.toSet().size)
    }

    @Test fun everyDefinitionIsAFullSentence() {
        for (t in Glossary.terms) {
            assertTrue(t.id, t.definition.length >= 30)
            assertTrue(t.id, t.definition.first().isUpperCase())
            assertTrue(t.id, t.definition.endsWith("."))
        }
    }

    @Test fun everyCrossReferenceResolves() {
        for (t in Glossary.terms) for (id in t.seeAlso) assertTrue("${t.id} -> $id", Glossary.byId(id) != null)
    }

    @Test fun nothingPointsAtItself() {
        for (t in Glossary.terms) assertFalse(t.id, t.id in t.seeAlso)
    }

    @Test fun everyGroupIsKnownAndUsed() {
        val known = setOf(Glossary.ACCOUNTS, Glossary.RATIOS, Glossary.CREDIT, Glossary.MARKETS, Glossary.PRODUCTION, Glossary.PEOPLE, Glossary.OWNERSHIP, Glossary.WORLD)
        assertEquals(known, Glossary.groups().toSet())
        for (g in known) assertTrue(g, Glossary.inGroup(g).size >= 3)
    }

    @Test fun termsInAGroupAreAlphabetical() {
        for (g in Glossary.groups()) {
            val names = Glossary.inGroup(g).map { it.term.lowercase() }
            assertEquals(g, names.sorted(), names)
        }
    }

    @Test fun relatedKeepsTheListedOrder() {
        val gp = Glossary.byId("gross-profit")!!
        assertEquals(gp.seeAlso, Glossary.related(gp).map { it.id })
        assertTrue(Glossary.related(Glossary.byId("tick")!!).isEmpty())
    }

    @Test fun mentionedByFindsTheLinksPointingHere() {
        val cogs = Glossary.byId("cogs")!!
        assertTrue(Glossary.mentionedBy(cogs).any { it.id == "gross-profit" })
        assertTrue(Glossary.mentionedBy(cogs).none { it.id == "cogs" })
    }

    @Test fun anEmptySearchListsEverythingAlphabetically() {
        val all = Glossary.search("  ")
        assertEquals(Glossary.terms.size, all.size)
        assertEquals(all.map { it.term.lowercase() }.sorted(), all.map { it.term.lowercase() })
    }

    @Test fun titlesThatStartWithTheQueryComeFirst() {
        val r = Glossary.search("net")
        assertTrue(r.first().term.lowercase().startsWith("net"))
        val firstOther = r.indexOfFirst { !it.term.lowercase().startsWith("net") }
        assertTrue(firstOther == -1 || r.drop(firstOther).none { it.term.lowercase().startsWith("net") })
    }

    @Test fun everyWordMustMatchSomewhere() {
        val r = Glossary.search("interest tax")
        assertTrue(r.isNotEmpty())
        assertTrue(r.all { (it.term + " " + it.definition).lowercase().let { s -> s.contains("interest") && s.contains("tax") } })
        assertTrue(Glossary.search("zzzz").isEmpty())
    }

    @Test fun searchIsNotCaseSensitive() {
        assertEquals(Glossary.search("ebitda").map { it.id }, Glossary.search("EBITDA").map { it.id })
        assertTrue(Glossary.search("ebitda").any { it.id == "ebitda" })
    }

    @Test fun lookupsByIdWork() {
        assertEquals("Revenue", Glossary.byId("revenue")!!.term)
        assertNull(Glossary.byId("nope"))
    }

    @Test fun theUnitsUsedInTheGameAreExplained() {
        assertTrue(Glossary.byId("tick")!!.definition.contains("360"))
        assertTrue(Glossary.byId("corporate-tax")!!.definition.contains("90"))
    }
}
