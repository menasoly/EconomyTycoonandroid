package app.many.economytycoon.companies

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.IndustryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Rate
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import app.many.economytycoon.finance.Chart
import app.many.economytycoon.finance.Holder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanyServiceTest {
    private fun rule(o: app.many.economytycoon.core.Outcome<*>) = o.rejectionOrNull()?.rule

    @Test fun foundingFundsTheCompanyAndIssuesShares() {
        val r = CompanyRig()
        val c = r.found("Acme Mills", capital = Money.units(250_000))
        assertEquals(Money.units(250_000), r.finance.ledger.cash(c.owner))
        val table = r.finance.equity.table(c.owner)!!
        assertEquals(r.companies.params.initialShares, table.held(c.founder, "common"))
        assertEquals(CompanyStatus.ACTIVE, c.status)
        assertTrue(r.finance.invariants().isEmpty())
    }

    @Test fun namesAreTrimmedValidatedAndUnique() {
        val r = CompanyRig()
        r.found("  Acme Mills ")
        assertEquals("Acme Mills", r.companies.all().single().name)
        assertEquals(Rules.NAME_TAKEN, rule(r.companies.found("acme mills", CompanyKind.AI, r.country, null, Holder("f", 9), Money.units(50_000), r.gov, Tick(0))))
        assertEquals(Rules.INVALID_ARGUMENT, rule(r.companies.found("ab", CompanyKind.AI, r.country, null, Holder("f", 9), Money.units(50_000), r.gov, Tick(0))))
        assertEquals(Rules.INVALID_ARGUMENT, rule(r.companies.found("x".repeat(41), CompanyKind.AI, r.country, null, Holder("f", 9), Money.units(50_000), r.gov, Tick(0))))
        assertEquals(Rules.INVALID_ARGUMENT, rule(r.companies.found("Bad\u0007Name", CompanyKind.AI, r.country, null, Holder("f", 9), Money.units(50_000), r.gov, Tick(0))))
    }

    @Test fun capitalMustBeInRangeExceptForStateEnterprises() {
        val r = CompanyRig()
        assertEquals(Rules.INVALID_ARGUMENT, rule(r.companies.found("Tiny Co", CompanyKind.AI, r.country, null, Holder("f", 1), Money.units(10), r.gov, Tick(0))))
        assertEquals(Rules.INVALID_ARGUMENT, rule(r.companies.found("Huge Co", CompanyKind.AI, r.country, null, Holder("f", 2), Money.units(900_000_000), r.gov, Tick(0))))
        assertNotNull(r.companies.found("State Rail", CompanyKind.STATE, r.country, null, Holder("f", 3), Money.units(100_000_000), r.gov, Tick(0)).getOrNull())
    }

    @Test fun playersAreLimitedInHowManyCompaniesTheyRun() {
        val r = CompanyRig(); val me = Holder.player(1)
        repeat(r.companies.params.maxPlayerCompanies) { r.found("Player Co $it", CompanyKind.PLAYER, founder = me) }
        assertEquals(Rules.LIMIT_EXCEEDED, rule(r.companies.found("One Too Many", CompanyKind.PLAYER, r.country, null, me, Money.units(50_000), r.gov, Tick(0))))
        assertNotNull(r.companies.found("Other Players Co", CompanyKind.PLAYER, r.country, null, Holder.player(2), Money.units(50_000), r.gov, Tick(0)).getOrNull())
    }

    @Test fun renameAndHeadquarters() {
        val r = CompanyRig(); val a = r.found("Alpha Works"); val b = r.found("Beta Works")
        assertEquals(Rules.NAME_TAKEN, rule(r.companies.rename(b.id, "alpha works")))
        r.companies.rename(a.id, "Alpha Works Ltd").getOrThrow()
        assertEquals("Alpha Works Ltd", r.companies.company(a.id)!!.name)
        r.companies.setHeadquarters(a.id, CityId(4)).getOrThrow()
        assertEquals(CityId(4), a.hq)
        assertEquals(b, r.companies.byName(" BETA works "))
        assertEquals(a, r.companies.byOwner(a.owner))
    }

    @Test fun departmentEffectsSaturate() {
        val b = DepartmentBudgets()
        val scale = Money.units(100_000)
        assertEquals(0L, b.effectPpm(Department.MARKETING, scale, 800_000))
        b.set(Department.MARKETING, Money.units(500))
        val small = b.effectPpm(Department.MARKETING, scale, 800_000)
        b.set(Department.MARKETING, Money.units(5_000))
        val mid = b.effectPpm(Department.MARKETING, scale, 800_000)
        b.set(Department.MARKETING, Money.units(500_000))
        val big = b.effectPpm(Department.MARKETING, scale, 800_000)
        assertTrue(small < mid && mid < big && big < 800_000)
        assertTrue((mid - small) > (big - mid) / 10)
    }

    @Test fun departmentIntentsCoverEachFundedDepartment() {
        val r = CompanyRig(); val c = r.found("Spender")
        val b = r.companies.budgets(c.id)
        b.set(Department.RESEARCH, Money.units(100)); b.set(Department.MARKETING, Money.units(50))
        val intents = r.companies.departmentIntents(Tick(5), r.finance.households)
        assertEquals(2, intents.size)
        val results = r.finance.settleTick(Tick(5), intents)
        assertTrue(results.all { it.paid.raw > 0 })
        assertEquals(Money.units(150), r.finance.ledger.balance(c.owner, Chart.RESEARCH_EXPENSE) + r.finance.ledger.balance(c.owner, Chart.MARKETING_EXPENSE))
    }

    @Test fun researchPointsAccumulateAndCanBeSpent() {
        val r = CompanyRig(); val c = r.found("Labs")
        r.companies.budgets(c.id).set(Department.RESEARCH, Money.units(40))
        repeat(10) { r.companies.advanceDepartments { Money.units(10_000) } }
        val p = r.companies.progress(c.id)
        assertEquals(400L, p.researchPoints)
        assertTrue(p.spendResearch(150))
        assertFalse(p.spendResearch(500))
        assertEquals(250L, p.researchPoints)
    }

    @Test fun legalSpendBuildsComplianceGradually() {
        val r = CompanyRig(); val c = r.found("Lawful")
        r.companies.budgets(c.id).set(Department.LEGAL, Money.units(2_000))
        r.companies.advanceDepartments { Money.units(50_000) }
        val first = r.companies.progress(c.id).compliancePpm
        repeat(60) { r.companies.advanceDepartments { Money.units(50_000) } }
        val later = r.companies.progress(c.id).compliancePpm
        assertTrue(first > 0 && later > first * 3)
    }

    @Test fun marketingBuildsAwarenessAndItFadesWithoutIt() {
        val brand = Brand()
        repeat(80) { brand.advance(400_000, 800_000, 1_000_000) }
        val peak = brand.awarenessPpm
        assertTrue(peak > 200_000)
        repeat(80) { brand.advance(0, 800_000, 1_000_000) }
        assertTrue(brand.awarenessPpm < peak)
    }

    @Test fun loyaltyFollowsQualityAndPriceFairness() {
        val good = Brand(); val greedy = Brand()
        repeat(300) { good.advance(0, 900_000, 1_000_000); greedy.advance(0, 900_000, 1_600_000) }
        assertTrue(good.loyaltyPpm > greedy.loyaltyPpm)
        assertTrue(good.loyaltyPpm in 850_000..900_000)
    }

    @Test fun reputationNudgesAndShocksStayInRange() {
        val rep = Reputation()
        rep.nudge(Reputation.Dimension.CUSTOMERS, 1_000_000, 500_000)
        assertEquals(750_000L, rep.customers)
        rep.shock(Reputation.Dimension.REGULATORS, -900_000)
        assertEquals(0L, rep.regulators)
        rep.shock(Reputation.Dimension.INVESTORS, 900_000)
        assertEquals(1_000_000L, rep.investors)
        assertTrue(rep.overall in 0..1_000_000)
    }

    @Test fun brandPremiumNeedsAwarenessAndLoyalty() {
        val b = Brand()
        assertEquals(0L, b.premiumPpm)
        b.restore(1_000_000, 1_000_000)
        assertEquals(200_000L, b.premiumPpm)
    }

    @Test fun advancingBrandsCoversEveryIndustryOfEveryActiveCompany() {
        val r = CompanyRig(); val c = r.found("Branded")
        r.companies.addIndustry(c.id, IndustryId("food")); r.companies.addIndustry(c.id, IndustryId("steel"))
        r.companies.advanceBrands({ 300_000 }, { _, _ -> 900_000 }, { _, _ -> 1_000_000 })
        assertEquals(2, r.companies.brands.brandsOf(c.id).size)
        assertTrue(r.companies.brands.brand(c.id, IndustryId("food")).awarenessPpm > 0)
    }

    @Test fun savedStateRoundTrips() {
        val r = CompanyRig()
        val a = r.found("Alpha Works"); val b = r.found("Beta Works")
        r.companies.addIndustry(a.id, IndustryId("food"))
        r.companies.budgets(a.id).set(Department.RESEARCH, Money.units(75))
        r.companies.advanceDepartments { Money.units(1000) }
        r.companies.brands.reputation(a.id).shock(Reputation.Dimension.CUSTOMERS, 100_000)
        r.companies.brands.brand(a.id, IndustryId("food")).restore(200_000, 300_000)
        r.companies.groups.setStake(a.id, b.id, Rate.percent(60))
        r.companies.policies.setSell(a.id, SellRule(app.many.economytycoon.core.FacilityId(3), app.many.economytycoon.core.GoodId("flour"), PriceStrategy.UNDERCUT, 50_000))
        r.companies.policies.setBuy(a.id, BuyRule(app.many.economytycoon.core.FacilityId(3), app.many.economytycoon.core.GoodId("wheat"), app.many.economytycoon.core.Quantity.units(40)))
        r.companies.policies.setDividend(a.id, DividendPolicy(400_000, 20, 60))
        r.insolvency.open(b.id, Tick(4))
        r.acquisitions.makeOffer(a.id, b.id, 100, Money.units(1), Tick(3)).getOrThrow()

        val w = ByteWriter(); CompanyCodec.write(r.companies, r.insolvency, r.acquisitions, w)
        val copy = CompanyRig()
        // same finance, fresh registries
        val companies2 = CompanyService(r.ids, r.finance)
        val insolvency2 = InsolvencyService(r.finance, companies2)
        val acquisitions2 = AcquisitionService(r.finance, companies2)
        CompanyCodec.read(ByteReader(w.toByteArray()), companies2, insolvency2, acquisitions2)
        fun h(c: CompanyService, i: InsolvencyService, a2: AcquisitionService) =
            app.many.economytycoon.core.CanonicalHasher().also { c.hashInto(it); i.hashInto(it); a2.hashInto(it) }.finish().toHex()
        assertEquals(h(r.companies, r.insolvency, r.acquisitions), h(companies2, insolvency2, acquisitions2))
        assertEquals(b.id, companies2.groups.controllerOf(b.id)?.let { b.id })
        assertNotNull(copy)
    }

    @Test fun badVersionIsRejected() {
        val w = ByteWriter(); w.writeVarInt(5)
        val r = CompanyRig()
        var failed = false
        try { CompanyCodec.read(ByteReader(w.toByteArray()), r.companies, r.insolvency, r.acquisitions) } catch (e: ByteReader.CorruptDataException) { failed = true }
        assertTrue(failed)
        assertNull(r.companies.company(app.many.economytycoon.core.CompanyId(1)))
    }
}
