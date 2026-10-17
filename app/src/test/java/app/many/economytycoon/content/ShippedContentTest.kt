package app.many.economytycoon.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ShippedContentTest {
    @Test fun everyPackIsPartOfTheRepository() {
        val repo = ShippedContent.repository
        assertEquals(16, ShippedContent.packs.size)
        assertSame(repo, ShippedContent.repository)
        assertTrue(repo.allGoods.size > 50)
        assertTrue(repo.allFacilityTypes.isNotEmpty() && repo.allTechnologies.isNotEmpty())
    }

    @Test fun packIdsAreUnique() {
        assertEquals(ShippedContent.packs.size, ShippedContent.packs.map { it.version }.toSet().size)
    }
}
