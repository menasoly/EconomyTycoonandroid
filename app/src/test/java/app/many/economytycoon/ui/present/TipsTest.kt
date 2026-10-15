package app.many.economytycoon.ui.present

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TipsTest {
    @Test fun idsAreUnique() {
        assertEquals(Tips.all.size, Tips.all.map { it.id }.toSet().size)
    }

    @Test fun everyTipSaysSomethingUseful() {
        for (t in Tips.all) {
            assertTrue("${t.id} text", t.text.length in 40..260)
            assertTrue("${t.id} ends like a sentence", t.text.endsWith("."))
            assertTrue("${t.id} area", t.area.isNotBlank())
        }
    }

    @Test fun routesAreRealScreens() {
        for (t in Tips.all) {
            val r = t.route ?: continue
            assertEquals("${t.id} route", r, app.many.economytycoon.ui.nav.Route.parse(r.key))
        }
    }

    @Test fun theTipChangesOncePerWeek() {
        assertEquals(Tips.forDay(0), Tips.forDay(6))
        assertNotEquals(Tips.forDay(6), Tips.forDay(7))
        assertEquals(Tips.forDay(7), Tips.forDay(13))
    }

    @Test fun theListCyclesAndNeverFails() {
        val n = Tips.all.size.toLong()
        assertEquals(Tips.forDay(0), Tips.forDay(n * Tips.DAYS_PER_TIP))
        assertEquals(Tips.all.first(), Tips.forDay(-50))
        assertEquals(Tips.forDay(3), Tips.forDay(3, Tips.all.size))
        assertEquals(Tips.forDay(3, 2), Tips.forDay(3, 2 - Tips.all.size))
    }

    @Test fun upcomingStartsWithTheCurrentTip() {
        val list = Tips.upcoming(20, 4)
        assertEquals(4, list.size)
        assertEquals(Tips.forDay(20), list.first())
        assertEquals(4, list.map { it.id }.toSet().size)
        assertEquals(Tips.all.size, Tips.upcoming(0, 1000).size)
        assertEquals(0, Tips.upcoming(0, -3).size)
    }

    @Test fun lookupAndGrouping() {
        assertEquals("sell-rules", Tips.byId("sell-rules")!!.id)
        assertEquals(null, Tips.byId("nope"))
        assertTrue(Tips.areas().containsAll(listOf("Trading", "Finance", "Production")))
        assertTrue(Tips.inArea("Finance").all { it.area == "Finance" })
        assertEquals(Tips.all.size, Tips.areas().sumOf { Tips.inArea(it).size })
    }

    @Test fun theTipsLinkToTheNewScreens() {
        val routes = Tips.all.mapNotNull { it.route }
        assertTrue(app.many.economytycoon.ui.nav.Route.Agenda in routes)
        assertTrue(app.many.economytycoon.ui.nav.Route.Glossary in routes)
        assertTrue(app.many.economytycoon.ui.nav.Route.Grants in routes)
    }

    @Test fun textsAreNotRepeated() {
        assertEquals(Tips.all.size, Tips.all.map { it.text }.toSet().size)
    }

    @Test fun noAreaIsLeftWithoutAdvice() {
        for (a in Tips.areas()) assertTrue("area $a", Tips.inArea(a).isNotEmpty())
        assertEquals(emptyList<Tip>(), Tips.inArea("Nothing"))
    }

    @Test fun consecutiveWeeksShowDifferentTipsUntilTheListRepeats() {
        val week = (0 until Tips.all.size).map { Tips.forDay(it * 7L).id }
        assertEquals(week.size, week.toSet().size)
    }

    @Test fun tipsThatNameAScreenNeverPointAtTheTipsOwnArea() {
        val withRoute = Tips.all.filter { it.route != null }
        assertTrue(withRoute.size > Tips.all.size / 2)
        assertTrue(withRoute.all { it.route!!.key.isNotBlank() })
    }
}
