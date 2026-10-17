package app.many.economytycoon.urban

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.LedgerOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrbanLedgerTest {
    private val country = UrbanTestSupport.country
    private val firm = CompanyId(4)
    private val tick = Tick(300)

    private fun settlement(
        charges: Long = 0, share: Long = 0, due: Long = 0, deduction: Long = 0, guarantee: Long = 0, capex: Long = 0,
    ) = ConcessionSettlement(
        ConcessionId(2), firm, Money.units(charges), Money.units(share), Money.units(due), Money.units(deduction), Money.units(guarantee), Money.units(capex),
    )

    @Test fun `a petition fee goes from the company to the government`() {
        val i = UrbanLedger.petitionFee(firm, country, PetitionId(3), Money.units(2_500), tick).single()
        assertEquals(LedgerOwner.company(firm), i.payer)
        assertEquals(LedgerOwner.government(country), i.payee)
        assertEquals(Money.units(2_500), i.total)
        assertEquals("urban:fee:3", i.key)
    }

    @Test fun `no money means no intent`() {
        assertTrue(UrbanLedger.petitionFee(firm, country, PetitionId(3), Money.ZERO, tick).isEmpty())
        assertTrue(UrbanLedger.lobbying(firm, PetitionId(3), 1, Money.ZERO, tick).isEmpty())
        assertTrue(UrbanLedger.concessionMonth(country, settlement(), tick).isEmpty())
        assertTrue(UrbanLedger.termination(country, ConcessionId(1), firm, Money.ZERO, tick).isEmpty())
        assertTrue(UrbanLedger.renegotiation(country, ConcessionId(1), firm, Money.units(-5), tick).isEmpty())
    }

    @Test fun `lobbying keys differ by sequence`() {
        val a = UrbanLedger.lobbying(firm, PetitionId(3), 1, Money.units(10), tick).single()
        val b = UrbanLedger.lobbying(firm, PetitionId(3), 2, Money.units(10), tick).single()
        val c = UrbanLedger.lobbying(firm, PetitionId(4), 1, Money.units(10), tick).single()
        assertEquals(3, setOf(a.key, b.key, c.key).size)
        assertEquals(LedgerOwner.SYSTEM, a.payee)
    }

    @Test fun `an operating month yields charges, share and availability`() {
        val list = UrbanLedger.concessionMonth(country, settlement(charges = 900, share = 270, due = 500, deduction = 100, guarantee = 40), tick)
        assertEquals(setOf("charges", "share", "availability", "guarantee"), list.map { it.key.substringAfterLast(':') }.toSet())
        val byName = list.associateBy { it.key.substringAfterLast(':') }
        assertEquals(Money.units(900), byName.getValue("charges").total)
        assertEquals(Money.units(270), byName.getValue("share").total)
        assertEquals(Money.units(400), byName.getValue("availability").total)
        assertEquals(LedgerOwner.government(country), byName.getValue("availability").payer)
        assertEquals(LedgerOwner.company(firm), byName.getValue("share").payer)
    }

    @Test fun `construction produces only a capital payment`() {
        val list = UrbanLedger.concessionMonth(country, settlement(capex = 5_000), tick)
        assertEquals(1, list.size)
        assertEquals(Money.units(5_000), list[0].total)
        assertTrue(list[0].key.endsWith(":capex"))
    }

    @Test fun `all keys of a month are unique and differ between months`() {
        val s = settlement(charges = 10, share = 3, due = 5, guarantee = 1, capex = 7)
        val march = UrbanLedger.concessionMonth(country, s, Tick(90)).map { it.key }
        val april = UrbanLedger.concessionMonth(country, s, Tick(120)).map { it.key }
        assertEquals(march.size, march.toSet().size)
        assertTrue((march + april).toSet().size == march.size + april.size)
    }

    @Test fun `termination and renegotiation pay the company`() {
        val end = UrbanLedger.termination(country, ConcessionId(7), firm, Money.units(1_000), tick).single()
        assertEquals(LedgerOwner.government(country), end.payer)
        assertEquals(LedgerOwner.company(firm), end.payee)
        val re = UrbanLedger.renegotiation(country, ConcessionId(7), firm, Money.units(300), tick).single()
        assertTrue(end.key != re.key)
        assertEquals(Money.units(300), re.total)
    }

    @Test fun `settlement totals add up`() {
        val s = settlement(charges = 1000, share = 300, due = 400, deduction = 50, guarantee = 20)
        assertEquals(Money.units(350), s.availabilityNet)
        assertEquals(Money.units(370), s.cityPays)
        assertEquals(Money.units(1000 - 300 + 370), s.concessionaireIncome)
    }
}
