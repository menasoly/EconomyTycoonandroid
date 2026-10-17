package app.many.economytycoon.talent

import app.many.economytycoon.core.CanonicalHasher
import app.many.economytycoon.core.Hashable
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.StateHash

/**
 * Everything the talent module owns and saves. Maps are sorted so iteration never depends on hashing. Services in
 * this package change the maps they own; everything outside reads through the services or the read model.
 */
class TalentState : Hashable {
    val counters = TalentCounters()

    // education
    internal val institutions = java.util.TreeMap<InstitutionId, Institution>()
    internal val qualifications = java.util.TreeMap<RegionId, QualificationStock>()
    /** School leavers waiting for the next intake day to go on to college or into the labour pool. */
    internal val leavers = java.util.TreeMap<RegionId, SkillDistribution>()

    // skills market
    internal val pools = java.util.TreeMap<PoolKey, SkillDistribution>()
    internal val markets = java.util.TreeMap<PoolKey, SkillMarket>()

    // cohort skill pictures
    internal val cohortSkills = java.util.TreeMap<app.many.economytycoon.workforce.CohortKey, CohortSkillSet>()

    // training
    internal val programmes = java.util.TreeMap<ProgrammeId, TrainingProgramme>()

    // migration
    internal val policies = java.util.TreeMap<app.many.economytycoon.core.CountryId, CountryPolicy>()
    internal val diaspora = java.util.TreeMap<DiasporaKey, SkillDistribution>()
    internal val ledgers = java.util.TreeMap<app.many.economytycoon.core.CountryId, MigrationLedger>()

    // people, positions and succession plans
    internal val people = java.util.TreeMap<PersonId, Person>()
    internal val positions = java.util.TreeMap<PositionId, KeyPosition>()
    internal val successors = java.util.TreeMap<PositionId, java.util.TreeMap<PersonId, Successor>>()

    // poaching
    internal val offers = java.util.TreeMap<OfferId, HeadhuntOffer>()
    internal val covenants = java.util.TreeMap<CovenantId, NonCompete>()
    internal val counterPolicies = java.util.TreeMap<app.many.economytycoon.core.CompanyId, CounterPolicy>()
    internal val retention = java.util.TreeMap<app.many.economytycoon.workforce.CohortKey, RetentionGrant>()
    internal val cooldowns = java.util.TreeMap<CooldownKey, app.many.economytycoon.core.Tick>()

    // employer branding and recruiting
    internal val brands = java.util.TreeMap<BrandKey, EmployerBrand>()
    internal val campaigns = java.util.TreeMap<RecruitCampaignId, RecruitCampaign>()

    // career ladders
    internal val ladders = java.util.TreeMap<LadderId, CareerLadder>()

    // training providers
    internal val providers = java.util.TreeMap<ProviderId, TrainingProvider>()

    /** Empties every partition and the id counters; used before a saved state is read back. */
    internal fun clear() {
        counters.restore(emptyMap())
        institutions.clear(); qualifications.clear(); leavers.clear(); pools.clear(); markets.clear(); cohortSkills.clear()
        programmes.clear(); policies.clear(); diaspora.clear(); ledgers.clear(); people.clear(); positions.clear(); successors.clear()
        offers.clear(); covenants.clear(); counterPolicies.clear(); retention.clear(); cooldowns.clear()
        brands.clear(); campaigns.clear(); ladders.clear(); providers.clear()
    }

    // ----- extension point: further partitions are added below by their services ---------------

    override fun hashInto(hasher: CanonicalHasher) {
        hasher.section("talent")
        for ((k, v) in counters.snapshot()) hasher.putString(k).putLong(v)
        hasher.putInt(institutions.size)
        for (i in institutions.values) i.hashInto(hasher)
        hasher.putInt(qualifications.size)
        for ((r, q) in qualifications) { hasher.putLong(r.value); q.hashInto(hasher) }
        hasher.putInt(leavers.size)
        for ((r, d) in leavers) { hasher.putLong(r.value); d.hashInto(hasher) }
        hasher.putInt(cohortSkills.size)
        for (c in cohortSkills.values) c.hashInto(hasher)
        hasher.putInt(programmes.size)
        for (p in programmes.values) p.hashInto(hasher)
        hasher.putInt(people.size)
        for (p in people.values) p.hashInto(hasher)
        hasher.putInt(positions.size)
        for (p in positions.values) p.hashInto(hasher)
        hasher.putInt(successors.size)
        for ((pos, m) in successors) { hasher.putLong(pos.value).putInt(m.size); for (c in m.values) c.hashInto(hasher) }
        hasher.putInt(offers.size)
        for (o in offers.values) o.hashInto(hasher)
        hasher.putInt(covenants.size)
        for (c in covenants.values) c.hashInto(hasher)
        hasher.putInt(counterPolicies.size)
        for (c in counterPolicies.values) c.hashInto(hasher)
        hasher.putInt(retention.size)
        for (r in retention.values) r.hashInto(hasher)
        hasher.putInt(cooldowns.size)
        for ((k, t) in cooldowns) hasher.putLong(k.hunter.value).putLong(k.cohort.company.value).putLong(k.cohort.facility.value).putString(k.cohort.role.key).putLong(k.cohort.region.value).putLong(t.value)
        hasher.putInt(policies.size)
        for (p in policies.values) p.hashInto(hasher)
        hasher.putInt(diaspora.size)
        for ((k, d) in diaspora) { hasher.putLong(k.from.value).putLong(k.to.value).putString(k.skill.key); d.hashInto(hasher) }
        hasher.putInt(ledgers.size)
        for ((c, l) in ledgers) { hasher.putLong(c.value); l.hashInto(hasher) }
        hasher.putInt(pools.size)
        for ((k, d) in pools) { hasher.putLong(k.region.value).putString(k.skill.key); d.hashInto(hasher) }
        hasher.putInt(markets.size)
        for ((k, m) in markets) { hasher.putLong(k.region.value).putString(k.skill.key); m.hashInto(hasher) }
        hasher.putInt(brands.size)
        for ((k, b) in brands) { hasher.putLong(k.company.value).putLong(k.region.value); b.hashInto(hasher) }
        hasher.putInt(campaigns.size)
        for (c in campaigns.values) c.hashInto(hasher)
        hasher.putInt(ladders.size)
        for (l in ladders.values) l.hashInto(hasher)
        hasher.putInt(providers.size)
        for (p in providers.values) p.hashInto(hasher)
    }

    fun stateHash(): StateHash = CanonicalHasher().also { hashInto(it) }.finish()
}
