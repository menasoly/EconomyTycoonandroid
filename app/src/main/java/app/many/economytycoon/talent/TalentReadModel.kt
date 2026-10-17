package app.many.economytycoon.talent

import app.many.economytycoon.advisor.Insight
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.CountryId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.RegionId
import app.many.economytycoon.core.RoleId
import app.many.economytycoon.core.Tick
import app.many.economytycoon.workforce.CohortKey

/** Names the screens show, supplied by whoever owns the world and the companies. */
interface TalentNames {
    fun region(id: RegionId): String
    fun company(id: CompanyId): String
    fun country(id: CountryId): String

    object Plain : TalentNames {
        override fun region(id: RegionId) = "Region ${id.value}"
        override fun company(id: CompanyId) = "Company ${id.value}"
        override fun country(id: CountryId) = "Country ${id.value}"
    }
}

data class CohortLine(
    val key: CohortKey,
    val role: String,
    val headcount: Long,
    val openings: Long,
    val multiplierPpm: Long,
    val matchPpm: Long,
    val meanLevelPpm: Long,
    val weakestSkill: String?,
    val wageRatioPpm: Long,
    val moralePpm: Long,
    val trainingShareOfCohortPpm: Long,
    val weakestSkillId: SkillId? = null,
    /** What a programme of each kind would cost per trainee for its usual length. */
    val quotes: Map<ProgrammeKind, Money> = emptyMap(),
)

data class InstitutionLine(
    val id: InstitutionId, val name: String, val kind: InstitutionKind, val region: RegionId, val regionName: String, val publicOwned: Boolean, val mine: Boolean,
    val capacity: Long, val enrolled: Long, val underConstruction: Long, val qualityPpm: Long, val tuition: Money, val spend: Money,
    val graduates: Long, val dropped: Long, val fields: List<String>,
)

data class QualificationLine(val region: RegionId, val regionName: String, val school: Long, val vocational: Long, val university: Long, val indexPpm: Long)

data class ProgrammeLine(
    val id: ProgrammeId, val cohort: CohortKey, val role: String, val skill: String, val kind: ProgrammeKind, val seats: Long, val start: Tick, val end: Tick,
    val status: ProgrammeStatus, val costPerHead: Money, val paid: Money, val provider: String?, val bondDays: Int?, val bondedHeads: Long, val clawedBack: Money,
)

data class OfferLine(
    val id: OfferId, val hunter: CompanyId, val victim: CompanyId, val hunterName: String, val victimName: String, val heads: Long, val moved: Long, val kept: Long,
    val wage: Money, val bonus: Money, val status: OfferStatus, val resolveAt: Tick, val oddsPpm: Long, val feePaid: Money, val stance: CovenantStance, val role: String,
)

data class CovenantLine(
    val id: CovenantId, val role: String, val cohort: CohortKey?, val person: String?, val heads: Long, val until: Tick, val active: Boolean, val penaltyPerHead: Money, val breaches: Int,
)

data class RetentionLine(val cohort: CohortKey, val role: String, val until: Tick, val strengthPpm: Long)

data class BrandLine(val region: RegionId, val regionName: String, val awarenessPpm: Long, val appealPpm: Long, val brandPpm: Long)

data class CampaignLine(
    val id: RecruitCampaignId, val region: RegionId, val regionName: String, val role: String, val budget: Money, val end: Tick, val status: CampaignStatus, val hired: Long,
)

data class LadderLine(val id: LadderId, val name: String, val enabled: Boolean, val promotions: Long, val rungs: List<String>, val steps: List<PromotionPreview>)

data class SuccessorLine(val person: PersonId, val name: String, val readinessPpm: Long, val development: DevelopmentKind)

data class PositionLine(
    val id: PositionId, val title: String, val role: String, val holder: String?, val holderAge: Int?, val vacant: Boolean, val vacantDays: Long,
    val searching: Boolean, val effectivenessPpm: Long, val successors: List<SuccessorLine>,
)

data class PersonLine(val id: PersonId, val name: String, val role: String, val ageYears: Int, val wage: Money, val loyaltyPpm: Long, val potentialPpm: Long, val position: String?)

data class CourseLine(val id: CourseId, val name: String, val skill: String, val kind: CourseKind, val seats: Long, val students: Long, val tuition: Money, val open: Boolean, val completed: Long)

data class ProviderLine(
    val id: ProviderId, val name: String, val owner: CompanyId, val ownerName: String, val region: RegionId, val regionName: String, val mine: Boolean,
    val qualityPpm: Long, val reputationPpm: Long, val accreditation: Accreditation, val accreditedUntil: Tick, val rank: Int, val scorePpm: Long,
    val graduates: Long, val completionPpm: Long, val meanGainPpm: Long, val revenue: Money, val costs: Money, val teachingLevelPpm: Long, val courses: List<CourseLine>,
)

data class MigrationLine(
    val country: CountryId, val countryName: String, val opennessPpm: Long, val quotaPpm: Long, val skilledOnly: Boolean,
    val out: Long, val skilledOut: Long, val arrived: Long, val returned: Long, val diasporaAbroad: Long, val brainDrainPpm: Long,
)

/** Everything one company can see of the talent module, read once per refresh. */
data class TalentView(
    val company: CompanyId,
    val tick: Tick,
    val cohorts: List<CohortLine>,
    val shortages: List<ShortageRow>,
    val institutions: List<InstitutionLine>,
    val qualifications: List<QualificationLine>,
    val programmes: List<ProgrammeLine>,
    val huntingOffers: List<OfferLine>,
    val offersAgainstUs: List<OfferLine>,
    val covenants: List<CovenantLine>,
    val retention: List<RetentionLine>,
    val counterEnabled: Boolean,
    val counterMaxMatchPpm: Long,
    val brands: List<BrandLine>,
    val campaigns: List<CampaignLine>,
    val ladders: List<LadderLine>,
    val positions: List<PositionLine>,
    val people: List<PersonLine>,
    val coveragePpm: Long,
    val keyRoleIndexPpm: Long,
    val providers: List<ProviderLine>,
    val migration: List<MigrationLine>,
    val advice: List<Insight>,
)

/** Builds [TalentView]s. Reads only; nothing here changes the system. */
class TalentReader(private val system: TalentSystem, private val names: TalentNames = TalentNames.Plain) {

    private fun roleName(r: RoleId) = system.taxonomy.profile(r)?.name ?: r.key.replace('_', ' ')
    private fun skillName(s: SkillId) = system.taxonomy.skillOrNull(s)?.name ?: s.key.replace('_', ' ')

    fun view(company: CompanyId, tick: Tick, inputs: TalentInputs, world: TalentWorld): TalentView {
        val own = inputs.cohorts.filter { it.key.company == company }.sortedBy { it.key }
        val regions = own.map { it.key.region }.toSet() + system.providers.providers().filter { it.owner == company }.map { it.region } +
            system.education.ownedBy(company).map { it.region }
        val countries = (regions.map { world.countryOf(it) } + system.education.ownedBy(company).map { it.country }).toSortedSet()
        val ranking = system.providers.ranking().associateBy { it.provider }
        return TalentView(
            company = company, tick = tick,
            cohorts = own.map { cohortLine(it) },
            shortages = system.markets.shortages().filter { it.region in regions },
            institutions = system.education.open().filter { it.region in regions || it.owner == company }.map { institutionLine(it, company) },
            qualifications = regions.sorted().map { qualificationLine(it, world) },
            programmes = system.training.all().filter { it.company == company }.sortedByDescending { it.startTick }.map { programmeLine(it) },
            huntingOffers = system.poaching.offers().filter { it.hunter == company }.sortedByDescending { it.submittedAt }.map { offerLine(it) },
            offersAgainstUs = system.poaching.offers().filter { it.victim == company }.sortedByDescending { it.submittedAt }.map { offerLine(it) },
            covenants = system.poaching.covenants(company).map { covenantLine(it, tick) },
            retention = own.mapNotNull { f -> system.poaching.retentionOf(f.key, tick).takeIf { it > 0L }?.let { s -> RetentionLine(f.key, roleName(f.key.role), system.state.retention.getValue(f.key).until, s) } },
            counterEnabled = system.poaching.counterPolicy(company)?.enabled == true,
            counterMaxMatchPpm = system.poaching.counterPolicy(company)?.maxMatchPpm ?: 0L,
            brands = regions.sorted().map { r -> BrandLine(r, names.region(r), system.brand.brand(company, r)?.awarenessPpm ?: 0L, system.brand.appeal(company, r), system.brand.brandOf(company, r)) },
            campaigns = system.brand.campaigns().filter { it.company == company }.sortedByDescending { it.startTick }.map {
                CampaignLine(it.id, it.region, names.region(it.region), roleName(it.role), it.budget, it.endTick, it.status, it.hired)
            },
            ladders = system.careers.laddersOf(company).map { l ->
                LadderLine(l.id, l.name, l.enabled, l.promotions, l.rungs.map { roleName(it.cohort.role) }, system.careers.preview(l, inputs))
            },
            positions = system.succession.positionsOf(company).map { positionLine(it, tick) },
            people = system.succession.peopleOf(company).map { personLine(it) },
            coveragePpm = system.succession.coveragePpm(company),
            keyRoleIndexPpm = system.succession.keyRoleIndex(company, tick),
            providers = system.providers.providers().filter { !it.closed && (it.region in regions || it.owner == company) }.map { providerLine(it, company, ranking[it.id]) },
            migration = countries.map { migrationLine(it, world) },
            advice = TalentAdvisor(system).advise(company, tick, inputs, world),
        )
    }

    private fun cohortLine(f: CohortFact): CohortLine {
        val prod = system.skills.productivity(f.key)
        return CohortLine(
            f.key, roleName(f.key.role), f.headcount, f.openings, prod?.multiplierPpm ?: Ppm.ONE, prod?.matchPpm ?: 0L, system.skills.meanLevel(f.key) ?: 0L,
            prod?.weakest?.let { skillName(it.skill) }, f.wageRatioPpm, f.moralePpm, system.training.trainingShare(f.key, f.headcount),
            prod?.weakest?.skill, ProgrammeKind.values().associateWith { system.training.quote(it, it.defaultDays, f) },
        )
    }

    private fun institutionLine(i: Institution, viewer: CompanyId) = InstitutionLine(
        i.id, i.name, i.kind, i.region, names.region(i.region), i.isPublic, i.owner == viewer, i.capacity, i.enrolled, i.underConstruction, i.qualityPpm, i.tuition, i.spend,
        i.graduatesTotal, i.droppedTotal, i.fields.map { skillName(it) },
    )

    private fun qualificationLine(r: RegionId, world: TalentWorld): QualificationLine {
        val q = system.education.qualifications(r)
        return QualificationLine(r, names.region(r), q.of(InstitutionKind.SCHOOL), q.of(InstitutionKind.VOCATIONAL), q.of(InstitutionKind.UNIVERSITY), q.indexPpm(world.workingAge(r)))
    }

    private fun programmeLine(p: TrainingProgramme) = ProgrammeLine(
        p.id, p.cohort, roleName(p.cohort.role), skillName(p.skill), p.kind, p.seats, p.startTick, p.endTick, p.status, p.costPerHead, p.paid,
        p.provider?.let { system.providers.provider(it)?.name }, p.clause?.bondDays, p.bondedHeads, p.clawedBack,
    )

    private fun offerLine(o: HeadhuntOffer) = OfferLine(
        o.id, o.hunter, o.victim, names.company(o.hunter), names.company(o.victim), o.heads, o.movedHeads, o.keptHeads, o.offeredWage, o.signingBonus, o.status, o.resolveAt,
        o.oddsPpm, o.feePaid, o.stance, roleName(o.destination.role),
    )

    private fun covenantLine(c: NonCompete, tick: Tick) = CovenantLine(
        c.id, roleName(c.cohort?.role ?: c.person?.let { system.succession.person(it)?.role } ?: RoleId("unknown")), c.cohort, c.person?.let { system.succession.person(it)?.name },
        c.headsCovered, c.until, c.isActive(tick), c.penaltyPerHead, c.breaches,
    )

    private fun positionLine(p: KeyPosition, tick: Tick): PositionLine {
        val holder = p.holder?.let { system.succession.person(it) }
        return PositionLine(
            p.id, p.title, roleName(p.role), holder?.name, holder?.ageYears, p.isVacant, p.vacantSince?.let { tick.value - it.value } ?: 0L, p.searchUntil != null,
            system.succession.effectiveness(p.id, tick),
            system.succession.successorsOf(p.id).map { s -> SuccessorLine(s.person, system.succession.person(s.person)?.name ?: "Unknown", s.readinessPpm, s.development) },
        )
    }

    private fun personLine(p: Person) = PersonLine(
        p.id, p.name, roleName(p.role), p.ageYears, p.wage, p.loyaltyPpm, p.potentialPpm, p.position?.let { system.succession.position(it)?.title },
    )

    private fun providerLine(p: TrainingProvider, viewer: CompanyId, rank: ProviderRank?) = ProviderLine(
        p.id, p.name, p.owner, names.company(p.owner), p.region, names.region(p.region), p.owner == viewer, p.qualityPpm, p.reputationPpm, p.accreditation, p.accreditedUntil,
        rank?.rank ?: 0, rank?.scorePpm ?: 0L, p.graduates, p.completionRatePpm, p.meanGainPpm, p.revenue, p.costs, p.teachingLevelPpm,
        p.courseList().map { c -> CourseLine(c.id, c.name, skillName(c.skill), c.kind, c.seats, c.students, c.tuition, c.open, c.completedTotal) },
    )

    private fun migrationLine(c: CountryId, world: TalentWorld): MigrationLine {
        val policy = system.migration.policy(c); val ledger = system.migration.ledger(c)
        return MigrationLine(
            c, names.country(c), policy.opennessPpm, policy.quotaPpm, policy.skilledOnly, ledger.out, ledger.skilledOut, ledger.arrived, ledger.returned,
            system.migration.diasporaOf(c), system.migration.brainDrainPpm(world, c),
        )
    }
}
