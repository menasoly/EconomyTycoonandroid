package app.many.economytycoon.production

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.FacilityTypeId
import app.many.economytycoon.core.IdAllocators
import app.many.economytycoon.core.MachineTypeId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Quantity
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.RecipeId
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.TechId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.core.DepositId
import app.many.economytycoon.production.ProductionTestSupport.Rig
import app.many.economytycoon.production.ProductionTestSupport.company
import app.many.economytycoon.production.ProductionTestSupport.flour
import app.many.economytycoon.production.ProductionTestSupport.region
import app.many.economytycoon.production.ProductionTestSupport.wheat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionServiceTest {
    private val bakery = FacilityTypeId("bakery")
    private val other = CompanyId(2)

    private fun rejected(o: app.many.economytycoon.core.Outcome<*>, rule: String) =
        assertEquals(rule, o.rejectionOrNull()?.rule)

    @Test fun constructionPaysInstallmentsAndCompletes() {
        val r = Rig(); val charged = ArrayList<Money>()
        r.svc.payer = Payer { _, amt, _ -> charged += amt; true }
        val p = r.svc.startConstruction(company, bakery, region, null, null, null, "Main bakery", Tick(0)).getOrThrow()
        val reports = r.run(4)
        assertEquals(ProjectState.DONE, p.state)
        assertEquals(1, reports.last().completedProjects.size)
        assertEquals(Money.units(50_000), Money(charged.sumOf { it.raw }))
        assertEquals(4, charged.size)
        val f = r.svc.facilitiesOf(company).single()
        assertEquals("Main bakery", f.name)
        assertEquals(1, f.lines.size)
    }

    @Test fun constructionStallsWhenBroke() {
        val r = Rig(); var cash = Money.units(20_000)
        r.svc.payer = Payer { _, amt, _ -> if (cash >= amt) { cash -= amt; true } else false }
        val p = r.svc.startConstruction(company, bakery, region, null, null, null, "", Tick(0)).getOrThrow()
        val reports = r.run(6)
        assertEquals(ProjectState.WAITING_FUNDS, p.state)
        assertTrue(p.ticksDone in 1..2)
        assertTrue(reports.last().stalledProjects.contains(p.id))
        cash = Money.units(1_000_000)
        r.run(4)
        assertEquals(ProjectState.DONE, p.state)
    }

    @Test fun lastStageWaitsForMaterials() {
        val r = Rig()
        val p = r.svc.startConstruction(company, FacilityTypeId("mine"), region, null, null, DepositId(3), "", Tick(0)).getOrThrow()
        r.run(10)
        assertEquals(ProjectState.WAITING_MATERIALS, p.state)
        assertEquals(5, p.ticksDone)
        r.svc.deliverMaterials(company, p.id, wheat, Quantity.units(4)).getOrThrow()
        r.run(2)
        assertEquals(ProjectState.WAITING_MATERIALS, p.state)
        val taken = r.svc.deliverMaterials(company, p.id, wheat, Quantity.units(20)).getOrThrow()
        assertEquals(Quantity.units(6), taken)
        r.run(2)
        assertEquals(ProjectState.DONE, p.state)
        assertEquals(DepositId(3), r.svc.facilitiesOf(company).single().depositId)
    }

    @Test fun extractionFacilityNeedsADeposit() {
        val r = Rig()
        rejected(r.svc.startConstruction(company, FacilityTypeId("mine"), region, null, null, null, "", Tick(0)), Rules.INVALID_ARGUMENT)
    }

    @Test fun parcelCannotHostTwoBuildings() {
        val r = Rig(); val parcel = app.many.economytycoon.core.ParcelId(5)
        r.svc.startConstruction(company, bakery, region, null, parcel, null, "", Tick(0)).getOrThrow()
        rejected(r.svc.startConstruction(other, bakery, region, null, parcel, null, "", Tick(0)), Rules.DUPLICATE)
    }

    @Test fun cancellingRefundsPartOfWhatWasPaid() {
        val r = Rig()
        val p = r.svc.startConstruction(company, bakery, region, null, null, null, "", Tick(0)).getOrThrow()
        r.run(2)
        val refund = r.svc.cancelConstruction(company, p.id).getOrThrow()
        assertEquals(Money(p.paid.raw * 4 / 10), refund)
        r.run(5)
        assertTrue(r.svc.facilities().isEmpty())
        rejected(r.svc.cancelConstruction(company, p.id), Rules.WRONG_STATE)
    }

    @Test fun storageExpansionAddsCapacity() {
        val r = Rig(); val f = r.bakery()
        val before = f.inventory.capacity
        r.svc.startStorageExpansion(company, f.id, Tick(0)).getOrThrow()
        rejected(r.svc.startStorageExpansion(company, f.id, Tick(0)), Rules.DUPLICATE)
        r.run(3)
        assertEquals(before + Quantity(before.raw / 4), f.inventory.capacity)
    }

    @Test fun otherCompaniesCannotCommandMyFacility() {
        val r = Rig(); val f = r.bakery()
        rejected(r.svc.setRecipe(other, f.id, f.lines[0].id, RecipeId("make_flour")), Rules.NOT_OWNER)
        rejected(r.svc.shutdown(other, f.id), Rules.NOT_OWNER)
        rejected(r.svc.demolish(other, f.id), Rules.NOT_OWNER)
    }

    @Test fun recipeMustMatchTheFacilityType() {
        val r = Rig(); val f = r.bakery()
        rejected(r.svc.setRecipe(company, f.id, f.lines[0].id, RecipeId("dig_ore")), Rules.INVALID_ARGUMENT)
        rejected(r.svc.setRecipe(company, f.id, f.lines[0].id, RecipeId("nope")), Rules.UNKNOWN_ENTITY)
    }

    @Test fun cannotSwapRecipeMidBatchButCanAbandon() {
        val r = Rig(); val f = r.bakery(); r.stock(f, wheat, 40.0, 10.0)
        r.svc.setRecipe(company, f.id, f.lines[0].id, RecipeId("make_flour")).getOrThrow()
        r.step()
        rejected(r.svc.setRecipe(company, f.id, f.lines[0].id, null), Rules.WRONG_STATE)
        val lost = r.svc.abandonBatch(company, f.id, f.lines[0].id).getOrThrow()
        assertEquals(Money.units(40), lost)
        r.svc.setRecipe(company, f.id, f.lines[0].id, null).getOrThrow()
    }

    @Test fun lineCountIsLimitedAndCostsMoney() {
        val r = Rig(); val f = r.bakery()
        r.svc.addLine(company, f.id).getOrThrow()
        rejected(r.svc.addLine(company, f.id), Rules.LIMIT_EXCEEDED)
        val poor = Rig(); val g = poor.bakery(); poor.svc.payer = Payer { _, _, _ -> false }
        rejected(poor.svc.addLine(company, g.id), Rules.INSUFFICIENT_FUNDS)
    }

    @Test fun machinesNeedTechAndMoney() {
        val r = Rig(); val f = r.bakery()
        r.svc.hasTech = { _, t -> t != TechId("mixing") }
        rejected(r.svc.installMachine(company, f.id, f.lines[0].id, MachineTypeId("mixer"), Tick(0)), Rules.NOT_UNLOCKED)
        r.svc.installMachine(company, f.id, f.lines[0].id, MachineTypeId("oven"), Tick(0)).getOrThrow()
        rejected(r.svc.installMachine(company, f.id, f.lines[0].id, MachineTypeId("oven"), Tick(0)), Rules.DUPLICATE)
        r.svc.payer = Payer { _, _, _ -> false }
        val g = r.bakery()
        rejected(r.svc.installMachine(company, g.id, g.lines[0].id, MachineTypeId("oven"), Tick(0)), Rules.INSUFFICIENT_FUNDS)
    }

    @Test fun removingAMachineReturnsAConditionAdjustedPrice() {
        val r = Rig(); val f = r.bakery(withOven = true)
        val back = r.svc.removeMachine(company, f.id, f.lines[0].id, MachineTypeId("oven")).getOrThrow()
        assertEquals(Money.units(3_000), back)
        assertTrue(f.lines[0].machines.isEmpty())
    }

    @Test fun maintenanceChargesOnlyWhatIsNeeded() {
        val r = Rig(); val f = r.bakery(withOven = true)
        val spent = r.svc.maintain(company, f.id, Money.units(500)).getOrThrow()
        assertEquals(Money.ZERO, spent)
        f.lines[0].machines[0].restore(500_000L, 10, -1, 0)
        val spent2 = r.svc.maintain(company, f.id, Money.units(100_000)).getOrThrow()
        assertEquals(Money.units(5_000), spent2)
        assertEquals(1_000_000L, f.lines[0].machines[0].conditionPpm)
    }

    @Test fun demolishingRefundsAndWritesOffStock() {
        val r = Rig(); val f = r.bakery(); r.stock(f, wheat, 10.0, 10.0)
        val d = r.svc.demolish(company, f.id).getOrThrow()
        assertEquals(Money.units(15_000), d.refund)
        assertEquals(Money.units(100), d.writtenOffStock)
        assertEquals(FacilityStatus.DEMOLISHED, f.status)
        assertTrue(r.svc.facilitiesOf(company).isEmpty())
        rejected(r.svc.shutdown(company, f.id), Rules.WRONG_STATE)
    }

    @Test fun demolishRefusesWhileStockIsReserved() {
        val r = Rig(); val f = r.bakery(); r.stock(f, wheat, 10.0, 10.0)
        f.inventory.reserve(wheat, Quantity.units(3)).getOrThrow()
        rejected(r.svc.demolish(company, f.id), Rules.WRONG_STATE)
    }

    @Test fun qualityAssuranceRangeIsChecked() {
        val r = Rig(); val f = r.bakery()
        rejected(r.svc.setQualityAssurance(company, f.id, Rate.percent(95)), Rules.INVALID_ARGUMENT)
        r.svc.setQualityAssurance(company, f.id, Rate.percent(40)).getOrThrow()
        val rep = r.step()
        assertEquals(Money.units(24), rep.facilities[0].upkeep)
    }

    @Test fun transferChangesOwnership() {
        val r = Rig(); val f = r.bakery()
        r.svc.transfer(f.id, other).getOrThrow()
        assertEquals(other, f.owner)
        assertEquals(1, r.svc.facilitiesOf(other).size)
        assertTrue(r.svc.facilitiesOf(company).isEmpty())
    }

    @Test fun inventoryValueSumsFacilities() {
        val r = Rig(); val a = r.bakery(); val b = r.bakery()
        r.stock(a, wheat, 10.0, 10.0); r.stock(b, flour, 4.0, 25.0)
        assertEquals(Money.units(200), r.svc.inventoryValue(company))
    }

    @Test fun saveAndLoadRoundTripsEverything() {
        val r = Rig(); val f = r.bakery(withOven = true); r.stock(f, flour, 500.0, 25.0); r.stock(f, ProductionTestSupport.bread, 50.0, 40.0)
        r.svc.setRecipe(company, f.id, f.lines[0].id, RecipeId("make_bread")).getOrThrow()
        r.svc.startConstruction(company, FacilityTypeId("mine"), region, null, null, DepositId(2), "", Tick(0)).getOrThrow()
        r.run(12)
        val w = ByteWriter(); ProductionCodec.write(r.svc, w)
        val copy = Rig()
        ProductionCodec.read(ByteReader(w.toByteArray()), copy.svc)
        assertEquals(r.hash(), copy.hash())
        // and the copy continues identically
        copy.random.restore(r.random.snapshot()); copy.tick = r.tick
        r.run(10); copy.run(10)
        assertEquals(r.hash(), copy.hash())
    }

    @Test fun corruptSaveVersionIsRejected() {
        val w = ByteWriter(); w.writeVarInt(99)
        var failed = false
        try { ProductionCodec.read(ByteReader(w.toByteArray()), Rig().svc) } catch (e: ByteReader.CorruptDataException) { failed = true }
        assertTrue(failed)
    }
}
