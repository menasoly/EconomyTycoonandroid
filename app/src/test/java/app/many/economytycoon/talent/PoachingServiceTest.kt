package app.many.economytycoon.talent

import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Outcome
import app.many.economytycoon.core.Rules
import app.many.economytycoon.core.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoachingServiceTest {
    private val rig = ServiceRig()
    private val victimKey = TalentTestSupport.key(company = 2L, role = "machinist")
    private val hunterKey = TalentTestSupport.key(company = 1L, role = "machinist")
    private val hunter = CompanyId(1)
    private val victim = CompanyId(2)

    private fun facts(vickWage: Long = 90L, morale: Long = 650_000L) = arrayOf(
        TalentTestSupport.fact(victimKey, 100, wage = Money.units(vickWage), morale = morale, tenure = 360L),
        TalentTestSupport.fact(hunterKey, 40),
    )

    private fun inputs(tick: Long, cash: Long = 1_000_000L, vickWage: Long = 90L, morale: Long = 650_000L) =
        TalentTestSupport.inputs(tick, *facts(vickWage, morale), cash = cash)

    private fun seeded(tick: Long = 0L) {
        val i = inputs(tick)
        for (f in i.cohorts) rig.skills.reconcile(f, rig.world)
    }

    private fun rule(o: Outcome<*>) = o.rejectionOrNull()?.rule
    private fun submit(wage: Long = 140L, bonus: Long = 0L, count: Long = 20L, stance: CovenantStance = CovenantStance.RESPECT, tick: Long = 0L): Outcome<SubmittedOffer> =
        rig.poaching.submit(hunter, hunterKey, PoachTarget.Group(victimKey, count), Money.units(wage), Money.units(bonus), stance, Tick(tick), inputs(tick))

    @Test fun anApproachIsRecordedWithARetainer() {
        seeded()
        val s = (submit() as Outcome.Ok).value
        assertEquals(1, s.intents.size)
        assertEquals(OfferStatus.PENDING, s.offer.status)
        assertEquals(Tick(10), s.offer.resolveAt)
        assertEquals(s.offer.feePaid, s.intents[0].total)
        assertEquals(20L, s.offer.heads)
        assertEquals(listOf(s.offer), rig.poaching.pendingOf(hunter))
    }

    @Test fun groupsAreCappedByShareOfCohort() {
        seeded()
        assertEquals(30L, (submit(count = 90L) as Outcome.Ok).value.offer.heads)
    }

    @Test fun badApproachesAreRejected() {
        seeded()
        val own = rig.poaching.submit(hunter, hunterKey, PoachTarget.Group(hunterKey, 2), Money.units(100), Money.ZERO, CovenantStance.RESPECT, Tick(0), inputs(0))
        assertEquals(Rules.INVALID_ARGUMENT, rule(own))
        assertEquals(Rules.NOT_OWNER, rule(rig.poaching.submit(hunter, victimKey, PoachTarget.Group(victimKey, 2), Money.units(100), Money.ZERO, CovenantStance.RESPECT, Tick(0), inputs(0))))
        assertEquals(Rules.INVALID_PRICE, rule(submit(wage = 0L)))
        assertEquals(Rules.INVALID_QUANTITY, rule(submit(count = 0L)))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(rig.poaching.submit(hunter, hunterKey, PoachTarget.Group(TalentTestSupport.key(company = 2L, role = "farmhand"), 2), Money.units(100), Money.ZERO, CovenantStance.RESPECT, Tick(0), inputs(0))))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(rig.poaching.submit(hunter, hunterKey, PoachTarget.Individual(PersonId(77)), Money.units(100), Money.ZERO, CovenantStance.RESPECT, Tick(0), inputs(0))))
    }

    @Test fun theHunterMustAffordTheRetainerAndBonuses() {
        seeded()
        val poor = rig.poaching.submit(hunter, hunterKey, PoachTarget.Group(victimKey, 20), Money.units(140), Money.units(1_000), CovenantStance.RESPECT, Tick(0), inputs(0, cash = 100L))
        assertEquals(Rules.INSUFFICIENT_FUNDS, rule(poor))
    }

    @Test fun pendingApproachesAreLimited() {
        seeded()
        // different cohorts of the victim would be needed to avoid the cooldown, which only starts when resolved
        repeat(rig.poaching.params.maxPendingPerHunter) { assertTrue(submit() is Outcome.Ok) }
        assertEquals(Rules.LIMIT_EXCEEDED, rule(submit()))
    }

    @Test fun aCooldownFollowsEveryResolvedApproach() {
        seeded()
        submit()
        rig.poaching.advance(Tick(10), rig.world, inputs(10))
        assertEquals(Rules.RATE_LIMITED, rule(submit(tick = 11L)))
        assertTrue(submit(tick = 80L) is Outcome.Ok)
    }

    @Test fun nothingHappensBeforeTheAnswerIsDue() {
        seeded()
        submit()
        val r = rig.poaching.advance(Tick(5), rig.world, inputs(5))
        assertTrue(r.events.isEmpty() && r.effects.isEmpty())
    }

    @Test fun aGenerousOfferMovesPeopleAndPaysTheFee() {
        seeded()
        val o = (submit(wage = 160L, bonus = 20L) as Outcome.Ok).value.offer
        val r = rig.poaching.advance(Tick(10), rig.world, inputs(10))
        assertEquals(OfferStatus.SUCCEEDED, o.status)
        assertTrue(o.movedHeads in 1L..20L)
        val t = r.effects.filterIsInstance<WorkforceEffect.Transfer>().single()
        assertEquals(o.movedHeads, t.count)
        assertEquals(victimKey, t.from)
        assertEquals(hunterKey, t.to)
        assertEquals(Money.units(160), t.newWage)
        assertTrue(r.events.any { it is TalentEvent.PoachSucceeded })
        assertEquals(PoachOdds.fee(Money.units(160), o.movedHeads, rig.poaching.params), o.feePaid)
        assertTrue(r.intents.any { it.memo == "signing bonus" })
    }

    @Test fun skillsFollowThePeopleWhoMove() {
        seeded()
        val before = rig.skills.get(hunterKey)!!.heads(rig.taxonomy.profile(hunterKey.role)!!.primary)
        val o = (submit(wage = 160L) as Outcome.Ok).value.offer
        rig.poaching.advance(Tick(10), rig.world, inputs(10))
        val after = rig.skills.get(hunterKey)!!.heads(rig.taxonomy.profile(hunterKey.role)!!.primary)
        assertEquals(before + o.movedHeads, after)
    }

    @Test fun bestFirstTakesTheMostSkilledMachinists() {
        seeded()
        val primary = rig.taxonomy.profile(victimKey.role)!!.primary
        val meanBefore = rig.skills.get(victimKey)!!.dist(primary)!!.mean()
        submit(wage = 170L)
        rig.poaching.advance(Tick(10), rig.world, inputs(10))
        assertTrue(rig.skills.get(victimKey)!!.dist(primary)!!.mean() <= meanBefore)
    }

    @Test fun aStingyOfferUsuallyFails() {
        seeded()
        val o = (submit(wage = 80L) as Outcome.Ok).value.offer
        rig.poaching.advance(Tick(10), rig.world, inputs(10, morale = 800_000L))
        assertTrue(o.oddsPpm <= 200_000L)
        assertTrue(o.movedHeads <= 8L)
    }

    @Test fun bestFirstSelectivityRespectsMinimumLevel() {
        seeded()
        val o = rig.poaching.submit(hunter, hunterKey, PoachTarget.Group(victimKey, 20, minLevelPpm = 990_000L), Money.units(170), Money.ZERO, CovenantStance.RESPECT, Tick(0), inputs(0))
        assertTrue(o is Outcome.Ok)
        val offer = (o as Outcome.Ok).value.offer
        rig.poaching.advance(Tick(10), rig.world, inputs(10))
        assertTrue(offer.movedHeads == 0L)
    }

    @Test fun counterOffersKeepSomeOfTheLeavers() {
        seeded()
        rig.poaching.setCounterPolicy(victim, true, 300_000L)
        val o = (submit(wage = 130L) as Outcome.Ok).value.offer
        val r = rig.poaching.advance(Tick(10), rig.world, inputs(10))
        assertTrue(o.keptHeads > 0L)
        assertNotNull(o.counterWage)
        assertTrue(r.effects.any { it is WorkforceEffect.RaiseWage })
        assertTrue(r.intents.any { it.memo == "counter-offer" })
    }

    @Test fun aBrokeVictimCannotCounter() {
        seeded()
        rig.poaching.setCounterPolicy(victim, true, 300_000L)
        val o = (submit(wage = 130L) as Outcome.Ok).value.offer
        val i = TalentInputs(Tick(10), inputs(10).cohorts, listOf(CompanyFact(hunter, Money.units(1_000_000)), CompanyFact(victim, Money.ZERO)))
        rig.poaching.advance(Tick(10), rig.world, i)
        assertEquals(0L, o.keptHeads)
    }

    @Test fun counterPolicyRangeIsChecked() {
        assertEquals(Rules.INVALID_ARGUMENT, rule(rig.poaching.setCounterPolicy(victim, true, -1L)))
        assertEquals(Rules.INVALID_ARGUMENT, rule(rig.poaching.setCounterPolicy(victim, true, 9_000_000L)))
        assertTrue(rig.poaching.setCounterPolicy(victim, false, 100_000L) is Outcome.Ok)
        assertFalse(rig.poaching.counterPolicy(victim)!!.enabled)
    }

    // ----- covenants ---------------------------------------------------------------------------------

    private fun sign(months: Int = 12, penalty: Long = 5_000L): Outcome<NonCompete> =
        rig.poaching.signNonCompete(victim, victimKey, null, months, Money.units(penalty), Tick(0), inputs(0))

    @Test fun covenantsAreSignedWithinLimits() {
        seeded()
        val c = (sign() as Outcome.Ok).value
        assertEquals(100L, c.headsCovered)
        assertEquals(Tick(360), c.until)
        assertEquals(Rules.DUPLICATE, rule(sign()))
        assertEquals(Rules.INVALID_ARGUMENT, rule(sign(months = 0)))
        assertEquals(Rules.INVALID_ARGUMENT, rule(sign(months = 99)))
        assertEquals(Rules.INVALID_PRICE, rule(sign(penalty = 0L)))
        assertEquals(Rules.INVALID_PRICE, rule(sign(penalty = 90L * 360 * 3)))
        assertEquals(Rules.NOT_OWNER, rule(rig.poaching.signNonCompete(hunter, victimKey, null, 12, Money.units(10), Tick(0), inputs(0))))
        assertEquals(Rules.INVALID_ARGUMENT, rule(rig.poaching.signNonCompete(victim, null, null, 12, Money.units(10), Tick(0), inputs(0))))
    }

    @Test fun respectingACovenantBlocksTheApproach() {
        seeded(); sign()
        assertEquals(Rules.REGULATION, rule(submit()))
    }

    @Test fun buyingOutPaysThePenaltyToTheVictim() {
        seeded(); sign(penalty = 2_000L)
        val o = (submit(wage = 170L, stance = CovenantStance.BUY_OUT) as Outcome.Ok).value.offer
        val r = rig.poaching.advance(Tick(10), rig.world, inputs(10))
        assertEquals(OfferStatus.SUCCEEDED, o.status)
        val paid = r.intents.filter { it.memo == "non-compete buy-out" }
        assertEquals(1, paid.size)
        assertEquals(Money.units(2_000) * o.movedHeads, paid[0].total)
        assertTrue(r.events.none { it is TalentEvent.CovenantBreached })
    }

    @Test fun ignoringACovenantIsABreach() {
        seeded()
        val c = (sign(penalty = 2_000L) as Outcome.Ok).value
        val o = (submit(wage = 170L, stance = CovenantStance.IGNORE) as Outcome.Ok).value.offer
        val r = rig.poaching.advance(Tick(10), rig.world, inputs(10))
        assertEquals(OfferStatus.SUCCEEDED, o.status)
        assertEquals(1, c.breaches)
        assertTrue(r.events.any { it is TalentEvent.CovenantBreached })
    }

    @Test fun covenantsCostAnAllowanceAndLapse() {
        seeded()
        val c = (sign(months = 2) as Outcome.Ok).value
        val month = rig.poaching.advance(Tick(30), rig.world, inputs(30))
        assertTrue(month.intents.any { it.memo == "non-compete allowance" })
        assertTrue(month.effects.any { it is WorkforceEffect.Morale && it.deltaPpm < 0L })
        assertFalse(c.lapsed)
        rig.poaching.advance(Tick(60), rig.world, inputs(60))
        assertTrue(c.lapsed)
        val quiet = rig.poaching.advance(Tick(90), rig.world, inputs(90))
        assertTrue(quiet.intents.isEmpty())
    }

    @Test fun releasingACovenantEndsIt() {
        seeded(); val c = (sign() as Outcome.Ok).value
        assertEquals(Rules.NOT_OWNER, rule(rig.poaching.releaseCovenant(c.id, hunter)))
        assertTrue(rig.poaching.releaseCovenant(c.id, victim) is Outcome.Ok)
        assertEquals(Rules.WRONG_STATE, rule(rig.poaching.releaseCovenant(c.id, victim)))
        assertEquals(Rules.UNKNOWN_ENTITY, rule(rig.poaching.releaseCovenant(CovenantId(99), victim)))
        assertTrue(submit() is Outcome.Ok)
    }

    @Test fun aCovenantAddedAfterTheApproachBlocksIt() {
        seeded()
        val o = (submit() as Outcome.Ok).value.offer
        sign()
        rig.poaching.advance(Tick(10), rig.world, inputs(10))
        assertEquals(OfferStatus.BLOCKED, o.status)
    }

    // ----- retention and people --------------------------------------------------------------------------

    @Test fun retentionBonusesLowerTheOdds() {
        seeded()
        val plain = (submit(wage = 140L) as Outcome.Ok).value.offer
        rig.poaching.advance(Tick(10), rig.world, inputs(10))
        val other = ServiceRig()
        for (f in inputs(0).cohorts) other.skills.reconcile(f, other.world)
        assertTrue(other.poaching.grantRetention(victim, victimKey, 800_000L, 90, Tick(0), inputs(0)) is Outcome.Ok)
        val kept = (other.poaching.submit(hunter, hunterKey, PoachTarget.Group(victimKey, 20), Money.units(140), Money.ZERO, CovenantStance.RESPECT, Tick(0), inputs(0)) as Outcome.Ok).value.offer
        other.poaching.advance(Tick(10), other.world, inputs(10))
        assertTrue(kept.oddsPpm < plain.oddsPpm)
    }

    @Test fun retentionGrantsCostMoneyAndExpire() {
        seeded()
        val r = (rig.poaching.grantRetention(victim, victimKey, 500_000L, 60, Tick(0), inputs(0)) as Outcome.Ok).value
        assertEquals(1, r.size)
        assertEquals(500_000L, rig.poaching.retentionOf(victimKey, Tick(10)))
        assertEquals(Rules.DUPLICATE, rule(rig.poaching.grantRetention(victim, victimKey, 400_000L, 60, Tick(1), inputs(1))))
        rig.poaching.advance(Tick(60), rig.world, inputs(60))
        assertEquals(0L, rig.poaching.retentionOf(victimKey, Tick(60)))
        assertEquals(Rules.INVALID_ARGUMENT, rule(rig.poaching.grantRetention(victim, victimKey, 0L, 60, Tick(70), inputs(70))))
        assertEquals(Rules.NOT_OWNER, rule(rig.poaching.grantRetention(hunter, victimKey, 500_000L, 60, Tick(70), inputs(70))))
        assertEquals(Rules.INSUFFICIENT_FUNDS, rule(rig.poaching.grantRetention(victim, victimKey, 500_000L, 60, Tick(70), inputs(70, cash = 1L))))
    }

    @Test fun huntingAKeyPersonVacatesTheirPosition() {
        seeded()
        val ceoKey = TalentTestSupport.key(company = 2L, role = "executive")
        val pos = (rig.succession.openPosition(victim, app.many.economytycoon.core.RoleId("executive"), victimKey.region, "CEO") as Outcome.Ok).value
        val star = (rig.succession.addPerson(victim, "Star", app.many.economytycoon.core.RoleId("executive"), victimKey.region, Money.units(300), 45, Tick(0)) as Outcome.Ok).value
        rig.succession.appoint(pos.id, star.id, Tick(0))
        val i = TalentInputs(Tick(0), inputs(0).cohorts, inputs(0).companies)
        val offer = rig.poaching.submit(hunter, hunterKey.copy(role = app.many.economytycoon.core.RoleId("executive")), PoachTarget.Individual(star.id), Money.units(900), Money.units(5_000), CovenantStance.RESPECT, Tick(0), i)
        assertTrue(offer is Outcome.Ok)
        val o = (offer as Outcome.Ok).value.offer
        val r = rig.poaching.advance(Tick(10), rig.world, inputs(10))
        assertEquals(OfferStatus.SUCCEEDED, o.status)
        assertEquals(hunter, star.company)
        assertNull(star.position)
        assertTrue(pos.isVacant)
        assertTrue(r.events.any { it is TalentEvent.PositionVacated })
        assertNotNull(ceoKey)
    }

    @Test fun oldApproachesArePruned() {
        seeded()
        val o = (submit() as Outcome.Ok).value.offer
        rig.poaching.advance(Tick(10), rig.world, inputs(10))
        rig.poaching.advance(Tick(10 + rig.poaching.params.keepDays.toLong() + 1), rig.world, inputs(400))
        assertNull(rig.poaching.offer(o.id))
    }

    @Test fun sameSeedGivesTheSameOutcome() {
        fun run(): Pair<Long, Long> {
            val r = ServiceRig(); for (f in inputs(0).cohorts) r.skills.reconcile(f, r.world)
            val o = (r.poaching.submit(hunter, hunterKey, PoachTarget.Group(victimKey, 25), Money.units(140), Money.ZERO, CovenantStance.RESPECT, Tick(0), inputs(0)) as Outcome.Ok).value.offer
            r.poaching.advance(Tick(10), r.world, inputs(10))
            return o.movedHeads to r.state.stateHash().hashCode().toLong()
        }
        assertEquals(run(), run())
    }

    @Test fun oddsRiseWithPayAndFallWithMorale() {
        val p = PoachParams()
        fun odds(offered: Long, morale: Long) = PoachOdds.acceptance(OddsInputs(Money.units(100), Money.units(offered), Money.ZERO, morale, 0L, 0L, 500_000L, 0L, 0L), p)
        assertTrue(odds(130, 600_000L) > odds(110, 600_000L))
        assertTrue(odds(130, 300_000L) > odds(130, 900_000L))
        assertEquals(p.maxOddsPpm, odds(1000, 0L))
        assertEquals(p.minOddsPpm, odds(10, 1_000_000L))
    }

    @Test fun counterOddsRiseWithTheMatchedGap() {
        val p = PoachParams()
        assertTrue(PoachOdds.counterSuccess(Money.units(150), Money.units(140), 600_000L, 600_000L, p) > PoachOdds.counterSuccess(Money.units(120), Money.units(140), 600_000L, 600_000L, p))
    }
}
