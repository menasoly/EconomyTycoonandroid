package app.many.economytycoon.tourism

import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaxationTest {
    private val w = TourismWorld()

    @Test fun vatInsideAGrossPrice() {
        assertEquals(Money.units(20), TourismTax.vatInside(Money.units(120), 200_000L))
        assertEquals(Money.ZERO, TourismTax.vatInside(Money.units(120), 0L))
        assertEquals(Money.ZERO, TourismTax.vatInside(Money.ZERO, 200_000L))
        assertEquals(Money.ZERO, TourismTax.vatInside(Money.units(-5), 200_000L))
    }

    @Test fun netPlusVatIsGross() {
        val gross = Money.units(1_234)
        val vat = TourismTax.vatInside(gross, 130_000L)
        val net = gross - vat
        assertTrue(Math.abs(net.raw * 1_130_000L / 1_000_000L - gross.raw) <= 2L)
    }

    @Test fun levyIsFlatPlusShare() {
        assertEquals(Money.units(300), TourismTax.levy(100, Money.units(3), Money.units(10_000), 0L))
        assertEquals(Money.units(500), TourismTax.levy(0, Money.units(3), Money.units(10_000), 50_000L))
        assertEquals(Money.units(800), TourismTax.levy(100, Money.units(3), Money.units(10_000), 50_000L))
        assertEquals(Money.ZERO, TourismTax.levy(-5, Money.ZERO, Money.units(-10), 50_000L))
    }

    @Test fun owedCombinesBothTaxesForADestination() {
        w.service.setTaxes(w.dest.id, Money.units(2), 20_000L, 100_000L).getOrThrow()
        val owed = TourismTax.owed(Money.units(11_000), 400, w.dest)
        assertEquals(Money.units(1_000), owed.vat)
        assertEquals(Money.units(800 + 220), owed.levy)
        assertEquals(owed.vat + owed.levy, owed.total)
    }

    @Test fun taxSettingsAreRangeChecked() {
        assertEquals(Rules.INVALID_ARGUMENT, w.service.setTaxes(w.dest.id, Money(-1), 0L, 0L).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, w.service.setTaxes(w.dest.id, Money.ZERO, 300_001L, 0L).rejectionOrNull()?.rule)
        assertEquals(Rules.INVALID_ARGUMENT, w.service.setTaxes(w.dest.id, Money.ZERO, 0L, 500_001L).rejectionOrNull()?.rule)
        assertEquals(Rules.UNKNOWN_ENTITY, w.service.setTaxes(DestinationId(99), Money.ZERO, 0L, 0L).rejectionOrNull()?.rule)
        assertTrue(w.service.setTaxes(w.dest.id, Money.units(5), 300_000L, 500_000L).isOk)
    }

    @Test fun priceFeelRisesWithTheLevy() {
        val ref = Money.units(150)
        assertEquals(0L, TourismTax.priceFeelPpm(w.dest, ref))
        w.service.setTaxes(w.dest.id, Money.units(3), 0L, 0L).getOrThrow()
        val flat = TourismTax.priceFeelPpm(w.dest, ref)
        assertTrue(flat > 0L)
        w.service.setTaxes(w.dest.id, Money.units(3), 40_000L, 0L).getOrThrow()
        assertEquals(flat + 40_000L, TourismTax.priceFeelPpm(w.dest, ref))
        assertEquals(0L, TourismTax.priceFeelPpm(w.dest, Money.ZERO))
    }

    @Test fun aHeavyLevyDentsDemand() {
        val calm = TourismWorld(seed = 11L)
        calm.hotel(); calm.days(40)
        val taxed = TourismWorld(seed = 11L)
        taxed.service.setTaxes(taxed.dest.id, Money.units(25), 150_000L, 0L).getOrThrow()
        taxed.hotel(); taxed.days(40)
        val a = calm.state.destinations[0].days.takeLast(20).sumOf { it.arrivals }
        val b = taxed.state.destinations[0].days.takeLast(20).sumOf { it.arrivals }
        assertTrue("$a vs $b", b < a)
    }

    @Test fun levyFlowsToTheGovernmentInTheMonthlySettlement() {
        w.service.setTaxes(w.dest.id, Money.units(3), 0L, 0L).getOrThrow()
        w.hotel()
        w.days(30)
        val run = w.settle()
        val levies = run.intents.filter { it.key.startsWith("tour:levy:") }
        assertTrue(levies.isNotEmpty())
        assertTrue(levies.all { it.lines.sumOf { l -> l.amount.raw } > 0L })
    }
}
