package app.many.economytycoon.urban

import app.many.economytycoon.core.CityId
import app.many.economytycoon.core.CompanyId
import app.many.economytycoon.core.Money
import app.many.economytycoon.core.Tick

/** Immutable read models of the urban partition for the presenters. Nothing here changes the plan. */

data class DistrictView(
    val index: Int,
    val name: String,
    val zone: ZoneUse,
    val residents: Long,
    val jobs: Long,
    val floorArea: Long,
    val landSqm: Long,
    val farMilli: Int,
    val maxFarMilli: Int,
    val maxFloors: Int,
    val headroom: Long,
    val pipelineArea: Long,
    val rentPerSqm: Money,
    val rentGrowthPpm: Long,
    val vacancyPpm: Long,
    val landValuePerSqm: Money,
    val accessibilityPermille: Int,
    val averageAgeYears: Int,
    val conditionPpm: Long,
    val lowIncomeSharePpm: Long,
    val displacedTotal: Long,
    val backlashPpm: Long,
    val tenantProtectionPermille: Int,
    val heritage: Boolean,
    val commuteSeconds: Long,
    val openPetition: PetitionId?,
    val rezonedRecently: Boolean,
)

data class LineView(
    val id: TransitLineId,
    val name: String,
    val mode: TransitMode,
    val stopNames: List<String>,
    val headwaySeconds: Int,
    val fare: Money,
    val status: LineStatus,
    val loadRatioPpm: Long,
    val monthlyRiders: Long,
    val monthlyCost: Money,
    val dedicatedLane: Boolean,
    val lengthMeters: Long,
    val concession: ConcessionId?,
)

data class RoadView(
    val id: LinkId,
    val fromName: String,
    val toName: String,
    val roadClass: RoadClass,
    val lanes: Int,
    val flowVph: Long,
    val capacityVph: Long,
    val volumeCapacityPpm: Long,
    val levelOfService: LevelOfService,
    val toll: Money,
)

data class ServiceView(val service: Service, val level: Int, val quality: Int, val monthlyCost: Money)

data class BudgetView(
    val rates: TaxRates,
    val revenue: List<Pair<RevenueLine, Money>>,
    val expenses: List<Pair<ExpenseLine, Money>>,
    val cash: Money,
    val debt: Money,
    val debtLimit: Money,
    val rating: Int,
    val interestRatePpm: Long,
    val fundingRatioPpm: Long,
    val deficitMonths: Int,
    val services: List<ServiceView>,
    val hasReport: Boolean,
)

data class SeatView(val index: Int, val name: String, val ideology: Int, val wardName: String, val developerTies: Int, val integrity: Int)

data class PetitionView(
    val id: PetitionId,
    val districtName: String,
    val district: Int,
    val kind: PetitionKind,
    val status: PetitionStatus,
    val applicant: CompanyId?,
    val fromZone: ZoneUse,
    val toZone: ZoneUse,
    val farMilli: Int,
    val filedAt: Tick,
    val hearingAt: Tick,
    val voteAt: Tick,
    val staffScore: Int,
    val support: Int,
    val opposition: Int,
    val yes: Int,
    val no: Int,
    val needed: Int,
    val lobbied: Money,
    val protest: Boolean,
    val note: String,
)

data class CouncilView(
    val seats: List<SeatView>,
    val majority: Int,
    val supermajority: Int,
    val growthSeats: Int,
    val moodAgainstGrowth: Int,
    val nextElectionIn: Long,
)

data class ProjectView(
    val id: CapitalProjectId,
    val name: String,
    val kind: ProjectKind,
    val phase: ProjectPhase,
    val status: ProjectStatus,
    val progressPpm: Long,
    val budget: Money,
    val originalBudget: Money,
    val spent: Money,
    val overrunPpm: Long,
    val delayDays: Int,
    val daysLeft: Int,
    val fixedPrice: Boolean,
)

data class ConcessionView(
    val id: ConcessionId,
    val name: String,
    val kind: ConcessionKind,
    val model: PaymentModel,
    val status: ConcessionStatus,
    val concessionaire: CompanyId?,
    val termMonths: Int,
    val toll: Money,
    val availabilityMonthly: Money,
    val cityShareNowPpm: Long,
    val bids: Int,
    val bestDiscountPpm: Long,
    val bidsCloseAt: Tick,
    val userChargesTotal: Money,
    val cityShareTotal: Money,
    val availabilityPaidTotal: Money,
    val deductionsTotal: Money,
)

data class EventView(val id: UrbanEventId, val kind: UrbanEventKind, val districtName: String?, val startedAt: Tick, val endsAt: Tick, val active: Boolean, val damageSqm: Long)

data class FactorView(val factor: LiveabilityFactor, val score: Int)

data class CityView(
    val city: CityId,
    val name: String,
    val population: Long,
    val jobs: Long,
    val floorArea: Long,
    val liveability: Int,
    val factors: List<FactorView>,
    val liveabilityHistory: List<Int>,
    val rank: Int,
    val cityCount: Int,
    val vacancyPpm: Long,
    val meanRentPerSqm: Money,
    val rentBurdenPpm: Long,
    val lowIncomeBurdenPpm: Long,
    val unhoused: Long,
    val commuters: Long,
    val averageCommuteSeconds: Long,
    val carSharePpm: Long,
    val transitSharePpm: Long,
    val walkSharePpm: Long,
    val congestionPpm: Long,
    val districts: List<DistrictView>,
    val lines: List<LineView>,
    val roads: List<RoadView>,
    val budget: BudgetView,
    val council: CouncilView,
    val petitions: List<PetitionView>,
    val projects: List<ProjectView>,
    val concessions: List<ConcessionView>,
    val events: List<EventView>,
    val insights: List<Insight>,
)

data class UrbanView(val now: Tick, val cities: List<CityView>, val ranking: List<RankEntry>) {
    fun city(id: CityId): CityView? = cities.firstOrNull { it.city == id }
}
