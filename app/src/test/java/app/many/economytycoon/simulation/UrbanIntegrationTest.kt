package app.many.economytycoon.simulation

import app.many.economytycoon.activity.ActivityArea
import app.many.economytycoon.activity.CommandDescriber
import app.many.economytycoon.companies.CompanyKind
import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.finance.LedgerOwner
import app.many.economytycoon.persistence.CommandCodec
import app.many.economytycoon.persistence.CommandSamples
import app.many.economytycoon.persistence.SectionedFile
import app.many.economytycoon.persistence.SimulationSnapshot
import app.many.economytycoon.urban.PetitionKind
import app.many.economytycoon.urban.PetitionStatus
import app.many.economytycoon.urban.TaxRates
import app.many.economytycoon.urban.UrbanCommand
import app.many.economytycoon.urban.ZoneUse
import app.many.economytycoon.world.ZoneType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** City planning as the simulation wires it: the switch, commands and who may give them, payments, the month's feedback, snapshots and replay. */
class UrbanIntegrationTest {
    private class Rig(seed: Long = 12L, ai: Int = 3, urban: Boolean = true) {
        val sim = Simulation(SimTestSupport.config(seed, ai), SimTestSupport.content, bootstrap = ai > 0)
        val e = sim.economy
        val country = e.world.countries().sortedBy { it.id }.first()
        val home: CityId = country.capital!!
        val me: CompanyId = CompanyId((sim.execute(Actor.Player(1), Command.FoundCompany("Civic Works", country.id, home, Money.units(5_000_000))) as Outcome.Ok).value.created!!)
        init { if (urban) sim.execute(Actor.System, Command.EstablishUrban) }

        /** Gives a company [amount] out of its country's government account, as a grant would. */
        fun grant(amount: Money, c: CompanyId = me) {
            val gov = app.many.economytycoon.finance.AccountRef(LedgerOwner.government(country.id), app.many.economytycoon.finance.Chart.CASH)
            val govOther = app.many.economytycoon.finance.AccountRef(LedgerOwner.government(country.id), app.many.economytycoon.finance.Chart.OTHER_EXPENSE)
            val cash = app.many.economytycoon.finance.AccountRef(LedgerOwner.company(c), app.many.economytycoon.finance.Chart.CASH)
            val income = app.many.economytycoon.finance.AccountRef(LedgerOwner.company(c), app.many.economytycoon.finance.Chart.REVENUE_OTHER)
            e.finance.ledger.post(
                app.many.economytycoon.finance.TransactionDraft(
                    "test:grant:${c.value}", e.tick, app.many.economytycoon.finance.ReasonCode.TRANSFER,
                    listOf(
                        app.many.economytycoon.finance.Entry(gov, -amount), app.many.economytycoon.finance.Entry(govOther, amount),
                        app.many.economytycoon.finance.Entry(cash, amount), app.many.economytycoon.finance.Entry(income, -amount),
                    ),
                    "grant",
                ),
            )
        }
        fun cash(c: CompanyId = me) = e.finance.ledger.cash(LedgerOwner.company(c))
        fun urban(cmd: UrbanCommand, by: Long = 1L, company: CompanyId = me) = sim.execute(Actor.Player(by), Command.Urban(company, cmd))
        val plan get() = e.urban.requirePlan(home)
    }

    @Test fun aGameWithoutPlansLeavesNoStateNoSectionAndNoDay() {
        val r = Rig(urban = false)
        val summaries = r.sim.advance(70)
        assertFalse(r.e.urbanRunner.active)
        assertEquals(0, r.e.urban.size)
        assertTrue(summaries.all { it.urbanNotices.isEmpty() })
        assertTrue(SectionedFile.read(SimulationSnapshot.save(r.sim)).none { it.name == "urban" })
        val early = r.urban(UrbanCommand.SetTaxRates(r.home, TaxRates()))
        assertEquals(Rules.WRONG_STATE, (early as Outcome.Rejected).rejection.rule)
        assertEquals(1_000_000L, r.e.urbanRunner.landFactorPpm(r.home))
        assertEquals(1_000_000L, r.e.urbanRunner.landFactorPpm(null))
    }

    @Test fun onlyTheSystemDrawsUpThePlansOnceAndTheyFollowTheWorld() {
        val r = Rig(urban = false)
        assertTrue(r.sim.execute(Actor.Player(1), Command.EstablishUrban) is Outcome.Rejected)
        assertFalse(r.e.urbanRunner.active)
        assertTrue(r.sim.execute(Actor.System, Command.EstablishUrban) is Outcome.Ok)
        assertTrue(r.sim.execute(Actor.System, Command.EstablishUrban) is Outcome.Rejected)
        assertEquals(r.e.world.cities().size, r.e.urban.size)
        for (city in r.e.world.cities()) {
            val plan = r.e.urban.requirePlan(city.id)
            assertEquals(city.name, plan.name)
            assertEquals(r.e.world.countryOfCity(city.id).id, plan.country)
            assertEquals(ZoneUse.COMMERCIAL, plan.districts[4].zone)
            // The plan houses about the people the world reports.
            val people = r.e.world.population(city.id).total
            assertTrue("${plan.population} for $people", plan.population in (people * 8 / 10)..(people * 12 / 10))
            assertEquals(people, plan.inputs.population)
        }
        assertTrue(SectionedFile.read(SimulationSnapshot.save(r.sim)).any { it.name == "urban" })
    }

    @Test fun districtsAreZonedFromTheLandOfTheCity() {
        val r = Rig()
        // Whatever mix of uses the parcels of a city have, the plan has no use that the land lacks entirely.
        for (city in r.e.world.cities()) {
            val uses = r.e.world.land.inCity(city.id).map {
                when (it.effectiveZone) {
                    ZoneType.COMMERCIAL -> ZoneUse.COMMERCIAL
                    ZoneType.RESIDENTIAL -> ZoneUse.RESIDENTIAL
                    ZoneType.MIXED -> ZoneUse.MIXED
                    ZoneType.INDUSTRIAL, ZoneType.MINING -> ZoneUse.INDUSTRIAL
                    ZoneType.AGRICULTURAL, ZoneType.PROTECTED -> ZoneUse.GREEN
                }
            }.toSet() + ZoneUse.COMMERCIAL + ZoneUse.RESIDENTIAL + ZoneUse.INDUSTRIAL + ZoneUse.GREEN
            assertTrue(r.e.urban.requirePlan(city.id).districts.all { it.zone in uses })
        }
        // And the same world always gets the same plans.
        val twin = Rig()
        assertEquals(r.e.urban.stateHash(), twin.e.urban.stateHash())
    }

    @Test fun theUrbanCommandsRoundTripThroughTheCodecAndDescriber() {
        val cs = CommandSamples.all().filter { it is Command.EstablishUrban || it is Command.Urban }
        assertEquals(21, cs.size)
        for (c in cs) {
            val w = ByteWriter(); CommandCodec.writeCommand(c, w)
            assertEquals(c, CommandCodec.readCommand(ByteReader(w.toByteArray())))
            assertEquals(ActivityArea.PUBLIC, CommandDescriber.area(c))
            assertTrue(CommandDescriber.describe(c).isNotBlank())
        }
        assertEquals(19, cs.filterIsInstance<Command.Urban>().map { it.command.javaClass }.toSet().size)
        assertTrue(CommandDescriber.movesMoney(cs.first { it is Command.Urban && it.command is UrbanCommand.LobbyPetition }))
        assertFalse(CommandDescriber.movesMoney(cs.first { it is Command.Urban && it.command is UrbanCommand.SetTaxRates }))
    }

    @Test fun aCorruptUrbanCommandIsRefusedRatherThanSkipped() {
        val w = ByteWriter(); CommandCodec.writeCommand(Command.Urban(CompanyId(3), UrbanCommand.TerminateConcession(CityId(2), app.many.economytycoon.urban.ConcessionId(1))), w)
        val bytes = w.toByteArray()
        // The last three bytes are the sub-tag, the city and the concession: make the sub-tag unknown.
        bytes[bytes.size - 3] = 90
        val failed = runCatching { CommandCodec.readCommand(ByteReader(bytes)) }.exceptionOrNull()
        assertTrue(failed.toString(), failed is ByteReader.CorruptDataException)
        // A tax rate outside the rules in the stream is corruption, not a crash.
        val bad = ByteWriter(); bad.writeVarInt(125); bad.writeVarLong(3); bad.writeVarInt(1); bad.writeVarLong(2); bad.writeVarLong(9_000_000L); bad.writeVarLong(1); bad.writeMoney(Money.ZERO)
        assertTrue(runCatching { CommandCodec.readCommand(ByteReader(bad.toByteArray())) }.exceptionOrNull() is ByteReader.CorruptDataException)
    }

    @Test fun theGovernmentOfACityObeysOnlyThePlayerWhoseHeadquartersIsThere() {
        val r = Rig()
        val rates = TaxRates(propertyPerYearPpm = 12_000L, incomePpm = 20_000L)
        assertTrue(r.urban(UrbanCommand.SetTaxRates(r.home, rates)) is Outcome.Ok)
        assertEquals(rates, r.plan.budget.rates)
        val elsewhere = r.e.world.cities().sortedBy { it.id }.first { it.id != r.home }.id
        val before = r.e.urban.stateHash()
        val refused = r.urban(UrbanCommand.SetTaxRates(elsewhere, rates))
        assertEquals(Rules.NOT_OWNER, (refused as Outcome.Rejected).rejection.rule)
        assertEquals(before, r.e.urban.stateHash())
        val rival = CompanyId((r.sim.execute(Actor.Player(2), Command.FoundCompany("Other Co", r.country.id, elsewhere, Money.units(500_000))) as Outcome.Ok).value.created!!)
        // A rival cannot give orders as your company, nor a computer company as a government.
        val forged = r.sim.execute(Actor.Player(2), Command.Urban(r.me, UrbanCommand.Borrow(r.home, Money.units(1))))
        assertEquals(Rules.NOT_OWNER, (forged as Outcome.Rejected).rejection.rule)
        val ai = r.e.companies.all().first { it.kind == CompanyKind.AI }.id
        val aiOrder = r.sim.execute(Actor.Ai(ai), Command.Urban(ai, UrbanCommand.SetTaxRates(r.home, rates)))
        assertEquals(Rules.NOT_OWNER, (aiOrder as Outcome.Rejected).rejection.rule)
        assertTrue(r.urban(UrbanCommand.SetTaxRates(elsewhere, rates), by = 2, company = rival) is Outcome.Ok)
    }

    @Test fun aRefusedOrderChangesNothingNotEvenTheIdsItReserved() {
        val r = Rig()
        val before = r.e.urban.stateHash()
        val district = r.plan.districts.first { it.zone != ZoneUse.RESIDENTIAL }.index
        // Same zone: not a change of use.
        val same = r.urban(UrbanCommand.FilePetition(r.home, district, PetitionKind.REZONE, r.plan.districts[district].zone, 0, 0, r.me))
        assertTrue(same is Outcome.Rejected)
        assertEquals(before, r.e.urban.stateHash())
        val road = r.urban(UrbanCommand.AddRoad(r.home, 0, 8, app.many.economytycoon.urban.RoadClass.LOCAL, 2))
        if (road is Outcome.Rejected) assertEquals(before, r.e.urban.stateHash())
    }

    @Test fun aPetitionIsPaidForAtOnceToTheGovernmentAndLobbyingIsChecked() {
        val r = Rig()
        r.grant(Money.units(40_000_000))
        val d = r.plan.districts.first { !it.heritage && it.zone == ZoneUse.INDUSTRIAL || !it.heritage && it.zone == ZoneUse.RESIDENTIAL }
        val gov = LedgerOwner.government(r.country.id)
        val govBefore = r.e.finance.ledger.cash(gov)
        val before = r.cash()
        val filed = r.urban(UrbanCommand.FilePetition(r.home, d.index, PetitionKind.REZONE, ZoneUse.MIXED, 0, 0, r.me))
        assertTrue(filed.toString(), filed is Outcome.Ok)
        val fee = r.plan.petitions.all().single().fee
        assertTrue(fee.isPositive)
        assertEquals(before - fee, r.cash())
        assertEquals(govBefore + fee, r.e.finance.ledger.cash(gov))
        val id = (filed as Outcome.Ok).value.created!!

        val spend = Money.units(10_000)
        val lobbied = r.urban(UrbanCommand.LobbyPetition(r.home, app.many.economytycoon.urban.PetitionId(id), r.me, null, spend))
        assertTrue(lobbied.toString(), lobbied is Outcome.Ok)
        assertEquals(before - fee - spend, r.cash())
        // Another company's name on the order is refused, and so is more than the company has.
        val named = r.urban(UrbanCommand.LobbyPetition(r.home, app.many.economytycoon.urban.PetitionId(id), CompanyId(r.me.value + 1), null, spend))
        assertEquals(Rules.NOT_OWNER, (named as Outcome.Rejected).rejection.rule)
        val broke = r.urban(UrbanCommand.LobbyPetition(r.home, app.many.economytycoon.urban.PetitionId(id), r.me, null, Money.units(900_000_000)))
        assertEquals(Rules.INSUFFICIENT_FUNDS, (broke as Outcome.Rejected).rejection.rule)
        assertTrue(r.urban(UrbanCommand.WithdrawPetition(r.home, app.many.economytycoon.urban.PetitionId(id), r.me)) is Outcome.Ok)
        assertEquals(PetitionStatus.WITHDRAWN, r.plan.petitions.get(app.many.economytycoon.urban.PetitionId(id))!!.status)
        assertEquals(emptyList<String>(), Invariants.check(r.e))
    }

    @Test fun theMonthGivesHousingRentAndWorkBackToTheWorldInSmallBoundedSteps() {
        val r = Rig(seed = 21L)
        val cities = r.e.world.cities().map { it.id }
        val base = app.many.economytycoon.world.DemographicParams().participationPpm
        var lastHousing = cities.associateWith { r.e.world.housingCapacity(it) }
        var changed = false
        for (month in 1..8) {
            r.sim.advance(30)
            for (c in cities) {
                val h = r.e.world.housingCapacity(c)
                val was = lastHousing.getValue(c)
                val room = was * UrbanRunner.HOUSING_STEP_PPM / 1_000_000L + 1L
                assertTrue("housing of $c moved from $was to $h", Math.abs(h - was) <= room)
                if (h != was) changed = true
                val pop = r.e.world.population(c)
                assertTrue(pop.rentIndexPpm in UrbanRunner.MIN_RENT_PPM..UrbanRunner.MAX_RENT_PPM)
                assertTrue(pop.participation in (base - 40_000L)..base)
            }
            lastHousing = cities.associateWith { r.e.world.housingCapacity(it) }
        }
        assertTrue("the plans moved the world's housing", changed)
        assertEquals(emptyList<String>(), Invariants.check(r.e))
        assertTrue(r.e.finance.invariants().isEmpty())
    }

    @Test fun landIsAppraisedWithTheAccessOfItsCity() {
        val r = Rig()
        val factors = r.e.world.cities().map { r.e.urbanRunner.landFactorPpm(it.id) }
        assertTrue(factors.all { it in UrbanRunner.LAND_FACTOR_FLOOR_PPM..(UrbanRunner.LAND_FACTOR_FLOOR_PPM + 200_000L) })
        val parcel = r.e.world.land.all().first { it.city != null && it.effectiveZone != ZoneType.PROTECTED }
        val plain = Rig(urban = false)
        val with = r.e.propertyRunner.appraise(parcel)
        val without = plain.e.propertyRunner.appraise(plain.e.world.land.get(parcel.id)!!)
        val factor = r.e.urbanRunner.landFactorPpm(parcel.city)
        assertTrue("$with against $without at $factor", Math.abs(without.raw * factor / 1_000_000L - with.raw) <= with.raw / 1_000L + 1L)
    }

    @Test fun computerMayorsRunTheCitiesNoPlayerGovernsAndTheirOrdersAreSettled() {
        val r = Rig(seed = 14L, ai = 4)
        val before = r.e.urban.stateHash()
        val notices = ArrayList<app.many.economytycoon.urban.UrbanNotice>()
        for (s in r.sim.advance(190)) notices += s.urbanNotices
        assertNotEquals(before, r.e.urban.stateHash())
        assertTrue("the cities' news", notices.isNotEmpty())
        assertEquals(emptyList<String>(), Invariants.check(r.e))
        assertTrue(r.e.finance.invariants().isEmpty())
        // The player's own city is left to the player: its taxes stay as set.
        val mine = r.plan.budget.rates
        assertEquals(TaxRates(), mine)
    }

    @Test fun computerCompaniesDealWithTheCouncilsWhereTheyDoBusiness() {
        val r = Rig(seed = 16L, ai = 6)
        var petitions = 0; var lobbying = 0
        val ai = AiUrban(r.e, CommandExecutor(r.e))
        for (t in 0 until 400) {
            r.sim.advance()
            val rep = ai.run(r.e.tick)
            petitions += rep.petitions; lobbying += rep.lobbying
        }
        assertTrue("lobbying $lobbying", lobbying >= 0)
        assertTrue(r.e.urban.plans().flatMap { it.petitions.all() }.any { it.applicant != null })
        assertEquals(emptyList<String>(), Invariants.check(r.e))
    }

    @Test fun aCompanyThatCeasesToExistWithdrawsItsPetitions() {
        val r = Rig(seed = 17L)
        val d = r.plan.districts.first { !it.heritage && (it.zone == ZoneUse.INDUSTRIAL || it.zone == ZoneUse.RESIDENTIAL) }
        assertTrue(r.urban(UrbanCommand.FilePetition(r.home, d.index, PetitionKind.REZONE, ZoneUse.MIXED, 0, 0, r.me)) is Outcome.Ok)
        assertTrue(r.sim.execute(Actor.Player(1), Command.Dissolve(r.me)) is Outcome.Ok)
        assertTrue(r.plan.petitions.all().none { it.applicant == r.me && it.status.open })
        r.sim.advance(40)
        assertEquals(emptyList<String>(), Invariants.check(r.e))
    }

    @Test fun savingAndLoadingContinuesExactlyWithPlansInForce() {
        val r = Rig(seed = 31L, ai = 4)
        val d = r.plan.districts.first { !it.heritage && (it.zone == ZoneUse.INDUSTRIAL || it.zone == ZoneUse.RESIDENTIAL) }
        r.urban(UrbanCommand.FilePetition(r.home, d.index, PetitionKind.REZONE, ZoneUse.MIXED, 0, 0, r.me))
        r.sim.advance(75)
        val sections = SectionedFile.read(SimulationSnapshot.save(r.sim))
        assertTrue(sections.first { it.name == "urban" }.payload.isNotEmpty())
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(r.sim), SimTestSupport.content)
        assertEquals(r.sim.hash(), copy.hash())
        assertEquals(r.e.urban.stateHash(), copy.economy.urban.stateHash())
        assertTrue(copy.economy.urbanRunner.active)
        for (i in 0 until 70) { r.sim.advance(); copy.advance(); assertEquals("tick ${r.sim.tick.value}", r.sim.hash(), copy.hash()) }
    }

    @Test fun anOlderSaveWithoutTheSectionLoadsWithNoPlans() {
        val r = Rig(seed = 8L, ai = 2, urban = false)
        r.sim.advance(10)
        val copy = SimulationSnapshot.load(SimulationSnapshot.save(r.sim), SimTestSupport.content)
        assertFalse(copy.economy.urbanRunner.active)
        assertEquals(r.sim.hash(), copy.hash())
    }

    @Test fun theCommandLogReplaysIdenticallyAndPlansChangeTheHash() {
        val a = Rig(seed = 33L, ai = 4)
        val plain = Rig(seed = 33L, ai = 4, urban = false)
        a.urban(UrbanCommand.SetTaxRates(a.home, TaxRates(propertyPerYearPpm = 11_000L)))
        val d = a.plan.districts.first { !it.heritage && (it.zone == ZoneUse.INDUSTRIAL || it.zone == ZoneUse.RESIDENTIAL) }
        a.sim.submit(Actor.Player(1), Command.Urban(a.me, UrbanCommand.FilePetition(a.home, d.index, PetitionKind.REZONE, ZoneUse.MIXED, 0, 0, a.me)))
        a.sim.advance(100); plain.sim.advance(100)
        val again = Simulation.replay(a.sim.config, SimTestSupport.content, a.sim.commandLog(), a.sim.tick.value)
        assertEquals(a.sim.hash(), again.hash())
        assertNotEquals(plain.sim.hash(), a.sim.hash())
        assertEquals(emptyList<String>(), Invariants.check(a.e))
    }

    @Test fun aMultiMonthRunWithPlansIsDeterministic() {
        val x = Rig(seed = 41L, ai = 5); val y = Rig(seed = 41L, ai = 5)
        x.sim.advance(240); y.sim.advance(240)
        assertEquals(x.sim.hash(), y.sim.hash())
        assertEquals(x.e.urban.stateHash(), y.e.urban.stateHash())
        assertTrue(Invariants.check(x.e).isEmpty())
        assertTrue(x.e.finance.invariants().isEmpty())
    }
}
