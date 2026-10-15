package app.many.economytycoon.simulation

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstructionSupplierTest {
    private class Rig(capital: Money = Money.units(5_000_000)) {
        val sim = Simulation(SimTestSupport.config(9, 0), SimTestSupport.contentWithShed, bootstrap = false)
        val e = sim.economy
        val country = e.world.countries().first()
        val region = e.world.regionsOf(country.id).first().id
        val id = CompanyId((sim.execute(Actor.Player(1), Command.FoundCompany("Builder Works", country.id, null, capital)) as Outcome.Ok).value.created!!)
        val supplier = ConstructionSupplier(e)

        fun typeWithMaterials(): FacilityTypeId = FacilityTypeId("test_shed")
    }

    @Test fun ownStockIsUsedBeforeAnythingIsBought() {
        val r = Rig()
        val type = r.typeWithMaterials()
        val def = r.e.content.facilityType(type)
        val project = r.e.production.startConstruction(r.id, type, r.region, null, null, null, "Site", r.e.tick).getOrThrow()
        val store = r.e.production.grantFacility(r.id, FacilityTypeId("grain_farm"), r.region, null, null, null, r.e.tick)
        val need = def.buildInputs.first()
        store.inventory.add(need.good, need.quantity, Money.units(100))
        assertTrue(r.supplier.run(Tick(1)) > 0)
        assertEquals(Quantity.ZERO, project.missing(need.good))
        assertEquals(Quantity.ZERO, store.inventory.quantity(need.good))
        assertEquals(Money.ZERO, r.supplier.contractorSpend)
    }

    @Test fun contractorsOnlyStepInNearTheEndOfTheBuild() {
        val r = Rig()
        val type = r.typeWithMaterials()
        val project = r.e.production.startConstruction(r.id, type, r.region, null, null, null, "Site", r.e.tick).getOrThrow()
        r.supplier.run(Tick(1))
        assertEquals(Money.ZERO, r.supplier.contractorSpend)
        assertTrue(!project.materialsComplete())
        r.sim.advance(project.totalTicks - 1)
        // The pipeline's own supplier has been buying as the last stage approached.
        assertTrue(project.materialsComplete())
    }

    @Test fun aCompanyThatCannotPayIsNotSuppliedOnCredit() {
        val r = Rig(Money.units(50_000))
        val type = r.typeWithMaterials()
        val project = r.e.production.startConstruction(r.id, type, r.region, null, null, null, "Site", r.e.tick).getOrThrow()
        r.sim.advance(project.totalTicks)
        r.supplier.run(r.e.tick)
        val cash = r.e.finance.ledger.cash(app.many.economytycoon.finance.LedgerOwner.company(r.id))
        assertTrue(!cash.isNegative)
    }

    @Test fun finishedProjectsBecomeFacilitiesWhenSuppliedAndPaid() {
        val r = Rig()
        val type = r.typeWithMaterials()
        r.e.production.startConstruction(r.id, type, r.region, null, null, null, "Site", r.e.tick)
        repeat(r.e.content.facilityType(type).buildTicks + 5) { r.sim.advance() }
        assertEquals(1, r.e.production.facilitiesOf(r.id).count { it.type == type })
        assertTrue(r.e.finance.invariants().isEmpty())
    }
}
